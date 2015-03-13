/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class JavaExternalDocumentationTest extends PlatformTestCase {
  public void testImagesInsideJavadocJar() throws Exception {
    final VirtualFile libClasses = getJarFile("library.jar");
    final VirtualFile libJavadocJar = getJarFile("library-javadoc.jar");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary("myLib");
        final Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(libClasses, OrderRootType.CLASSES);
        model.addRoot(libJavadocJar, JavadocOrderRootType.getInstance());
        model.commit();

        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        assertSize(1, modules);
        ModuleRootModificationUtil.addDependency(modules[0], library);
      }
    });

    PsiFile psiFile =
      PsiFileFactory.getInstance(myProject).createFileFromText(JavaLanguage.INSTANCE, "class Foo { com.jetbrains.Test field; }");
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    assertNotNull(document);
    Editor editor = EditorFactory.getInstance().createEditor(document, myProject);
    try {
      editor.getCaretModel().moveToOffset(document.getText().indexOf("Test"));
      DocumentationManager documentationManager = DocumentationManager.getInstance(myProject);
      documentationManager.showJavaDocInfo(editor, psiFile, false);
      JBPopup popup = documentationManager.getDocInfoHint();
      assertNotNull(popup);
      DocumentationComponent documentationComponent = (DocumentationComponent)popup.getContent().getComponent(0);
      try {
        byte[] imageData = getImageDataFromDocumentationComponent(documentationComponent);
        assertEquals(228, imageData.length);
      }
      finally {
        Disposer.dispose(documentationComponent);
      }
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }
  
  @NotNull
  private static VirtualFile getJarFile(String name) {
    VirtualFile file = getVirtualFile(new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/documentation/" + name));
    assertNotNull(file);
    VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assertNotNull(jarFile);
    return jarFile;
  }
  
  private static byte[] getImageDataFromDocumentationComponent(DocumentationComponent documentationComponent) throws Exception {
    JEditorPane editorPane = (JEditorPane)documentationComponent.getComponent();
    HTMLDocument document = (HTMLDocument)editorPane.getDocument();
    HTMLDocument.Iterator it = document.getIterator(HTML.Tag.IMG);
    assertTrue(it.isValid());
    String relativeUrl = (String)it.getAttributes().getAttribute(HTML.Attribute.SRC);
    it.next();
    assertFalse(it.isValid());
    URL imageUrl = new URL(document.getBase(), relativeUrl);
    InputStream stream = imageUrl.openStream();
    try {
      return FileUtil.loadBytes(stream);
    }
    finally {
      stream.close();
    }
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }
}

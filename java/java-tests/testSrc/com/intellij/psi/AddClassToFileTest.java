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
package com.intellij.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

@PlatformTestCase.WrapInCommand
public class AddClassToFileTest extends PsiTestCase{
  public void test() throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    PsiDirectory dir = myPsiManager.findDirectory(root);
    assertNotNull(dir);
    PsiFile file = ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return dir.createFile("AAA.java");
      }
    });
    PsiClass aClass = myJavaFacade.getElementFactory().createClass("AAA");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        file.add(aClass);
      }
    });


    PsiTestUtil.checkFileStructure(file);
  }

  public void testFileModified() throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    VirtualFile pkg = createChildDirectory(root, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    String text = "package foo;\n\nclass A {}";
    PsiElement created = ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
      @Override
      public PsiElement compute() {
        return dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText("A.java", JavaFileType.INSTANCE, text));
      }
    });
    VirtualFile virtualFile = created.getContainingFile().getVirtualFile();
    assertNotNull(virtualFile);
    String fileText = LoadTextUtil.loadText(virtualFile).toString();
    assertEquals(text, fileText);

    Document doc = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(doc);
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(doc));
    assertFalse(FileDocumentManager.getInstance().isFileModified(virtualFile));
  }
}

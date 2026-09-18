// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static com.intellij.platform.backend.documentation.impl.ImplKt.computeHtmlDocBlocking;
import static org.junit.Assert.assertNotNull;

@SuppressWarnings({"removal", "UnnecessaryFullyQualifiedName"})
public final class JavaDocumentationTestUtil {
  private JavaDocumentationTestUtil() {
  }

  public static @NotNull VirtualFile getJarFile(@NotNull String name) {
    Path dataFile = Path.of(JavaTestUtil.getJavaTestDataPath(), "codeInsight", "documentation", name);
    VirtualFile file = StandardFileSystems.local().refreshAndFindFileByPath(dataFile.toAbsolutePath().toString());
    assertNotNull(file);
    VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assertNotNull(jarFile);
    return jarFile;
  }

  public static @NotNull String getDocumentationText(@NotNull Project project, @NotNull String sourceEditorText) {
    int caretPosition = sourceEditorText.indexOf(EditorTestUtil.CARET_TAG);
    if (caretPosition >= 0) {
      sourceEditorText = sourceEditorText.substring(0, caretPosition) +
                         sourceEditorText.substring(caretPosition + EditorTestUtil.CARET_TAG.length());
    }
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, sourceEditorText);
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    assertNotNull(document);
    Editor editor = EditorFactory.getInstance().createEditor(document, project);
    try {
      if (caretPosition >= 0) {
        editor.getCaretModel().moveToOffset(caretPosition);
      }
      return getDocumentationText(editor, psiFile);
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  public static @NotNull String getDocumentationText(@NotNull Editor editor) {
    Project project = editor.getProject();
    assertNotNull(project);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assertNotNull(psiFile);
    return getDocumentationText(editor, psiFile);
  }

  private static @NotNull String getDocumentationText(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    String html = computeHtmlDocBlocking(editor, psiFile);
    if (html == null) html = "";
    return com.intellij.codeInsight.documentation.DocumentationManager.decorate(html, null, null);
  }
}
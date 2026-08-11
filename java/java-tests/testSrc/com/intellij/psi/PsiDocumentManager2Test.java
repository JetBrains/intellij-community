// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@HeavyPlatformTestCase.WrapInCommand
public class PsiDocumentManager2Test extends LightPlatformTestCase {
  public void testUnregisteredFileType() {
    final class MyFileType extends XmlLikeFileType {
      private MyFileType() {
        super(XMLLanguage.INSTANCE);
      }

      @Override
      @NotNull
      public String getName() {
        return "MY";
      }

      @Override
      @NotNull
      public String getDefaultExtension() {
        return "my";
      }

      @Override
      @NotNull
      public String getDescription() {
        return "my own";
      }

      @Override
      public Icon getIcon() {
        return null;
      }
    }
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("DummyFile.my", new MyFileType(), "<gggg></gggg>", LocalTimeCounter.currentTime(), true);
    Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(getProject()).getDocument(file));

    assertTrue(document.isWritable());
    assertTrue(file.getVirtualFile().getFileType() instanceof MyFileType);
    assertTrue(DaemonCodeAnalyzer.getInstance(getProject()).isHighlightingAvailable(file));
  }

  public void testPsiToDocSyncInNonPhysicalFile() {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}", false, false);

    Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());
    assertNotNull(document);

    file.getClasses()[0].getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

    assertEquals("public class Foo {}", document.getText());
    assertFalse(PsiDocumentManager.getInstance(getProject()).isUncommited(document));
  }

  public void test_nonPhysical_PSI_matches_document_loaded_after_modifications() {
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}\nclass Bar{}", false, false);

    file.getClasses()[1].delete();
    String expectedText = "class Foo {}\n";
    assertEquals(expectedText, file.getText());

    Document document = file.getViewProvider().getDocument();
    assertTrue(PsiDocumentManager.getInstance(getProject()).isCommitted(document));
    assertEquals(expectedText, document.getText());
  }

  public void testGetDocumentWithoutReadActionThrows() throws ExecutionException, InterruptedException {
    VirtualFile file = VfsTestUtil.createFile(getSourceRoot(), "test.txt", "test");
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Throwable error = LoggedErrorProcessor.executeAndReturnLoggedError(
        () -> FileDocumentManager.getInstance().getDocument(file)
      );
      assertTrue(error.getMessage(), error.getMessage().contains(ThreadingAssertions.MUST_EXECUTE_IN_READ_ACTION));
    }).get();
  }


}

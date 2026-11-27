// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.highlighter.XmlLikeFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PsiDocumentManager2Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  @Test
  public void testUnregisteredFileType() {
    Project project = projectFixture.get().getProject();
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
    ReadAction.runBlocking(() -> {
      PsiFile
        file = PsiFileFactory.getInstance(project).createFileFromText("DummyFile.my", new MyFileType(), "<gggg></gggg>", LocalTimeCounter.currentTime(), true);
      Document document = Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(file));

      assertTrue(document.isWritable());
      assertTrue(file.getVirtualFile().getFileType() instanceof MyFileType);
      assertTrue(DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file));
    });
  }

  @Test
  public void testPsiToDocSyncInNonPhysicalFile() {
    Project project = projectFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project)
        .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}", false, false);

      Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());
      assertNotNull(document);

      file.getClasses()[0].getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

      assertEquals("public class Foo {}", document.getText());
      assertFalse(PsiDocumentManager.getInstance(project).isUncommited(document));
    });
  }

  @Test
  public void test_nonPhysical_PSI_matches_document_loaded_after_modifications() {
    Project project = projectFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(project)
        .createFileFromText("a.java", JavaLanguage.INSTANCE, "class Foo {}\nclass Bar{}", false, false);

      file.getClasses()[1].delete();
      String expectedText = "class Foo {}\n";
      assertEquals(expectedText, file.getText());

      Document document = file.getViewProvider().getDocument();
      assertTrue(PsiDocumentManager.getInstance(project).isCommitted(document));
      assertEquals(expectedText, document.getText());
    });
  }

  @Test
  public void testGetDocumentWithoutReadActionThrows() {
    VirtualFile file = VfsTestUtil.createFile(projectFixture.get().getSourceRoot(), "test.txt", "test");
    Throwable error = LoggedErrorProcessor.executeAndReturnLoggedError(
      () -> FileDocumentManager.getInstance().getDocument(file)
    );
    assertTrue(error.getMessage().contains(ThreadingAssertions.MUST_EXECUTE_IN_READ_ACTION), error.getMessage());
  }
}

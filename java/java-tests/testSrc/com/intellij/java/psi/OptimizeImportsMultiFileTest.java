// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class OptimizeImportsMultiFileTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testOptimizeImportInPackageIgnoresFilesOutsideSourceRootsForExampleTestData() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    VirtualFile root = fixture.createTestProjectStructure(OptimizeImportsFixture.BASE_PATH + "/testData", false);
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), root);
    VirtualFile x = root.findChild("X.java");
    String textBefore = VfsUtilCore.loadText(x);
    PsiDirectory directory = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(root));
    assertNotNull(directory);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      new OptimizeImportsProcessor(project, directory, true, false).run();
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    });
    EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
    String textAfter = VfsUtilCore.loadText(x);
    assertEquals(textBefore, textAfter);
  }

  @Test
  public void testOptimizeImportsMustAddUnambiguousImportsIfTheCorrespondingSettingIsOn() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
      VirtualFile root = fixture.createTestProjectStructure(OptimizeImportsFixture.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(fixture.getModule(), root);
      PsiDirectory directory = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(root));
      assertNotNull(directory);
      EdtTestUtil.runInEdtAndWait(() -> new OptimizeImportsProcessor(project, directory, true, false).run());
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });
      EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertTrue(text1After.contains("import java.util.ArrayList;"), text1After);
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertTrue(text2After.contains("import java.util.ArrayList;"), text2After);
      return null;
    });
  }

  @Test
  public void testOptimizeImportsMustNotAddUnambiguousImportsIfTheCorrespondingSettingIsOff() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    CodeInsightSettings.runWithTemporarySettings(settings -> {
      settings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false;
      VirtualFile root = fixture.createTestProjectStructure(OptimizeImportsFixture.BASE_PATH + "/src1", false);
      PsiTestUtil.addSourceRoot(fixture.getModule(), root);
      PsiDirectory directory = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(root));
      assertNotNull(directory);
      EdtTestUtil.runInEdtAndWait(() -> new OptimizeImportsProcessor(project, directory, true, false).run());
      WriteCommandAction.runWriteCommandAction(null, () -> {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });
      EdtTestUtil.runInEdtAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
      String text1After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X1.java"));
      assertFalse(text1After.contains("import java.util.ArrayList;"), text1After);
      String text2After = VfsUtilCore.loadText(root.findFileByRelativePath("p/X2.java"));
      assertFalse(text2After.contains("import java.util.ArrayList;"), text2After);
      return null;
    });
  }
}

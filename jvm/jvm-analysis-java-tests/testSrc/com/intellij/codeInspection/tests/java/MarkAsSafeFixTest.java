package com.intellij.codeInspection.tests.java;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class MarkAsSafeFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SourceToSinkFlowInspection()};
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile annotations = getSourceRoot()
            .createChildDirectory(this, "org")
            .createChildDirectory(this, "checkerframework")
            .createChildDirectory(this, "checker")
            .createChildDirectory(this, "tainting")
            .createChildDirectory(this, "qual");
          VirtualFile untainted = annotations.createChildData(this, "Untainted.java");
          VfsUtil.saveText(untainted, "      package org.checkerframework.checker.tainting.qual;\n" +
                                      "      import java.lang.annotation.ElementType;\n" +
                                      "      import java.lang.annotation.Target;\n" +
                                      "      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})\n" +
                                      "      public @interface Untainted {\n" +
                                      "      }");
          VirtualFile tainted = annotations.createChildData(this, "Tainted.java");
          VfsUtil.saveText(tainted, "  package org.checkerframework.checker.tainting.qual;\n" +
                                    "      import java.lang.annotation.ElementType;\n" +
                                    "      import java.lang.annotation.Target;\n" +
                                    "      @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})\n" +
                                    "      public @interface Tainted {\n" +
                                    "      }");
        }
        catch (IOException ignored) {
        }
      }
    });
  }

  @Override
  protected String getBasePath() {
    return "/codeInspection/sourceToSinkFlow/markAsSafe";
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH;
  }
}

package com.intellij.codeInspection.tests.java.sourceToSink;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jetbrains.annotations.NotNull;

 
abstract class SourceToSinkFixBaseTest extends LightQuickFixParameterizedTestCase {
  
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SourceToSinkFlowInspection()};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module,
                                  @NotNull ModifiableRootModel model,
                                  @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        MavenDependencyUtil.addFromMaven(model, "org.checkerframework:checker-qual:3.18.0");
      }
    };
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH;
  }
}

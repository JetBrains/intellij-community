package com.intellij.codeInspection.tests.kotlin;

import com.intellij.codeInspection.tests.UastCallMatcherTestBase;
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import kotlin.KotlinVersion;
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts;
import org.junit.Assume;

import java.io.File;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/uastCallMatcher")
public class KtUastCallMatcherTest extends UastCallMatcherTestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Assume.assumeTrue(KotlinVersion.CURRENT.isAtLeast(1, 2, 60));
  }

  @Override
  protected String getBasePath() {
    return JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/uastCallMatcher";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    super.tuneFixture(moduleBuilder);
    File kotlinStdlib = KotlinArtifacts.getInstance().getKotlinStdlib();
    moduleBuilder.addLibraryJars("kotlin-stdlib", kotlinStdlib.getParent(), kotlinStdlib.getName());
  }


  public void testCallExpressions() {
    doTestCallExpressions("MyClass.kt");
  }

  public void testCallableReferences() {
    doTestCallableReferences("MethodReferences.kt");
  }
}

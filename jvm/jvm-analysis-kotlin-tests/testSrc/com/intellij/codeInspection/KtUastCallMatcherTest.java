package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import kotlin.KotlinVersion;
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
    //TODO check if adding kotlin-stdlib is redundant
    String kotlinDir = PathManager.getHomePath().replace(File.separatorChar, '/') +
                       "/community/build/dependencies/build/kotlin/Kotlin/kotlinc/lib";
    moduleBuilder.addLibraryJars("kotlin-stdlib", kotlinDir, "kotlin-stdlib.jar");
  }


  public void testCallExpressions() {
    doTestCallExpressions("MyClass.kt");
  }

  public void testCallableReferences() {
    doTestCallableReferences("MethodReferences.kt");
  }
}

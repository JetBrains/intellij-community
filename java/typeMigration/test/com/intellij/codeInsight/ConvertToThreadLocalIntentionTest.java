package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

/**
 * @author anna
 */
@TestApplication
public class ConvertToThreadLocalIntentionTest extends LightQuickFixFileBasedTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData" + "/intentions/threadLocal";
  }
}
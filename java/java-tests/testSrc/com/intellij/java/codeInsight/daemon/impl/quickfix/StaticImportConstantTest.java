// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportConstantFix;
import com.intellij.codeInsight.daemon.impl.quickfix.StaticImportMemberFix;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.java.codeInsight.daemon.impl.ImportHelperTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.BooleanSupplier;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class StaticImportConstantTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/staticImportConstant";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    BooleanSupplier isInsideResolve = () -> {
      if (!ImportHelperTest.isFromJavaCodeReferenceElementResolve()) {
        return false;
      }
      Throwable currentStack = new Throwable();
      boolean isInsideStaticImportFix = ContainerUtil.exists(currentStack.getStackTrace(),
                                                             stackElement -> stackElement.getClassName().equals(StaticImportConstantFix.class.getName())
                                                                             || stackElement.getClassName().equals(StaticImportMemberFix.class.getName())
      );
      // check that resolve is called from inside StaticImport*Fix, because there are so many other intentions/fixes which doing resolve in EDT we haven't fixed yet
      return isInsideStaticImportFix;
    };
    EdtTestUtil.runInEdtAndWait(() -> ImportHelperTest.assertResolveNotCalledInEDTDuring(isInsideResolve, () -> quickFixFixture.get().doTest(fileName)));
  }
}

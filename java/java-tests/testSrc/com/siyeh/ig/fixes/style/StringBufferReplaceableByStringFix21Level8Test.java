// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Supplier;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class StringBufferReplaceableByStringFix21Level8Test {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR_WITH_JDK_21_LEVEL_8 =
    createProjectDescriptor(IdeaTestUtil::getMockJdk21, LanguageLevel.JDK_1_8);

  static @NotNull DefaultLightProjectDescriptor createProjectDescriptor(Supplier<? extends Sdk> customSdk, @NotNull LanguageLevel level) {
    return new DefaultLightProjectDescriptor(customSdk) {
      @Override
      public void configureModule(@NotNull Module module,
                                  @NotNull ModifiableRootModel model,
                                  @NotNull ContentEntry contentEntry) {
        LanguageLevelModuleExtension extension = model.getModuleExtension(LanguageLevelModuleExtension.class);
        if (extension != null) {
          extension.setLanguageLevel(level);
        }
        addJetBrainsAnnotationsWithTypeUse(model);
      }
    };
  }

  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igfixes/style/replace_with_string_21_level_8";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR_WITH_JDK_21_LEVEL_8);
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, () -> List.of(new StringBufferReplaceableByStringInspection()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }
}

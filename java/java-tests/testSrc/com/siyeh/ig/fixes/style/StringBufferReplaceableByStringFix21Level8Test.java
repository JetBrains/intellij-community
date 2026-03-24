// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class StringBufferReplaceableByStringFix21Level8Test extends LightQuickFixParameterizedTestCase {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR_WITH_JDK_21_LEVEL_8 =
    createProjectDescriptor(IdeaTestUtil::getMockJdk21, LanguageLevel.JDK_1_8);

  @Override
  protected String getBasePath() {
    return "/ig/com/siyeh/igfixes/style/replace_with_string_21_level_8";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR_WITH_JDK_21_LEVEL_8;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new StringBufferReplaceableByStringInspection()};
  }

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
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ForwardCompatibilityInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/jdk/forward_compatibility";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ForwardCompatibilityInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testAssert() {
    withLevel(LanguageLevel.JDK_1_3, this::doTest);
  }

  public void testEnum() {
    withLevel(LanguageLevel.JDK_1_3, this::doTest);
  }

  public void testUnqualifiedYield() { doTest(); }

  public void testQualifiedYieldWithUnexpectedToken() {
    withLevel(LanguageLevel.JDK_1_8, () -> {
      myFixture.configureByFile(getTestName(false) + ".java");
      List<HighlightInfo> list = ContainerUtil.filter(myFixture.doHighlighting(HighlightSeverity.WARNING), it -> it.getSeverity().equals(HighlightSeverity.WARNING));
      assertEquals(1, list.size());
      HighlightInfo info = ContainerUtil.getOnlyItem(list);
      assertEquals("yield", info.getText());
      assertEquals("Unqualified call to 'yield' method is not supported in releases since Java 14", info.getDescription());
      assertEquals(60, info.getStartOffset());
      assertEquals(65, info.getEndOffset());
      assertFalse(info.findRegisteredQuickFix((descriptor, textRange) -> {
        return descriptor.getAction().getFamilyName().equals(InspectionGadgetsBundle.message("qualify.call.fix.family.name"));
      }));
    });
  }

  public void testUnderscore() { doTest(); }

  public void testRestrictedKeywordWarning() { doTest(); }

  public void testLoneSemicolon() { withLevel(LanguageLevel.JDK_20, this::doTest); }

  public void testModuleInfoWarning() {
    withLevel(LanguageLevel.JDK_1_9, () -> {
      myFixture.configureByFile("module-info.java");
      myFixture.testHighlighting(true, false, false);
    });
  }

  public void withLevel(LanguageLevel languageLevel, Runnable runnable) {
    Module module = getModule();
    LanguageLevel prev = LanguageLevelUtil.getCustomLanguageLevel(module);
    IdeaTestUtil.setModuleLanguageLevel(module, languageLevel);
    try {
      runnable.run();
    }
    finally {
      IdeaTestUtil.setModuleLanguageLevel(module, prev);
    }
  }
}

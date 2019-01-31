// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public abstract class LightInspectionTestCase extends LightCodeInsightFixtureTestCase {

  public static final String INSPECTION_GADGETS_TEST_DATA_PATH = "/plugins/InspectionGadgets/test/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    for (String environmentClass : getEnvironmentClasses()) {
      myFixture.addClass(environmentClass);
    }
    final InspectionProfileEntry inspection = getInspection();
    if (inspection != null) {
      myFixture.enableInspections(inspection);

      final Project project = myFixture.getProject();
      final HighlightDisplayKey displayKey = HighlightDisplayKey.find(inspection.getShortName());
      final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
      final HighlightDisplayLevel errorLevel = currentProfile.getErrorLevel(displayKey, null);
      if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW) {
        currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
      }
    }

    Sdk sdk = ModuleRootManager.getInstance(ModuleManager.getInstance(getProject()).getModules()[0]).getSdk();
    if (JAVA_1_7.getSdk().getName().equals(sdk == null ? null : sdk.getName())) {
      PsiClass object = JavaPsiFacade.getInstance(getProject()).findClass("java.lang.Object", GlobalSearchScope.allScope(getProject()));
      assertNotNull(object);

      PsiClass component = JavaPsiFacade.getInstance(getProject()).findClass("java.awt.Component", GlobalSearchScope.allScope(getProject()));
      assertNotNull(component);
    }
  }

  @Nullable
  protected abstract InspectionProfileEntry getInspection();

  @Language("JAVA")
  @SuppressWarnings("LanguageMismatch")
  protected String[] getEnvironmentClasses() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  protected void addEnvironmentClass(@Language("JAVA") @NotNull String classText) {
    myFixture.addClass(classText);
  }

  protected final void doStatementTest(@Language(value="JAVA", prefix="@SuppressWarnings(\"all\") class X { void m() {", suffix="}}") @NotNull String statementText) {
    doTest("class X { void m() {" + statementText + "}}");
  }

  protected final void doMemberTest(@Language(value="JAVA", prefix="@SuppressWarnings(\"all\") class X {", suffix="}") @NotNull String memberText) {
    doTest("class X {" + memberText + "}");
  }

  protected final void doTest(@Language("JAVA") @NotNull String classText) {
    doTest(classText, "X.java");
  }

  protected final void assertQuickFixNotAvailable(String name) {
    assertEmpty(myFixture.filterAvailableIntentions(name));
  }

  protected final void checkQuickFix(String name, @Language("JAVA") String result) {
    final IntentionAction intention = myFixture.getAvailableIntention(name);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult(result);
  }

  protected final void checkQuickFix(String intentionName) {
    final IntentionAction intention = myFixture.getAvailableIntention(intentionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  protected final void doTest(@Language("JAVA") @NotNull String classText, String fileName) {
    final StringBuilder newText = new StringBuilder();
    int start = 0;
    int end = classText.indexOf("/*");
    while (end >= 0) {
      newText.append(classText, start, end);
      start = end + 2;
      end = classText.indexOf("*/", end);
      if (end < 0) {
        throw new IllegalArgumentException("invalid class text");
      }
      final String text = classText.substring(start, end);
      if (text.isEmpty()) {
        newText.append("</warning>");
      }
      else if ("!".equals(text)) {
        newText.append("</error>");
      }
      else if ("_".equals(text)) {
        newText.append("<caret>");
      }
      else if (text.startsWith("!")) {
        newText.append("<error descr=\"").append(text.substring(1)).append("\">");
      }
      else if (text.startsWith(" ")) {
        newText.append("/*").append(text).append("*/");
      }
      else {
        newText.append("<warning descr=\"").append(text).append("\">");
      }
      start = end + 2;
      end = classText.indexOf("/*", end + 1);
    }
    newText.append(classText, start, classText.length());
    myFixture.configureByText(fileName, newText.toString());
    myFixture.testHighlighting(true, false, false);
  }

  @Override
  protected String getBasePath() {
    final String className = getInspectionClass().getName();
    final String[] words = className.split("\\.");
    final StringBuilder basePath = new StringBuilder(INSPECTION_GADGETS_TEST_DATA_PATH);
    final int lastWordIndex = words.length - 1;
    for (int i = 0; i < lastWordIndex; i++) {
      String word = words[i];
      if (word.equals("ig")) {
        //noinspection SpellCheckingInspection
        word = "igtest";
      }
      basePath.append(word).append('/');
    }
    String lastWord = words[lastWordIndex];
    lastWord = StringUtil.trimEnd(lastWord, "Inspection");
    final int length = lastWord.length();
    boolean upperCase = false;
    for (int i = 0; i < length; i++) {
      final char ch = lastWord.charAt(i);
      if (Character.isUpperCase(ch)) {
        if (!upperCase) {
          upperCase = true;
          if (i != 0) {
            basePath.append('_');
          }
        }
        basePath.append(Character.toLowerCase(ch));
      }
      else {
        upperCase = false;
        basePath.append(ch);
      }
    }
    return basePath.toString();
  }

  protected Class<? extends InspectionProfileEntry> getInspectionClass() {
    final InspectionProfileEntry inspection = getInspection();
    assertNotNull("File-based tests should either return an inspection or override this method", inspection);
    return inspection.getClass();
  }

  protected final void doTest() {
    doNamedTest(getTestName(false));
  }

  protected final void doNamedTest(String name) {
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, false, false);
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtilRt;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.RegExpParserDefinition;
import org.intellij.lang.regexp.intention.CheckRegExpForm;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class JavaCheckRegexpWithFlagsTest extends JavaCodeInsightTestCase {

  private static String getJavaText(@NotNull String regExp, int flags) {
    return "import java.util.regex.Pattern;\n" +
           "\n" +
           "public class Test {\n" +
           "  public void foo() {\n" +
           "    Pattern.compile(\"<caret>" + regExp + "\", " + flags + ");\n" +
           "  }\n" +
           "}\n";
  }

  private void defaultTest(@NotNull String regExp, int flags, String @NotNull [] matching, String @NotNull [] nonMatching) {
    LanguageParserDefinitions.INSTANCE.addExplicitExtension(RegExpLanguage.INSTANCE,
                                                            new RegExpParserDefinition());

    // the 1.8 mock jdk has the needed Pattern class
    Sdk jdk18 = IdeaTestUtil.getMockJdk18();
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(jdk18, myProject));
    PsiTestUtil.removeAllRoots(myModule, jdk18);

    configureByText(JavaFileType.INSTANCE, getJavaText(regExp, flags));
    final PsiFile regexpFile = setUpRegexpInjectionAndGetRegexpFile();

    for (String text : matching) {
      assertTrue("Text '" + text + "' should match", CheckRegExpForm.isMatchingTextTest(regexpFile, text));
    }
    for (String text : nonMatching) {
      assertFalse("Text '" + text + "' should not match", CheckRegExpForm.isMatchingTextTest(regexpFile, text));
    }
  }

  @NotNull
  private PsiFile setUpRegexpInjectionAndGetRegexpFile() {
    final PsiFile file = getFile();

    int offsetWithRegexp = file.getText().indexOf("Pattern.compile(\"")
      + "Pattern.compile(\"".length();

    final PsiElement stringLiteralLeaf = file.findElementAt(offsetWithRegexp);
    assertNotNull(stringLiteralLeaf);
    assertNotNull(stringLiteralLeaf.getParent());
    assertTrue(stringLiteralLeaf.getParent() instanceof PsiLanguageInjectionHost);

    final PsiLanguageInjectionHost elementWithInjection = ((PsiLanguageInjectionHost)stringLiteralLeaf.getParent());

    InjectedLanguageManager.getInstance(file.getProject()).enumerateEx(elementWithInjection, file, false, (injectedPsi, places) -> {
    });

    assertTrue(InjectedLanguageUtil.hasInjections(elementWithInjection));
    final PsiElement elementInInjected =
      InjectedLanguageUtil.findElementInInjected(elementWithInjection, offsetWithRegexp);

    final PsiFile regexpFile = PsiTreeUtil.getParentOfType(elementInInjected, PsiFile.class);
    assertNotNull(regexpFile);

    return regexpFile;
  }

  public void testSimple() {
    defaultTest("abc|def|xy.", 0, new String[]{"abc", "def", "xyz"}, new String[]{"", "abcd", "Abc", "xy\n"});
  }

  public void testEnsureFlagsParameterIsUsed() {
    defaultTest("abc|def|xy.", Pattern.DOTALL, new String[]{"xyz", "xy\n"}, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void testEnsureJavaNotRubyModeIsEnabled() {
    defaultTest("(?ms:^abc$.*)|(?m:xy.)", 0, new String[]{"abc", "abc\na", "xyz"}, new String[]{"xy\n"});
  }
}

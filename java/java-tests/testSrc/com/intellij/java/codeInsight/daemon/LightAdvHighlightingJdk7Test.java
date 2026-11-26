// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SafeVarargsHasNoEffectInspection;
import com.intellij.codeInspection.SafeVarargsOnNonReifiableTypeInspection;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnreachableCatchInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LightAdvHighlightingJdk7Test {
  // has to have src.zip and weird method handle signatures
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(
      new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection(),
      new SafeVarargsHasNoEffectInspection(), new SafeVarargsOnNonReifiableTypeInspection(),
      new UnreachableCatchInspection());
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, myFixture.getModule(), myFixture.getTestRootDisposable());
  }

  private void doTest(boolean checkWarnings, boolean checkInfos, InspectionProfileEntry... inspections) {
    daemonFixture.get().getFixture().enableInspections(inspections);
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Test public void testAllJava15Features() { doTest(false, false); }
  @Test public void testEnumSyntheticMethods() { doTest(false, false); }
  @Test public void testDuplicateAnnotations() { doTest(false, false); }
  @Test public void testSwitchByString() { doTest(false, false); }
  @Test public void testSwitchByInaccessibleEnum() { doTest(false, false); }
  @Test public void testMultipleConstructors() { doTest(false, false); }
  @Test public void testHighlightInaccessibleFromClassModifierList() { doTest(false, false); }
  @Test public void testInnerInTypeArguments() { doTest(false, false); }
  @Test public void testRawSubstitutor() { doTest(false, false); }
  @Test public void testIncompleteDiamonds() { doTest(false, false); }
  @Test public void testResolveConflictDiamonds() { doTest(false, false); }

  @Test
  public void testDynamicallyAddIgnoredAnnotations() {
    ExtensionPoint<EntryPoint> point = EntryPointsManagerBase.DEAD_CODE_EP_NAME.getPoint();
    EntryPoint extension = new EntryPoint() {
      @NotNull @Override public String getDisplayName() { return "duh"; }
      @Override public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) { return false; }
      @Override public boolean isEntryPoint(@NotNull PsiElement psiElement) { return false; }
      @Override public boolean isSelected() { return false; }
      @Override public void setSelected(boolean selected) { }
      @Override public void readExternal(Element element) { }
      @Override public void writeExternal(Element element) { }
      @Override public String[] getIgnoreAnnotations() { return new String[]{"MyAnno"}; }
    };

    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspectionBase(true));

    doTest(true, false);
    List<HighlightInfo> infos = daemonFixture.get().doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, infos.size()); // unused class and unused method

    point.registerExtension(extension, daemonFixture.get().getFixture().getTestRootDisposable());

    infos = daemonFixture.get().doHighlighting(HighlightSeverity.WARNING);
    assertTrue(infos.isEmpty(), infos.toString());
  }

  @Test public void testNumericLiterals() { doTest(false, false); }
  @Test public void testMultiCatch() { doTest(false, false); }
  @Test public void testMultiCatchRethrowConditional() { doTest(false, false); }
  @Test public void testMultiCatchParametersComparison() { doTest(false, false); }
  @Test public void testTryWithResources() { doTest(false, false); }
  @Test public void testTryWithResourcesWarn() { doTest(true, false, new DefUseInspection()); }
  @Test public void testSafeVarargsApplicability() { doTest(true, false); }
  @Test public void testUncheckedGenericsArrayCreation() { doTest(true, false); }
  @Test public void testGenericsArrayCreation() { doTest(false, false); }
  @Test public void testCannotCreateArrayWithEmptyDiamond() { doTest(false, false); }
  @Test public void testPreciseRethrow() { doTest(false, false); }
  @Test public void testPreciseRethrowCaptured() { doTest(false, false); }
  @Test public void testPreciseRethrowNonAssignableToException() { doTest(false, false); }
  @Test public void testPreciseRethrowOfOneExceptionInTheBlock() { doTest(false, false); }
  @Test public void testPreciseRethrowWithRuntimeExceptionDeclared() { doTest(false, false); }
  @Test public void testImprovedCatchAnalysis() { doTest(true, false); }
  @Test public void testPolymorphicTypeCast() { doTest(true, false); }
  @Test public void testTypeCastInInstanceof() { doTest(true, false); }
  @Test public void testErasureClashConfusion() { doTest(true, false, new UnusedDeclarationInspectionBase(true)); }
  @Test public void testUnused() { doTest(true, false, new UnusedDeclarationInspectionBase(true)); }
  @Test public void testSuperBound() { doTest(false, false); }
  @Test public void testExtendsBound() { doTest(false, false); }
  @Test public void testIDEA84533() { doTest(false, false); }
  @Test public void testClassLiteral() { doTest(false, false); }
  @Test public void testUncheckedWarning() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA59290() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA70620() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA60166() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA21432() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA99357() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA26738() { doTest(true, false); }
  @Test public void testUncheckedWarningIDEA99536() { doTest(true, false); }
  @Test public void testEnclosingInstance() { doTest(false, false); }
  @Test public void testIDEA122519EnclosingInstance() { doTest(false, false); }
  @Test public void testWrongArgsAndUnknownTypeParams() { doTest(false, false); }
  @Test public void testAmbiguousMethodCallIDEA97983() { doTest(false, false); }
  @Test public void testAmbiguousMethodCallIDEA100314() { doTest(false, false); }
  @Test public void testAmbiguousMethodCallIDEA67668() { doTest(false, false); }
  @Test public void testAmbiguousMethodCallIDEA67671() { doTest(false, false); }
  @Test public void testAmbiguousMethodCallIDEA67669() { doTest(false, false); }
  @Test public void testInstanceMemberNotAccessibleInStaticContext() { doTest(false, false); }
  @Test public void testRejectedTypeParamsForConstructor() { doTest(false, false); }
  @Test public void testAnnotationArgs() { doTest(false, false);}
  @Test public void testIDEA70890() { doTest(false, false); }
  @Test public void testIDEA63731() { doTest(false, false); }
  @Test public void testIDEA62056() { doTest(false, false); }
  @Test public void testIDEA78916() { doTest(false, false); }
  @Test public void testIDEA111420() { doTest(false, false); }
  @Test public void testIDEA111450() { doTest(true, false); }
  @Test public void testExternalizable() { doTest(true, false); }
  @Test public void testAccessToStaticMethodsFromInterfaces() { doTest(true, false); }
  @Test public void testUncheckedExtendedWarnings() { doTest(true, false); }
  @Test public void testInaccessibleInferredTypeForVarargsArgument() { doTest(false, false);}
  @Test public void testRuntimeClassCast() { doTest(true, false);}
  @Test public void testTryWithResourcesWithMultipleCloseInterfaces() { doTest(false, false);}
  @Test public void testIDEA138978() { doTest(false, false); }
  @Test public void testIntersectionTypeCast() { doTest(false, false); }
  @Test public void testUsedMethodCalledViaReflectionInTheSameFile() { doTest(true, false); }
  @Test public void testCatchSubclassOfThrownException() { doTest(true, false); }
  @Test public void testNoUncheckedWarningOnRawSubstitutor() { doTest(true, false); }
  @Test public void testArrayInitializerTypeCheckVariableType() { doTest(false, false);}

  @Test
  public void testJavaUtilCollections_NoVerify_Stress() {
    ApplicationManagerEx.runInStressTest(true, () -> {
      LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
      final String text = ReadAction.computeBlocking(() -> {
        PsiClass collectionsClass = JavaPsiFacade.getInstance(myFixture.getProject())
          .findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(myFixture.getModule()));
        assertNotNull(collectionsClass);
        collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
        assertFalse(collectionsClass instanceof PsiCompiledElement);
        return collectionsClass.getContainingFile().getText();
      });
      daemonFixture.get().configureFromFileText(
        "Collections.java",
        StringUtil.convertLineSeparators(StringUtil.replace(text, "package java.util;", "package java.utilx; import java.util.*;")));
      daemonFixture.get().doTestConfiguredFile(false, false, null);
    });
  }
}
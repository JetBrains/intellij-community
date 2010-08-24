package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;


public class GenericsHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting";

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection(), new UnusedSymbolLocalInspection(), new UnusedImportLocalInspection()};
  }

  private void doTest(boolean checkWarnings) throws Exception {
    doTest(getTestName(false) + ".java", checkWarnings);
  }

  private void doTest(@NonNls String filePath, boolean checkWarnings) throws Exception {
    doTest(BASE_PATH + "/" + filePath, checkWarnings, false);
  }

  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevel level = getTestName(false).contains("Level6") ? LanguageLevel.JDK_1_6 : LanguageLevel.JDK_1_5;
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(level);
  }

  @Override protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }


  public void testReferenceTypeParams() throws Exception { doTest(false); }
  public void testOverridingMethods() throws Exception { doTest(false); }
  public void testTypeParameterBoundsList() throws Exception { doTest(false); }
  public void testClassInheritance() throws Exception { doTest(false); }
  public void testTypeInference() throws Exception { doTest(false); }
  public void testRaw() throws Exception { doTest(true); }
  public void testExceptions() throws Exception { doTest(false); }
  public void testExplicitMethodParameters() throws Exception { doTest(false); }
  public void testExplicitMethodParameters1() throws Exception { doTest(false); }
  public void testInferenceWithBounds() throws Exception {doTest(false);}
  public void testInferenceWithSuperBounds() throws Exception {doTest(false);}
  public void testInferenceWithUpperBoundPromotion() throws Exception {doTest(false);}
  public void testVariance() throws Exception {doTest(false);}
  public void testForeachTypes() throws Exception {doTest(false);}
  public void testRawOverridingMethods() throws Exception {doTest(false);}
  public void testAutoboxing() throws Exception {doTest(false);}
  public void testAutoboxingMethods() throws Exception {doTest(false);}
  public void testAutoboxingConstructors() throws Exception {doTest(false);}
  public void testEnumWithAbstractMethods() throws Exception { doTest(false); }
  public void testEnum() throws Exception { doTest(false); }
  public void testSameErasure() throws Exception { doTest(false); }

  public void testMethods() throws Exception { doTest(false); }
  public void testStaticImports() throws Exception { doTest(true); }
  public void testUncheckedCasts() throws Exception { doTest(true); }
  public void testUncheckedOverriding() throws Exception { doTest(true); }
  public void testWildcardTypes() throws Exception { doTest(true); }
  public void testConvertibleTypes() throws Exception { doTest(true); }

  public void testIntersectionTypes() throws Exception { doTest(true); }
  public void testVarargs() throws Exception { doTest(true); }
  public void testTypeArgsOnRaw() throws Exception { doTest(false); }
  public void testConditionalExpression() throws Exception { doTest(false); }

  public void testUnused() throws Exception { doTest(true); }

  public void testIDEADEV7337() throws Exception { doTest(true); }
  public void testIDEADEV10459() throws Exception { doTest(true); }
  public void testIDEADEV12951() throws Exception { doTest(true); }
  public void testIDEADEV13011() throws Exception { doTest(true); }
  public void testIDEADEV14006() throws Exception { doTest(true); }
  public void testIDEADEV14103() throws Exception { doTest(true); }
  public void testIDEADEV15534() throws Exception { doTest(true); }
  public void testIDEADEV23157() throws Exception { doTest(true); }
  public void testIDEADEV24166() throws Exception { doTest(true); }
  public void testIDEADEV25778() throws Exception { doTest(true); }
  public void testIDEADEV57343() throws Exception { doTest(false); }

  public void testGenericExtendException() throws Exception { doTest(false); }
  public void testSameErasureDifferentReturnTypes() throws Exception { doTest(false); }
  public void testDeepConflictingReturnTypes() throws Exception { doTest(false); }
  public void testInheritFromTypeParameter() throws Exception { doTest(false); }
  public void testAnnotationsAsPartOfModifierList() throws Exception { doTest(false); }
  public void testImplementAnnotation() throws Exception { doTest(false); }
  public void testOverrideAtLanguageLevel6() throws Exception { doTest(false); }
  public void testOverrideAtLanguageLevel5() throws Exception { doTest(false); }
  public void testSuperMethodCallWithErasure() throws Exception { doTest(false); }
  public void testWildcardCastConversion() throws Exception { doTest(false); }
  public void testTypeWithinItsWildcardBound() throws Exception { doTest(false); }

  public void testJavaUtilCollections() throws Exception {
    PsiClass collectionsClass = getJavaFacade().findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(getModule()));

    assertNotNull(collectionsClass);
    collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
    final String text = collectionsClass.getContainingFile().getText();
    configureFromFileText("Collections.java", text.replaceAll("\r","\n"));
    doTestConfiguredFile(false, false);
  }
}

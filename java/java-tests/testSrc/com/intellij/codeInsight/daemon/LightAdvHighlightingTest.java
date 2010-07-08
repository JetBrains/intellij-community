package com.intellij.codeInsight.daemon;

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedCodeExtension;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclaration;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase.filter;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";
  private UnusedSymbolLocalInspection myUnusedSymbolLocalInspection;

  private void doTest(boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_1_4);
  }


  protected LocalInspectionTool[] configureLocalInspectionTools() {
    myUnusedSymbolLocalInspection = new UnusedSymbolLocalInspection();
    return new LocalInspectionTool[]{
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclaration(),
      myUnusedSymbolLocalInspection,
      new UnusedImportLocalInspection(),
      new UncheckedWarningLocalInspection()};
  }

  public void testCanHaveBody() throws Exception { doTest(false, false); }
  public void testInheritFinal() throws Exception { doTest(false, false); }
  public void testBreakOutside() throws Exception { doTest(false, false); }
  public void testLoop() throws Exception { doTest(false, false); }
  public void testIllegalModifiersCombination() throws Exception { doTest(false, false); }
  public void testModifierAllowed() throws Exception { doTest(false, false); }
  public void testAbstractMethods() throws Exception { doTest(false, false); }
  public void testInstantiateAbstract() throws Exception { doTest(false, false); }
  public void testDuplicateClassMethod() throws Exception { doTest(false, false); }
  public void testa10() throws Exception { doTest(false, false); }
  public void testa11() throws Exception { doTest(false, false); }
  public void testInvalidExpressions() throws Exception { doTest(false, false); }
  public void testIllegalVoidType() throws Exception { doTest(false, false); }
  public void testIllegalType() throws Exception { doTest(false, false); }
  public void testOperatorApplicability() throws Exception { doTest(false, false); }
  public void testIncompatibleTypes() throws Exception { doTest(false, false); }
  public void testCtrCallIsFirst() throws Exception { doTest(false, false); }
  public void testAccessLevelClash() throws Exception { doTest(false, false); }
  public void testa16() throws Exception { doTest(false, false); }
  public void testOverrideConflicts() throws Exception { doTest(false, false); }
  public void testOverriddenMethodIsFinal() throws Exception { doTest(false, false); }
  public void testa18() throws Exception { doTest(false, false); }
  public void testUnreachable() throws Exception { doTest(false, false); }
  public void testFinalFieldInit() throws Exception { doTest(false, false); }
  public void testLocalVariableInitialization() throws Exception { doTest(false, false); }
  public void testa22() throws Exception { doTest(false, false); }
  public void testa22_1() throws Exception { doTest(false, false); }
  public void testAssignToFinal() throws Exception { doTest(false, false); }
  public void testa24() throws Exception { doTest(false, false); }
  public void testa25() throws Exception { doTest(false, false); }
  public void testMustBeBoolean() throws Exception { doTest(false, false); }

  public void testNumericLiterals() throws Exception { doTest(false, false); }
  public void testInitializerCompletion() throws Exception { doTest(false, false); }

  public void testa28() throws Exception { doTest(false, false); }
  public void testDuplicateSwitchLabels() throws Exception { doTest(false, false); }
  public void testStringSwitchLabels() throws Exception { doTest(false, false); }
  public void testa30() throws Exception { doTest(false, false); }
  public void testStaticOverride() throws Exception { doTest(false, false); }
  public void testa32() throws Exception { doTest(false, false); }
  public void testReferenceMemberBeforeCtrCalled() throws Exception { doTest(false, false); }
  public void testa34() throws Exception { doTest(false, false); }
  public void testa35() throws Exception { doTest(false, false); }
  public void testa35_1() throws Exception { doTest(false, false); }
  public void testa35_2() throws Exception { doTest(false, false); }
  public void testSillyAssignment() throws Exception { doTest(true, false); }
  public void testa37() throws Exception { doTest(false, false); }
  public void testa38() throws Exception { doTest(false, false); }
  public void testa39() throws Exception { doTest(false, false); }
  public void testMustBeThrowable() throws Exception { doTest(false, false); }
  public void testUnhandledMessingWithFinally() throws Exception { doTest(false, false); }
  public void testSerializableStuff() throws Exception { doTest(true, false); }
  public void testDeprecated() throws Exception { doTest(true, false); }
  public void testJavadoc() throws Exception { enableInspectionTool(new JavaDocLocalInspection()); doTest(true, false); }
  public void testa44() throws Exception { doTest(false, false); }
  public void testa45() throws Exception { doTest(false, false); }

  public void testExceptionNeverThrown() throws Exception { doTest(true, false); }
  public void testExceptionNeverThrownInTry() throws Exception { doTest(false, false); }

  public void testa47() throws Exception { doTest(false, false); }
  public void testa48() throws Exception { doTest(false, false); }

  public void testa49() throws Exception { doTest(false, false); }
  public void testa50() throws Exception { doTest(false, false); }
  public void testa52() throws Exception { doTest(false, false); }
  public void testMethodCalls() throws Exception { doTest(false, false); }
  public void testa54() throws Exception { doTest(false, false); }
  public void testa54_1() throws Exception { doTest(true, false); } //duplicate imports
  public void testa55() throws Exception { doTest(false, false); }
  public void testQualifiedNew() throws Exception { doTest(false, false); }
  public void testEnclosingInstance() throws Exception { doTest(false, false); }

  public void testa59() throws Exception { doTest(true, false); } // static via instabnce
  public void testa60() throws Exception { doTest(true, false); } //illegal qualified this or super

  public void testAmbiguousMethodCall() throws Exception { doTest(false, false); }

  public void testImplicitConstructor() throws Exception { doTest(false, false); }
  public void testDotBeforeDecl() throws Exception { doTest(false, false); }
  public void testComputeConstant() throws Exception { doTest(false, false); }

  public void testAnonInAnon() throws Exception { doTest(false, false); }
  public void testAnonBaseRef() throws Exception { doTest(false, false); }
  public void testReturn() throws Exception { doTest(false, false); }
  public void testInterface() throws Exception { doTest(false, false); }
  public void testExtendsClause() throws Exception { doTest(false, false); }
  public void testMustBeFinal() throws Exception { doTest(false, false); }

  public void testXXX() throws Exception { doTest(false, false); }
  public void testUnused() throws Exception { doTest(true, false); }
  public void testQualifierBeforeClassName() throws Exception { doTest(false, false); }
  public void testQualifiedSuper() throws Exception { doTest(false, false); }
  public void testCastFromVoid() throws Exception { doTest(false, false); }
  public void testCatchUnknownMethod() throws Exception { doTest(false, false); }
  public void testIDEADEV8822() throws Exception { doTest(false, false); }
  public void testIDEADEV9201() throws Exception { doTest(false, false); }
  public void testIDEADEV11877() throws Exception { doTest(false, false); }
  public void testIDEADEV25784() throws Exception { doTest(false, false); }
  public void testIDEADEV13249() throws Exception { doTest(false, false); }
  public void testIDEADEV11919() throws Exception { doTest(false, false); }
  public void testMethodCannotBeApplied() throws Exception { doTest(false, false); }

  public void testUnusedParamsOfPublicMethod() throws Exception { doTest(true, false); }
  public void testUnusedParamsOfPublicMethodDisabled() throws Exception {
    myUnusedSymbolLocalInspection.REPORT_PARAMETER_FOR_PUBLIC_METHODS = false;
    doTest(true, false);
  }
  public void testUnusedNonPrivateMembers() throws Exception {
    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);
    doTest(true, false);
  }
  public void testUnusedNonPrivateMembers2() throws Exception {
    ExtensionPoint<UnusedCodeExtension> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    UnusedCodeExtension extension = new UnusedCodeExtension() {
      @NotNull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(PsiElement psiElement) {
        return psiElement instanceof PsiMethod && ((PsiMethod)psiElement).getName().equals("myTestMethod");
      }

      @Override
      public boolean isSelected() {
        return false;
      }

      @Override
      public void setSelected(boolean selected) {

      }

      public void readExternal(Element element) {

      }

      public void writeExternal(Element element) {

      }
    };

    point.registerExtension(extension);
    
    try {

      UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
      enableInspectionTool(deadCodeInspection);

      doTest(true, false);
    }
    finally {
      point.unregisterExtension(extension);
    }

  }

  public void testNamesHighlighting() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest(false, true);
  }

  public static class MyAnnotator implements Annotator {
    public void annotate(@NotNull PsiElement psiElement, @NotNull final AnnotationHolder holder) {
      psiElement.accept(new XmlElementVisitor() {
        @Override public void visitXmlTag(XmlTag tag) {
          XmlAttribute attribute = tag.getAttribute("aaa", "");
          if (attribute != null) {
            holder.createWarningAnnotation(attribute, "AAATTR");
          }
        }

        @Override public void visitXmlToken(XmlToken token) {
          if (token.getTokenType() == XmlTokenType.XML_ENTITY_REF_TOKEN) {
            holder.createWarningAnnotation(token, "ENTITY");
          }
        }
      });
    }
  }
  public void testInjectedAnnotator() throws Exception {
    Annotator annotator = new MyAnnotator();
    Language xml = StdFileTypes.XML.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(xml, annotator);
    try {
      List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(xml);
      assertTrue(list.toString(), list.contains(annotator));
      doTest(BASE_PATH + "/" + getTestName(false) + ".xml",true,false);
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(xml, annotator);
    }

    List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(xml);
    assertFalse(list.toString(), list.contains(annotator));
  }

  public void testSOEForTypeOfHugeBinaryExpression() throws IOException {
    configureFromFileText("a.java", "class A { String s = \"\"; }");
    assertEmpty(filter(doHighlighting(), HighlightSeverity.ERROR));

    PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];

    // have to manipulate PSI, will get SOE in BlockSupportImpl otherwise
    PsiExpression literal = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText("\"xxx\"", field);

    PsiBinaryExpression binary = (PsiBinaryExpression)JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText("a+b", field);
    for (int i=0; i<2000;i++) {
      PsiExpression expression = field.getInitializer();
      binary.getLOperand().replace(expression);
      binary.getROperand().replace(literal);

      UIUtil.dispatchAllInvocationEvents();

      expression.replace(binary);
      PostprocessReformattingAspect.getInstance(getProject()).clear(); // OOM otherwise
    }

    field.getInitializer().getType(); // SOE
  }
}

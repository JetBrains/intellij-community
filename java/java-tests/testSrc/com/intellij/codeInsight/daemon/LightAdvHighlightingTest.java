/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.reference.EntryPoint;
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

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

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclaration(),
      myUnusedSymbolLocalInspection = new UnusedSymbolLocalInspection(),
      new UnusedImportLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
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
  public void testStringLiterals() throws Exception { doTest(false, false); }
  public void testStaticInInner() throws Exception { doTest(false, false); }
  public void testInvalidExpressions() throws Exception { doTest(false, false); }
  public void testIllegalVoidType() throws Exception { doTest(false, false); }
  public void testIllegalType() throws Exception { doTest(false, false); }
  public void testOperatorApplicability() throws Exception { doTest(false, false); }
  public void testIncompatibleTypes() throws Exception { doTest(false, false); }
  public void testCtrCallIsFirst() throws Exception { doTest(false, false); }
  public void testAccessLevelClash() throws Exception { doTest(false, false); }
  public void testCasts() throws Exception { doTest(false, false); }
  public void testOverrideConflicts() throws Exception { doTest(false, false); }
  public void testOverriddenMethodIsFinal() throws Exception { doTest(false, false); }
  public void testMissingReturn() throws Exception { doTest(false, false); }
  public void testUnreachable() throws Exception { doTest(false, false); }
  public void testFinalFieldInit() throws Exception { doTest(false, false); }
  public void testLocalVariableInitialization() throws Exception { doTest(false, false); }
  public void testVarDoubleInitialization() throws Exception { doTest(false, false); }
  public void testFieldDoubleInitialization() throws Exception { doTest(false, false); }
  public void testAssignToFinal() throws Exception { doTest(false, false); }
  public void testUnhandledExceptionsInSuperclass() throws Exception { doTest(false, false); }
  public void testAssignmentCompatible () throws Exception { doTest(false, false); }
  public void testMustBeBoolean() throws Exception { doTest(false, false); }

  public void testNumericLiterals() throws Exception { doTest(false, false); }
  public void testInitializerCompletion() throws Exception { doTest(false, false); }

  public void testUndefinedLabel() throws Exception { doTest(false, false); }
  public void testDuplicateSwitchLabels() throws Exception { doTest(false, false); }
  public void testStringSwitchLabels() throws Exception { doTest(false, false); }
  public void testIllegalForwardReference() throws Exception { doTest(false, false); }
  public void testStaticOverride() throws Exception { doTest(false, false); }
  public void testCyclicInheritance() throws Exception { doTest(false, false); }
  public void testReferenceMemberBeforeCtrCalled() throws Exception { doTest(false, false); }
  public void testLabels() throws Exception { doTest(false, false); }
  public void testUnclosedBlockComment() throws Exception { doTest(false, false); }
  public void testUnclosedComment() throws Exception { doTest(false, false); }
  public void testUnclosedDecl() throws Exception { doTest(false, false); }
  public void testSillyAssignment() throws Exception { doTest(true, false); }
  public void testTernary() throws Exception { doTest(false, false); }
  public void testDuplicateClass() throws Exception { doTest(false, false); }
  public void testCatchType() throws Exception { doTest(false, false); }
  public void testMustBeThrowable() throws Exception { doTest(false, false); }
  public void testUnhandledMessingWithFinally() throws Exception { doTest(false, false); }
  public void testSerializableStuff() throws Exception { doTest(true, false); }
  public void testDeprecated() throws Exception { doTest(true, false); }
  public void testJavadoc() throws Exception { enableInspectionTool(new JavaDocLocalInspection()); doTest(true, false); }
  public void testExpressionsInSwitch () throws Exception { doTest(false, false); }
  public void testAccessInner () throws Exception { doTest(false, false); }

  public void testExceptionNeverThrown() throws Exception { doTest(true, false); }
  public void testExceptionNeverThrownInTry() throws Exception { doTest(false, false); }

  public void testSwitchStatement() throws Exception { doTest(false, false); }
  public void testAssertExpression() throws Exception { doTest(false, false); }

  public void testSynchronizedExpression() throws Exception { doTest(false, false); }
  public void testExtendMultipleClasses() throws Exception { doTest(false, false); }
  public void testRecursiveConstructorInvocation() throws Exception { doTest(false, false); }
  public void testMethodCalls() throws Exception { doTest(false, false); }
  public void testSingleTypeImportConflicts() throws Exception { doTest(false, false); }
  public void testMultipleSingleTypeImports() throws Exception { doTest(true, false); } //duplicate imports
  public void testNotAllowedInInterface() throws Exception { doTest(false, false); }
  public void testQualifiedNew() throws Exception { doTest(false, false); }
  public void testEnclosingInstance() throws Exception { doTest(false, false); }

  public void testStaticViaInstance() throws Exception { doTest(true, false); } // static via instance
  public void testQualifiedThisSuper() throws Exception { doTest(true, false); } //illegal qualified this or super

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
  public void testDefaultPackageClassInStaticImport() throws Exception { doTest(false, false); }

  public void testUnusedParamsOfPublicMethod() throws Exception { doTest(true, false); }
  public void testInnerClassesShadowing() throws Exception { doTest(false, false); }

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
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    EntryPoint extension = new EntryPoint() {
      @NotNull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) {
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
      public void setSelected(boolean selected) { }

      @Override
      public void readExternal(Element element) { }

      @Override
      public void writeExternal(Element element) { }
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
  public void testUnusedNonPrivateMembersReferencedFromText() throws Exception {
    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);

    doTest(true, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        PsiDirectory directory = myFile.getParent();
        assertNotNull(myFile.toString(), directory);
        PsiFile txt = directory.createFile("x.txt");
        VirtualFile vFile = txt.getVirtualFile();
        assertNotNull(txt.toString(), vFile);
        try {
          VfsUtil.saveText(vFile, "XXX");
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
  }

  public void testNamesHighlighting() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testMultiFieldDeclNames() throws Exception {
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public static class MyAnnotator implements Annotator {
    @Override
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
    assertEmpty(highlightErrors());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final StringBuilder sb = new StringBuilder("\"-\"");
    for (int i = 0; i < 10000; i++) sb.append("+\"b\"");
    final String hugeExpr = sb.toString();
    final int pos = getEditor().getDocument().getText().indexOf("\"\"");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getEditor().getDocument().replaceString(pos, pos + 2, hugeExpr);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    });

    final PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];
    final PsiExpression expression = field.getInitializer();
    assert expression != null;
    final PsiType type = expression.getType();
    assert type != null;
    assertEquals("PsiType:String", type.toString());
  }

  public void testSOEForCyclicInheritance() throws IOException {
    configureFromFileText("a.java", "class A extends B { String s = \"\"; void f() {}} class B extends A { void f() {} } ");
    doHighlighting();
  }

  public void testClassicRethrow() throws Exception { doTest(false, false); }
  public void testRegexp() throws Exception { doTest(false, false); }
  public void testUnsupportedFeatures() throws Exception { doTest(false, false); }
  public void testThisBeforeSuper() throws Exception { doTest(false, false); }
  public void testExplicitConstructorInvocation() throws Exception { doTest(false, false); }
}

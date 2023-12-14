// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ReassignedVariableInspection;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * This class is for "lightweight" tests only, i.e., those which can run inside default light project.
 * For "heavyweight" tests use {@link AdvHighlightingTest}
 */
public class LightAdvHighlightingTest extends LightDaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  private UnusedDeclarationInspectionBase myUnusedDeclarationInspection;

  private void doTest(boolean checkWarnings) {
    doTest(checkWarnings, false);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUnusedDeclarationInspection = new UnusedDeclarationInspection(isUnusedInspectionRequired());
    enableInspectionTool(myUnusedDeclarationInspection);
    enableInspectionTool(new UnusedImportInspection());
    enableInspectionTool(new AccessStaticViaInstance());
    enableInspectionTool(new ReassignedVariableInspection());
    setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  private boolean isUnusedInspectionRequired() {
    return getTestName(false).contains("UnusedInspection");
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclarationLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  public void testCanHaveBody() { doTest(false); }
  public void testInheritFinal() { doTest(false); }
  public void testBreakOutside() { doTest(false); }
  public void testLoop() { doTest(false); }
  public void testIllegalModifiersCombination() { doTest(false); }
  public void testModifierAllowed() { doTest(false); }
  public void testAbstractMethods() { doTest(false); }
  public void testInstantiateAbstract() { doTest(false); }
  public void testDuplicateClassMethod() { doTest(false); }
  public void testStringLiterals() { doTest(false); }
  public void testStaticInInner() { doTest(false); }
  public void testStaticInInnerJava16() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, () -> doTest(false)); }
  public void testInvalidExpressions() { doTest(false); }
  public void testIllegalVoidType() { doTest(false); }
  public void testIllegalType() { doTest(false); }
  public void testOperatorApplicability() { doTest(false); }
  public void testIncompatibleTypes() { doTest(false); }
  public void testCtrCallIsFirst() { doTest(false); }
  public void testAccessLevelClash() { doTest(false); }
  public void testCasts() { doTest(false); }
  public void testOverrideConflicts() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.HIGHEST, () -> doTest(false)); }
  public void testOverriddenMethodIsFinal() { doTest(false); }
  public void testMissingReturn() { doTest(false); }
  public void testUnreachable() { doTest(false); }
  public void testUnreachableMultiFinally() { doTest(false); }
  public void testFinalFieldInit() { doTest(false); }
  public void testLocalVariableInitialization() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, () -> doTest(false)); }
  public void testVarDoubleInitialization() { doTest(false); }
  public void testFieldDoubleInitialization() { doTest(false); }
  public void testFinalFieldInitialization() { doTest(false); }
  public void testFinalFieldUsedInAnonymousArgumentListInsideInner() { doTest(false); }
  public void testAssignToFinal() { doTest(false); }
  public void testUnhandledExceptionsInSuperclass() { doTest(false); }
  public void testNoUnhandledExceptionsMultipleInheritance() { doTest(false); }
  public void testIgnoreSuperMethodsInMultipleOverridingCheck() { doTest(false); }
  public void testFalseExceptionsMultipleInheritance() { doTest(true); }
  public void testAssignmentCompatible () { setLanguageLevel(LanguageLevel.JDK_1_5); doTest(false); }
  public void testMustBeBoolean() { doTest(false); }

  public void testNumericLiterals() { doTest(false); }
  public void testInitializerCompletion() { doTest(false); }

  public void testUndefinedLabel() { doTest(false); }
  public void testDuplicateSwitchLabels() { doTest(false); }
  public void testStringSwitchLabels() { doTest(false); }
  public void testIllegalForwardReference() { doTest(false); }
  public void testStaticOverride() { doTest(false); }
  public void testCyclicInheritance() { doTest(false); }
  public void testReferenceMemberBeforeCtrCalled() { doTest(false); }
  public void testLabels() { doTest(false); }
  public void testUnclosedBlockComment() { doTest(false); }
  public void testUnclosedComment() { doTest(false); }
  public void testBadUnicodeEscapeInComment() { doTest(false); }
  public void testUnclosedDecl() { doTest(false); }
  public void testSillyAssignment() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(true, true);
  }
  public void testTernary() { doTest(false); }
  public void testDuplicateClass() { doTest(false); }
  public void testCatchType() { doTest(false); }
  public void testMustBeThrowable() { doTest(false); }
  public void testUnhandledMessingWithFinally() { doTest(false); }
  public void testSerializableStuff() {  doTest(true); }
  public void testDeprecated() { doTest(true); }
  public void testJavadoc() { enableInspectionTool(new JavadocDeclarationInspection()); doTest(true); }
  public void testExpressionsInSwitch () { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> doTest(false)); }
  public void testAccessInner() {
    Editor e = createSaveAndOpenFile("x/BeanContextServicesSupport.java",
                                     "package x;\n" +
                                     "public class BeanContextServicesSupport {" +
                                     "  protected class BCSSChild {" +
                                     "    class BCSSCServiceClassRef {" +
                                     "      void addRequestor(Object requestor) {" +
                                     "      }" +
                                     "    }" +
                                     "  }" +
                                     "}");
    Editor e2 = createSaveAndOpenFile("x/Component.java",
                                      "package x;\n" +
                                      "public class Component {" +
                                      "  protected class FlipBufferStrategy {" +
                                      "    protected int numBuffers;" +
                                      "    protected void createBuffers(int numBuffers){}" +
                                      "  }" +
                                      "}");
    try {
      UIUtil.dispatchAllInvocationEvents();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      doTest(false);
    }
    finally {
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(e.getDocument()));
      FileEditorManager.getInstance(getProject()).closeFile(file);
      VfsTestUtil.deleteFile(file);
      VirtualFile file2 = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(e2.getDocument()));
      FileEditorManager.getInstance(getProject()).closeFile(file2);
      VfsTestUtil.deleteFile(file2);
    }
  }

  public void testExceptionNeverThrown() { doTest(true); }
  public void testExceptionNeverThrownInTry() { doTest(false); }

  public void testSwitchStatement() { doTest(false); }
  public void testAssertExpression() { doTest(false); }

  public void testSynchronizedExpression() { doTest(false); }
  public void testExtendMultipleClasses() { doTest(false); }

  public void testRecursiveConstructorInvocation() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable()); // mutual recursion is prevented in contract inference
    doTest(false);
  }

  public void testMethodCalls() { doTest(false); }
  public void testSingleTypeImportConflicts() {
    createSaveAndOpenFile("sql/Date.java", "package sql; public class Date{}");
    UIUtil.dispatchAllInvocationEvents();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    doTest(false);
  }
  public void testMultipleSingleTypeImports() { doTest(true); } //duplicate imports
  public void testNotAllowedInInterface() { doTest(false); }
  public void testQualifiedNew() { doTest(false); }
  public void testEnclosingInstance() { doTest(false); }

  public void testStaticViaInstance() { doTest(true); } // static via instance
  public void testQualifiedThisSuper() { doTest(true); } //illegal qualified this or super

  public void testAmbiguousMethodCall() { doTest(false); }

  public void testImplicitConstructor() { doTest(false); }
  public void testDotBeforeDecl() { doTest(false); }
  public void testComputeConstant() { doTest(false); }

  public void testAnonInAnon() { doTest(false); }
  public void testAnonBaseRef() { doTest(false); }
  public void testReturn() { doTest(false); }
  public void testInterface() { doTest(false); }
  public void testExtendsClause() { doTest(false); }
  public void testMustBeFinal() { doTest(false); }

  public void testXXX() { doTest(false); }
  public void testUnused() { doTest(true, true); }
  public void testQualifierBeforeClassName() { doTest(false); }
  public void testQualifiedSuper() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), getTestRootDisposable());
    doTest(false);
  }

  public void testIgnoreImplicitThisReferenceBeforeSuperSinceJdk7() { doTest(false); }

  public void testCastFromVoid() { doTest(false); }
  public void testCatchUnknownMethod() { doTest(false); }
  public void testIDEADEV8822() { doTest(false); }
  public void testIDEADEV9201() { doTest(false); }
  public void testIDEADEV25784() { doTest(false); }
  public void testIDEADEV13249() { doTest(false); }
  public void testIDEADEV11919() { doTest(false); }
  public void testIDEA67829() { doTest(false); }
  public void testMethodCannotBeApplied() { doTest(false); }
  public void testUnusedParamsOfPublicMethod() { doTest(true); }
  public void testInnerClassesShadowing() { doTest(false); }

  public void testUnusedParamsOfPublicMethodDisabled() {
    UnusedSymbolLocalInspectionBase tool = myUnusedDeclarationInspection.getSharedLocalInspectionTool();
    assertNotNull(tool);
    String oldVal = tool.getParameterVisibility();
    try {
      tool.setParameterVisibility(PsiModifier.PRIVATE);
      doTest(true);
    }
    finally {
      tool.setParameterVisibility(oldVal);
    }
  }

  public void testUnusedInspectionNonPrivateMembers() {
    doTest(true);
  }

  public void testUnusedNonPrivateMembers2() {
    ExtensionPoint<EntryPoint> point = EntryPointsManagerBase.DEAD_CODE_EP_NAME.getPoint();
    EntryPoint extension = new EntryPoint() {
      @NotNull
      @Override
      public String getDisplayName() {
        return "duh";
      }

      @Override
      public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
        return false;
      }

      @Override
      public boolean isEntryPoint(@NotNull PsiElement psiElement) {
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

    point.registerExtension(extension, getTestRootDisposable());
    try {
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase(true);
      doTest(true);
    }
    finally {
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase();
    }
  }

  public void testUnusedInspectionNonPrivateMembersReferencedFromText() {
    doTest(true);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      PsiDirectory directory = getFile().getParent();
      assertNotNull(getFile().toString(), directory);
      PsiFile txt = directory.createFile("x.txt");
      VirtualFile vFile = txt.getVirtualFile();
      assertNotNull(txt.toString(), vFile);
      try {
        VfsUtil.saveText(vFile, "XXX");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
  }

  public void testNamesHighlighting() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testMultiFieldDeclNames() {
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testSOEForTypeOfHugeBinaryExpression() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = "class A { String s = \"\"; }";
    configureFromFileText("a.java", text);
    assertEmpty(highlightErrors());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    String hugeExpr = "\"-\"" + "+\"b\"".repeat(10000);
    int pos = getEditor().getDocument().getText().indexOf("\"\"");

    ApplicationManager.getApplication().runWriteAction(() -> {
      getEditor().getDocument().replaceString(pos, pos + 2, hugeExpr);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];
    PsiExpression expression = field.getInitializer();
    assert expression != null;
    PsiType type = expression.getType();
    assert type != null;
    assertEquals("PsiType:String", type.toString());
  }

  public void testSOEForCyclicInheritance() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = "class A extends B { String s = \"\"; void f() {}} class B extends A { void f() {} } ";
    configureFromFileText("a.java", text);
    doHighlighting();
  }

  public void testClassicRethrow() { doTest(false); }
  public void testRegexp() { doTest(false); }
  public void testUnsupportedFeatures() { doTest(false); }
  public void testThisBeforeSuper() { doTest(false); }
  public void testExplicitConstructorInvocation() { doTest(false); }
  public void testExtendFinalClass() { doTest(false); }
  public void testThisInInterface() { doTest(false); }
  public void testInnerClassConstantReference() { doTest(false); }
  public void testIDEA60875() { doTest(false); }
  public void testIDEA71645() { doTest(false); }
  public void testIDEA18343() { doTest(false); }
  public void testNewExpressionClass() { doTest(false); }
  public void testInnerClassObjectLiteralFromSuperExpression() { doTest(false); }
  public void testPrivateFieldInSuperClass() { doTest(false); }
  public void testNoEnclosingInstanceWhenStaticNestedInheritsFromContainingClass() { doTest(false); }
  public void testIDEA168768() { doTest(false); }
  public void testStatementWithExpression() { doTest(false); }

  public void testStaticMethodCalls() {
    doTestFile(BASE_PATH + "/" + getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testNestedLocalClasses() {
    doTest(false);
  }

  public void testAmbiguousConstants() {
    doTest(false);
  }

  public void testUnreachableArrayElementAssignment() { doTest(false); }
  
  public void testNotWellFormedExpressionStatementWithoutSemicolon() {
    doTest(false);
  }

  public void testTooManyArrayDimensions() { doTest(false);}

  public void testInsane() {
    configureFromFileText("x.java", "class X { \nx_x_x_x\n }");
    List<HighlightInfo> infos = highlightErrors();
    assertFalse(infos.isEmpty());
  }

  public void testIllegalWhitespaces() { doTest(false); }

  public void testMarkUsedDefaultAnnotationMethodUnusedInspection() {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest(true);
  }

  public void testNavigateByReassignVariables() {
    doTest(true, true);
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean old = settings.isNextErrorActionGoesToErrorsFirst();
    settings.setNextErrorActionGoesToErrorsFirst(true);
    try {
      int offset = getEditor().getCaretModel().getOffset();
      new GotoNextErrorHandler(true).invoke(getProject(), getEditor(), getFile());

      assertEquals(offset, getEditor().getCaretModel().getOffset());
    }
    finally {
      settings.setNextErrorActionGoesToErrorsFirst(old);
    }
  }

  public void testUninitializedVarComplexTernary() {
    doTest(false);
  }

  public void testArrayInitBeforeSuper() {
    doTest(false);
  }

  public void testThisReferencedInnerClass() {
    doTest(false);
  }

  public void testReferenceToImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      doTest(false);
    });
  }
}
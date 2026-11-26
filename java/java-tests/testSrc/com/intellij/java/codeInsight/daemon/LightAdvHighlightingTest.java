// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
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
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class is for "lightweight" tests only, i.e., those which can run inside default light project.
 * For "heavyweight" tests use {@link AdvHighlightingTest}
 */
@TestApplication
public class LightAdvHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), () -> List.of(
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclarationLocalInspection(),
      new UncheckedWarningLocalInspection()
    ));

  public static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  private UnusedDeclarationInspectionBase myUnusedDeclarationInspection;

  private void doTest(boolean checkWarnings) {
    doTest(checkWarnings, false);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @BeforeEach
  void setUp() {
    myUnusedDeclarationInspection = new UnusedDeclarationInspection(isUnusedInspectionRequired());
    daemonFixture.get().getFixture().enableInspections(myUnusedDeclarationInspection);
    daemonFixture.get().getFixture().enableInspections(new UnusedImportInspection());
    daemonFixture.get().getFixture().enableInspections(new AccessStaticViaInstance());
    daemonFixture.get().getFixture().enableInspections(new ReassignedVariableInspection());
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  private boolean isUnusedInspectionRequired() {
    return daemonFixture.get().getFixture().getTestName(false).contains("UnusedInspection");
  }

  @Test public void testCanHaveBody() { doTest(false); }
  @Test public void testInheritFinal() { doTest(false); }
  @Test public void testBreakOutside() { doTest(false); }
  @Test public void testLoop() { doTest(false); }
  @Test public void testForInitWithMultipleExpressions() { doTest(false); }
  @Test public void testIllegalModifiersCombination() { doTest(false); }
  @Test public void testModifierAllowed() { doTest(false); }
  @Test public void testAbstractMethods() { doTest(false); }
  @Test public void testInstantiateAbstract() { doTest(false); }
  @Test public void testDuplicateClassMethod() { doTest(false); }
  @Test public void testStringLiterals() { doTest(false); }
  @Test public void testStaticInInner() { doTest(false); }
  @Test
  public void testStaticInInnerJava16() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_16, () -> doTest(false));
  }
  @Test
  public void testStaticAccessViaInstanceForImplicitClasses() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_22_PREVIEW, () -> doTest(false));
  }
  @Test public void testInvalidExpressions() { doTest(false); }
  @Test public void testIllegalVoidType() { doTest(false); }
  @Test public void testIllegalType() { doTest(false); }
  @Test public void testOperatorApplicability() { doTest(false); }
  @Test public void testIncompatibleTypes() { doTest(false); }
  @Test public void testCtrCallIsFirst() { doTest(false); }
  @Test public void testAccessLevelClash() { doTest(false); }
  @Test public void testCasts() { doTest(false); }
  @Test
  public void testOverrideConflicts() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.HIGHEST, () -> doTest(false));
  }
  @Test public void testOverriddenMethodIsFinal() { doTest(false); }
  @Test public void testMissingReturn() { doTest(false); }
  @Test public void testUnreachable() { doTest(false); }
  @Test public void testUnreachableMultiFinally() { doTest(false); }
  @Test public void testFinalFieldInit() { doTest(false); }
  @Test
  public void testLocalVariableInitialization() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_1_5, () -> doTest(false));
  }
  @Test public void testVarDoubleInitialization() { doTest(false); }
  @Test public void testFieldDoubleInitialization() { doTest(false); }
  @Test public void testFinalFieldInitialization() { doTest(false); }
  @Test public void testFinalFieldInitializationInAnonymousClass() { doTest(false); }
  @Test public void testFinalFieldUsedInAnonymousArgumentListInsideInner() { doTest(false); }
  @Test public void testAssignToFinal() { doTest(false); }
  @Test public void testUnhandledExceptionsInSuperclass() { doTest(false); }
  @Test public void testNoUnhandledExceptionsMultipleInheritance() { doTest(false); }
  @Test public void testIgnoreSuperMethodsInMultipleOverridingCheck() { doTest(false); }
  @Test public void testFalseExceptionsMultipleInheritance() { doTest(true); }
  @Test public void testAssignmentCompatible () { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_5); doTest(false); }
  @Test public void testMustBeBoolean() { doTest(false); }

  @Test public void testNumericLiterals() { doTest(false); }
  @Test public void testInitializerCompletion() { doTest(false); }

  @Test public void testUndefinedLabel() { doTest(false); }
  @Test public void testDuplicateSwitchLabels() { doTest(false); }
  @Test public void testStringSwitchLabels() { doTest(false); }
  @Test public void testIllegalForwardReference() { doTest(false); }
  @Test public void testStaticOverride() { doTest(false); }
  @Test public void testCyclicInheritance() { doTest(false); }
  @Test public void testReferenceMemberBeforeCtrCalled() { doTest(false); }
  @Test public void testQualifiedThisBeforeCtrCalled() { doTest(false); }
  @Test public void testLabels() { doTest(false); }
  @Test public void testUnclosedBlockComment() { doTest(false); }
  @Test public void testUnclosedComment() { doTest(false); }
  @Test public void testBadUnicodeEscapeInComment() { doTest(false); }
  @Test public void testUnclosedDecl() { doTest(false); }
  @Test
  public void testSillyAssignment() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(true, true);
  }
  @Test public void testTernary() { doTest(false); }
  @Test public void testDuplicateClass() { doTest(false); }
  @Test public void testCatchType() { doTest(false); }
  @Test public void testMustBeThrowable() { doTest(false); }
  @Test public void testUnhandledMessingWithFinally() { doTest(false); }
  @Test public void testSerializableStuff() {  doTest(true); }
  @Test public void testDeprecated() { doTest(true); }
  @Test public void testJavadoc() { daemonFixture.get().getFixture().enableInspections(new JavadocDeclarationInspection()); doTest(true); }
  @Test
  public void testExpressionsInSwitch () {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(false));
  }
  @Test
  public void testAccessInner() {
    Editor e = daemonFixture.get().createSaveAndOpenFile("x/BeanContextServicesSupport.java",
                                     "package x;\n" +
                                     "public class BeanContextServicesSupport {" +
                                     "  protected class BCSSChild {" +
                                     "    class BCSSCServiceClassRef {" +
                                     "      void addRequestor(Object requestor) {" +
                                     "      }" +
                                     "    }" +
                                     "  }" +
                                     "}");
    Editor e2 = daemonFixture.get().createSaveAndOpenFile("x/Component.java",
                                      "package x;\n" +
                                      "public class Component {" +
                                      "  protected class FlipBufferStrategy {" +
                                      "    protected int numBuffers;" +
                                      "    protected void createBuffers(int numBuffers){}" +
                                      "  }" +
                                      "}");
    Project project = daemonFixture.get().getFixture().getProject();
    try {
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      });
      doTest(false);
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> {
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        VirtualFile file = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(e.getDocument()));
        FileEditorManager.getInstance(project).closeFile(file);
        VfsTestUtil.deleteFile(file);
        VirtualFile file2 = Objects.requireNonNull(FileDocumentManager.getInstance().getFile(e2.getDocument()));
        FileEditorManager.getInstance(project).closeFile(file2);
        VfsTestUtil.deleteFile(file2);
      });
    }
  }

  @Test public void testExceptionNeverThrown() { doTest(true); }
  @Test public void testExceptionNeverThrownInTry() { doTest(false); }

  @Test public void testSwitchStatement() { doTest(false); }
  @Test public void testAssertExpression() { doTest(false); }

  @Test public void testSynchronizedExpression() { doTest(false); }
  @Test public void testExtendMultipleClasses() { doTest(false); }

  @Test
  public void testRecursiveConstructorInvocation() {
    RecursionManager.disableMissedCacheAssertions(daemonFixture.get().getFixture().getTestRootDisposable()); // mutual recursion is prevented in contract inference
    doTest(false);
  }

  @Test public void testMethodCalls() { doTest(false); }
  @Test
  public void testSingleTypeImportConflicts() {
    daemonFixture.get().createSaveAndOpenFile("sql/Date.java", "package sql; public class Date{}");
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      PsiDocumentManager.getInstance(daemonFixture.get().getFixture().getProject()).commitAllDocuments();
    });
    doTest(false);
  }
  @Test
  public void testMultipleSingleTypeImports() { doTest(true); } //duplicate imports
  @Test public void testNotAllowedInInterface() { doTest(false); }
  @Test public void testQualifiedNew() { doTest(false); }
  @Test public void testEnclosingInstance() { doTest(false); }

  @Test
  public void testStaticViaInstance() { doTest(true); } // static via instance
  @Test
  public void testQualifiedThisSuper() { doTest(true); } //illegal qualified this or super

  @Test public void testAmbiguousMethodCall() { doTest(false); }

  @Test public void testImplicitConstructor() { doTest(false); }
  @Test public void testDotBeforeDecl() { doTest(false); }
  @Test public void testComputeConstant() { doTest(false); }

  @Test public void testAnonInAnon() { doTest(false); }
  @Test public void testAnonBaseRef() { doTest(false); }
  @Test public void testReturn() { doTest(false); }
  @Test public void testInterface() { doTest(false); }
  @Test public void testExtendsClause() { doTest(false); }
  @Test public void testMustBeFinal() { doTest(false); }

  @Test public void testXXX() { doTest(false); }
  @Test public void testUnused() { doTest(true, true); }
  @Test public void testQualifierBeforeClassName() { doTest(false); }
  @Test
  public void testQualifiedSuper() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest(false);
  }

  @Test public void testIgnoreImplicitThisReferenceBeforeSuperSinceJdk7() { doTest(false); }
  @Test
  public void testStatementsBeforeSuper() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_22_PREVIEW, () -> doTest(false));
  }

  @Test
  public void testFlexibleConstructorBodies() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_23_PREVIEW, () -> doTest(false));
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_25, () -> doTest(false));
  }

  @Test public void testCastFromVoid() { doTest(false); }
  @Test public void testCatchUnknownMethod() { doTest(false); }
  @Test public void testIDEADEV8822() { doTest(false); }
  @Test public void testIDEADEV9201() { doTest(false); }
  @Test public void testIDEADEV25784() { doTest(false); }
  @Test public void testIDEADEV13249() { doTest(false); }
  @Test public void testIDEADEV11919() { doTest(false); }
  @Test public void testIDEA67829() { doTest(false); }
  @Test public void testMethodCannotBeApplied() { doTest(false); }
  @Test public void testUnusedParamsOfPublicMethod() { doTest(true); }
  @Test public void testInnerClassesShadowing() { doTest(false); }

  @Test
  public void testUnusedParamsOfPublicMethodDisabled() {
    UnusedSymbolLocalInspection tool = myUnusedDeclarationInspection.getSharedLocalInspectionTool();
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

  @Test
  public void testUnusedInspectionNonPrivateMembers() {
    doTest(true);
  }

  @Test
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
        return psiElement instanceof PsiMethod m && m.getName().equals("myTestMethod");
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

    point.registerExtension(extension, daemonFixture.get().getFixture().getTestRootDisposable());
    try {
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase(true);
      doTest(true);
    }
    finally {
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase();
    }
  }

  @Test
  public void testUnusedInspectionNonPrivateMembersReferencedFromText() {
    doTest(true);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      PsiDirectory directory = daemonFixture.get().getFixture().getFile().getParent();
      assertNotNull(directory, daemonFixture.get().getFixture().getFile().toString());
      PsiFile txt = directory.createFile("x.txt");
      VirtualFile vFile = txt.getVirtualFile();
      assertNotNull(vFile, txt.toString());
      try {
        VfsUtil.saveText(vFile, "XXX");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    List<HighlightInfo> infos = daemonFixture.get().doHighlighting(HighlightSeverity.WARNING);
    assertTrue(infos.isEmpty(), infos.toString());
  }

  @Test
  public void testNamesHighlighting() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_5);
    daemonFixture.get().doTestFile(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java").checkSymbolNames().test();
  }

  @Test
  public void testAnnotationHighlighting() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_5);
    daemonFixture.get().doTestFile(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java").checkSymbolNames().test();
  }

  @Test
  public void testMultiFieldDeclNames() {
    daemonFixture.get().doTestFile(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java").checkSymbolNames().test();
  }

  @Test
  public void testSOEForTypeOfHugeBinaryExpression() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = "class A { String s = \"\"; }";
    daemonFixture.get().configureFromFileText("a.java", text);
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertTrue(errors.isEmpty(), errors.toString());
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments());

    String hugeExpr = "\"-\"" + "+\"b\"".repeat(10000);
    int pos = myFixture.getEditor().getDocument().getText().indexOf("\"\"");

    myFixture.runInCommand(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      myFixture.getEditor().getDocument().replaceString(pos, pos + 2, hugeExpr);
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    }));

    ReadAction.runBlocking(() -> {
      PsiField field = ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getFields()[0];
      PsiExpression expression = field.getInitializer();
      assert expression != null;
      PsiType type = expression.getType();
      assert type != null;
      assertEquals("PsiType:String", type.toString());
    });
  }

  @Test
  public void testSOEForCyclicInheritance() {
    @org.intellij.lang.annotations.Language("JAVA")
    String text = "class A extends B { String s = \"\"; void f() {}} class B extends A { void f() {} } ";
    daemonFixture.get().configureFromFileText("a.java", text);
    daemonFixture.get().doHighlighting();
  }

  @Test public void testClassicRethrow() { doTest(false); }
  @Test public void testRegexp() { doTest(false); }
  @Test public void testUnsupportedFeatures() { doTest(false); }
  @Test public void testThisBeforeSuper() { doTest(false); }
  @Test public void testExplicitConstructorInvocation() { doTest(false); }
  @Test public void testExtendFinalClass() { doTest(false); }
  @Test public void testPrivateMethodCalledFromSuper() { doTest(false); }
  @Test public void testQualifiedThisCalledFromSuper() { doTest(false); }
  @Test
  public void testReferenceToClassFromSuper() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_16, () -> doTest(false));
  }
  @Test public void testThisInInterface() { doTest(false); }
  @Test public void testInnerClassConstantReference() { doTest(false); }
  @Test
  public void testConstantReferencedViaInstance() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_16, () -> doTest(false));
  }
  @Test public void testIDEA60875() { doTest(false); }
  @Test public void testIDEA71645() { doTest(false); }
  @Test public void testIDEA18343() { doTest(false); }
  @Test public void testNewExpressionClass() { doTest(false); }
  @Test public void testInnerClassObjectLiteralFromSuperExpression() { doTest(false); }
  @Test public void testPrivateFieldInSuperClass() { doTest(false); }
  @Test public void testNoEnclosingInstanceWhenStaticNestedInheritsFromContainingClass() { doTest(false); }
  @Test public void testIDEA168768() { doTest(false); }
  @Test public void testStatementWithExpression() { doTest(false); }
  @Test public void testReturnFromConstructor() { doTest(false); }
  @Test public void testInheritFromFinalLocalClass() { doTest(false); }
  @Test
  public void testLocalClassInstantiation() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_24, () -> doTest(false));
  }

  @Test
  public void testStaticMethodCalls() {
    daemonFixture.get().doTestFile(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java").checkSymbolNames().test();
  }

  @Test
  public void testNestedLocalClasses() {
    doTest(false);
  }

  @Test
  public void testAmbiguousConstants() {
    doTest(false);
  }

  @Test public void testUnreachableArrayElementAssignment() { doTest(false); }
  
  @Test
  public void testNotWellFormedExpressionStatementWithoutSemicolon() {
    doTest(false);
  }

  @Test public void testTooManyArrayDimensions() { doTest(false);}

  @Test
  public void testInsane() {
    daemonFixture.get().configureFromFileText("x.java", "class X { \nx_x_x_x\n }");
    List<HighlightInfo> infos = daemonFixture.get().highlightErrors();
    assertFalse(infos.isEmpty());
  }

  @Test public void testIllegalWhitespaces() { doTest(false); }

  @Test
  public void testMarkUsedDefaultAnnotationMethodUnusedInspection() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest(true);
  }

  @Test
  public void testNavigateByReassignVariables() {
    doTest(true, true);
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    boolean old = settings.isNextErrorActionGoesToErrorsFirst();
    settings.setNextErrorActionGoesToErrorsFirst(true);
    try {
      LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
      EdtTestUtil.runInEdtAndWait(() -> {
        int offset = myFixture.getEditor().getCaretModel().getOffset();
        new GotoNextErrorHandler(true).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

        assertEquals(offset, myFixture.getEditor().getCaretModel().getOffset());
      });
    }
    finally {
      settings.setNextErrorActionGoesToErrorsFirst(old);
    }
  }

  @Test
  public void testUninitializedVarComplexTernary() {
    doTest(false);
  }

  @Test
  public void testArrayInitBeforeSuper() {
    doTest(false);
  }

  @Test
  public void testThisReferencedInnerClass() {
    doTest(false);
  }

  @Test
  public void testReferenceToImplicitClass() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_22_PREVIEW, () -> doTest(false));
  }
  
  @Test public void testUninitializedFields() { doTest(false); }
  
  @Test
  public void testUninitializedFieldsBeforeSuper() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_25, () -> doTest(false));
  }
}
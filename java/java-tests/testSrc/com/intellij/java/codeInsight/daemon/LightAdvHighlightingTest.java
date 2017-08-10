/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon;

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.util.List;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingTest extends LightDaemonAnalyzerTestCase {
  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting";

  private UnusedDeclarationInspectionBase myUnusedDeclarationInspection;

  private void doTest(boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUnusedDeclarationInspection = new UnusedDeclarationInspection(isUnusedInspectionRequired());
    enableInspectionTool(myUnusedDeclarationInspection);
    setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  private boolean isUnusedInspectionRequired() {
    return getTestName(false).contains("UnusedInspection");
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new SillyAssignmentInspection(),
      new AccessStaticViaInstance(),
      new DeprecationInspection(),
      new RedundantThrowsDeclarationLocalInspection(),
      new UnusedImportLocalInspection(),
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
  public void testInvalidExpressions() { doTest(false); }
  public void testIllegalVoidType() { doTest(false); }
  public void testIllegalType() { doTest(false); }
  public void testOperatorApplicability() { doTest(false); }
  public void testIncompatibleTypes() { doTest(false); }
  public void testCtrCallIsFirst() { doTest(false); }
  public void testAccessLevelClash() { doTest(false); }
  public void testCasts() { doTest(false); }
  public void testOverrideConflicts() { doTest(false); }
  public void testOverriddenMethodIsFinal() { doTest(false); }
  public void testMissingReturn() { doTest(false); }
  public void testUnreachable() { doTest(false); }
  public void testFinalFieldInit() { doTest(false); }
  public void testLocalVariableInitialization() { doTest(false); }
  public void testVarDoubleInitialization() { doTest(false); }
  public void testFieldDoubleInitialization() { doTest(false); }
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
  public void testUnclosedDecl() { doTest(false); }
  public void testSillyAssignment() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(true);
  }
  public void testTernary() { doTest(false); }
  public void testDuplicateClass() { doTest(false); }
  public void testCatchType() { doTest(false); }
  public void testMustBeThrowable() { doTest(false); }
  public void testUnhandledMessingWithFinally() { doTest(false); }
  public void testSerializableStuff() {  doTest(true); }
  public void testDeprecated() { doTest(true); }
  public void testJavadoc() { enableInspectionTool(new JavaDocLocalInspection()); doTest(true); }
  public void testExpressionsInSwitch () { doTest(false); }
  public void testAccessInner() throws IOException {
    Editor e = createSaveAndOpenFile("x/BeanContextServicesSupport.java",
                                     "" +
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
                                      "" +
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
      VirtualFile file = FileDocumentManager.getInstance().getFile(e.getDocument());
      FileEditorManager.getInstance(getProject()).closeFile(file);
      VfsTestUtil.deleteFile(file);
      VirtualFile file2 = FileDocumentManager.getInstance().getFile(e2.getDocument());
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
  public void testRecursiveConstructorInvocation() { doTest(false); }
  public void testMethodCalls() { doTest(false); }
  public void testSingleTypeImportConflicts() throws IOException {
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
  public void testUnused() { doTest(true); }
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
  public void testDefaultPackageClassInStaticImport() { doTest(false); }

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
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.DEAD_CODE_TOOL);
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

    point.registerExtension(extension);

    try {
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase(true);
      doTest(true);
    }
    finally {
      point.unregisterExtension(extension);
      myUnusedDeclarationInspection = new UnusedDeclarationInspectionBase();
    }
  }

  public void testUnusedInspectionNonPrivateMembersReferencedFromText() {
    doTest(true);
    WriteCommandAction.runWriteCommandAction(null, () -> {
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

  public void testInjectedAnnotator() {
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

  public void testSOEForTypeOfHugeBinaryExpression() {
    configureFromFileText("a.java", "class A { String s = \"\"; }");
    assertEmpty(highlightErrors());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final StringBuilder sb = new StringBuilder("\"-\"");
    for (int i = 0; i < 10000; i++) sb.append("+\"b\"");
    final String hugeExpr = sb.toString();
    final int pos = getEditor().getDocument().getText().indexOf("\"\"");

    ApplicationManager.getApplication().runWriteAction(() -> {
      getEditor().getDocument().replaceString(pos, pos + 2, hugeExpr);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    });

    final PsiField field = ((PsiJavaFile)getFile()).getClasses()[0].getFields()[0];
    final PsiExpression expression = field.getInitializer();
    assert expression != null;
    final PsiType type = expression.getType();
    assert type != null;
    assertEquals("PsiType:String", type.toString());
  }

  public void testSOEForCyclicInheritance() {
    configureFromFileText("a.java", "class A extends B { String s = \"\"; void f() {}} class B extends A { void f() {} } ");
    doHighlighting();
  }

  public void testClassicRethrow() { doTest(false); }
  public void testRegexp() { doTest(false); }
  public void testUnsupportedFeatures() { doTest(false); }
  public void testThisBeforeSuper() { doTest(false); }
  public void testExplicitConstructorInvocation() { doTest(false); }
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

  public void testInsane() {
    configureFromFileText("x.java", "class X { \nx_x_x_x\n }");
    List<HighlightInfo> infos = highlightErrors();
    assertTrue(!infos.isEmpty());
  }

  public void testAnnotatorWorksWithFileLevel() {
    Annotator annotator = new MyTopFileAnnotator();
    Language java = StdFileTypes.JAVA.getLanguage();
    LanguageAnnotators.INSTANCE.addExplicitExtension(java, annotator);
    try {
      List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(java);
      assertTrue(list.toString(), list.contains(annotator));
      configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
      ((EditorEx)getEditor()).getScrollPane().getViewport().setSize(new Dimension(1000,1000)); // whole file fit onscreen
      doHighlighting();
      List<HighlightInfo> fileLevel =
        ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(ourProject)).getFileLevelHighlights(getProject(), getFile());
      HighlightInfo info = assertOneElement(fileLevel);
      assertEquals("top level", info.getDescription());

      type("\n\n");
      doHighlighting();
      fileLevel =
        ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(ourProject)).getFileLevelHighlights(getProject(), getFile());
      info = assertOneElement(fileLevel);
      assertEquals("top level", info.getDescription());

      type("//xxx"); //disable top level annotation
      List<HighlightInfo> warnings = doHighlighting(HighlightSeverity.WARNING);
      assertEmpty(warnings);
      fileLevel = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(ourProject)).getFileLevelHighlights(getProject(), getFile());
      assertEmpty(fileLevel);
    }
    finally {
      LanguageAnnotators.INSTANCE.removeExplicitExtension(java, annotator);
    }

    List<Annotator> list = LanguageAnnotators.INSTANCE.allForLanguage(java);
    assertFalse(list.toString(), list.contains(annotator));
  }

  public void testIllegalWhitespaces() { doTest(false); }

  // must stay public for PicoContainer to work
  public static class MyAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      psiElement.accept(new XmlElementVisitor() {
        @Override public void visitXmlTag(XmlTag tag) {
          XmlAttribute attribute = tag.getAttribute("aaa", "");
          if (attribute != null) {
            holder.createWarningAnnotation(attribute, "MyAnnotator");
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

  // must stay public for PicoContainer to work
  public static class MyTopFileAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
      if (psiElement instanceof PsiFile && !psiElement.getText().contains("xxx")) {
        Annotation annotation = holder.createWarningAnnotation(psiElement, "top level");
        annotation.setFileLevelAnnotation(true);
      }
    }
  }
}
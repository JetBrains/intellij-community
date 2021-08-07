// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SourceToSinkPropertyInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  private static final @Language("JAVA") String A_CLASS = "package com.jetbrains;\n" +
                                                          "import com.jetbrains.OtherClass;\n" +
                                                          "import org.checkerframework.checker.tainting.qual.*;\n" +
                                                          "class A {\n" +
                                                          "    void callSink() {}\n" +
                                                          "    void sink(@Untainted String s) {}\n" +
                                                          "    static @Tainted String source() { return \"unsafe\"; }\n" +
                                                          "    static String foo() { return \"bar\"; }\n" +
                                                          "    static @Untainted String safe() { return \"safe\"; }\n" +
                                                          "}";


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SourceToSinkFlowInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16;
  }

  public void testSinkToSourceFlowIsReported() {
    myFixture.addClass("package org.checkerframework.checker.tainting.qual;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "@Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})\n" +
                       "public @interface Tainted {\n" +
                       "}");
    myFixture.addClass("package org.checkerframework.checker.tainting.qual;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "@Target({TYPE_USE,TYPE_PARAMETER})\n" +
                       "public @interface Untainted {\n" +
                       "}");

    myFixture.addClass(A_CLASS);
    myFixture.addClass("package com.jetbrains;\n" +
                       "import org.checkerframework.checker.tainting.qual.*;\n" +
                       "class OtherClass {\n" +
                       "    static @Tainted String source() { return \"unsafe\"; }\n" +
                       "    static String foo() { return \"bar\"; }\n" +
                       "    static @Untainted String safe() { return \"safe\"; }\n" +
                       "}");
    PropertyChecker.customized()
      .checkScenarios(() -> this::doTestSinkToSourceFlowIsReported);
  }

  private void doTestSinkToSourceFlowIsReported(ImperativeCommand.Environment env) {
    JavaPsiFacadeEx facade = myFixture.getJavaFacade();
    PsiClass psiClass = Objects.requireNonNull(facade.findClass("com.jetbrains.A", GlobalSearchScope.allScope(getProject())));
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());
    Variable variable = Variable.generate(env);
    PsiClass aClass = WriteCommandAction.runWriteCommandAction(getProject(),
                                                               (Computable<PsiClass>)() -> recreateClass(facade.getElementFactory(), 
                                                                                                         psiClass));
    JavaContext javaContext = JavaContext.create(aClass, facade);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> variable.add(javaContext));
    env.logMessage("A class:\n" + aClass.getText());
    TaintState taintState = variable.taintState();
    List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.WARNING);
    if (taintState == TaintState.SAFE) {
      assertEmpty(infos);
    }
    else {
      assertSize(1, infos);
      PsiElement sinkArgument = javaContext.getSinkCallArgument();
      HighlightInfo info = infos.get(0);
      assertEquals(sinkArgument.getTextRange().getStartOffset(), info.getActualStartOffset());
      String description = JvmAnalysisBundle.message(taintState == TaintState.TAINTED ? "jvm.inspections.source.unsafe.to.sink.flow.description"
                                                                                      : "jvm.inspections.source.unknown.to.sink.flow.description");
      assertEquals(description, info.getDescription());
    }
  }

  private static @NotNull PsiClass recreateClass(@NotNull PsiElementFactory factory, @NotNull PsiClass psiClass) {
    PsiClass recreated = factory.createClassFromText(A_CLASS, null);
    return (PsiClass)psiClass.replace(recreated.getInnerClasses()[0]);
  }

  private interface Statement {

    void add(JavaContext javaContext);

    TaintState taintState();
  }

  private interface Expression {
    String getText();

    TaintState taintState();
  }

  private static class JavaContext {

    private final PsiMethod myMethod;
    private final PsiElementFactory myFactory;
    private PsiElement mySinkCallArgument;

    private JavaContext(PsiMethod method, PsiElementFactory factory) {
      myMethod = method;
      myFactory = factory;
    }

    public PsiStatement addStatement(@NotNull String statementText) {
      PsiStatement statement = myFactory.createStatementFromText(statementText, null);
      return (PsiStatement)myMethod.getBody().add(statement);
    }

    public void declareField(@NotNull String declarationText) {
      declarationText = "private String s = " + declarationText;
      PsiField field = myFactory.createFieldFromText(declarationText, null);
      PsiClass containingClass = myMethod.getContainingClass();
      PsiElement lBrace = containingClass.getLBrace();
      containingClass.addAfter(field, lBrace);
    }

    public void declareLocalVariable(@NotNull String declarationText) {
      declarationText = "String s = " + declarationText;
      addStatement(declarationText);
    }

    public void callSink() {
      PsiStatement statement = addStatement("sink(s);");
      PsiMethodCallExpression call = PsiTreeUtil.findChildOfType(statement, PsiMethodCallExpression.class);
      mySinkCallArgument = call.getArgumentList().getExpressions()[0];
    }

    public PsiElement getSinkCallArgument() {
      return mySinkCallArgument;
    }

    private static @NotNull JavaContext create(PsiClass psiClass, JavaPsiFacade facade) {
      PsiMethod callSinkMethod = psiClass.getMethods()[0];
      return new JavaContext(callSinkMethod, facade.getElementFactory());
    }
  }

  private static class CallExpression implements Expression {

    private final boolean myIsExternal;
    private final TaintState myCallType;

    private CallExpression(boolean isExternal, @NotNull SourceToSinkPropertyInspectionTest.TaintState callType) {
      myIsExternal = isExternal;
      myCallType = callType;
    }

    @Override
    public String getText() {
      return (myIsExternal ? "OtherClass." : "") + myCallType.methodName() + "()";
    }

    @Override
    public TaintState taintState() {
      return myCallType;
    }

    private static CallExpression generate(ImperativeCommand.Environment env) {
      boolean isExternal = env.generateValue(Generator.booleans(), null);
      TaintState callType = env.generateValue(Generator.sampledFrom(TaintState.values()), null);
      return new CallExpression(isExternal, callType);
    }
  }

  private enum TaintState {
    SAFE("safe"),
    UNKNOWN("foo"),
    TAINTED("source");

    private final String myMethodName;

    TaintState(String methodName) {
      myMethodName = methodName;
    }

    public String methodName() {
      return myMethodName;
    }
  }

  private static class LiteralExpression implements Expression {

    private final boolean myNullInitializer;

    private LiteralExpression(boolean nullInitializer) {
      myNullInitializer = nullInitializer;
    }

    @Override
    public String getText() {
      return myNullInitializer ? "null" : "\"safe\"";
    }

    @Override
    public TaintState taintState() {
      return TaintState.SAFE;
    }

    private static LiteralExpression generate(ImperativeCommand.Environment env) {
      boolean nullInitializer = env.generateValue(Generator.booleans(), null);
      return new LiteralExpression(nullInitializer);
    }
  }

  private static class Variable implements Statement {
    private final Declaration myDeclaration;
    private final List<Assignment> myAssignments;

    private Variable(Declaration declaration, List<Assignment> assignments) {
      myDeclaration = declaration;
      myAssignments = assignments;
    }

    @Override
    public void add(JavaContext javaContext) {
      myDeclaration.add(javaContext);
      myAssignments.forEach(a -> a.add(javaContext));
      javaContext.callSink();
    }

    @Override
    public TaintState taintState() {
      if (myDeclaration.myVarType == Declaration.VarType.FIELD) return TaintState.UNKNOWN;
      TaintState taintState = myDeclaration.taintState();
      if (taintState == TaintState.TAINTED) return taintState;
      for (Assignment assignment : myAssignments) {
        TaintState assignmentState = assignment.taintState();
        if (assignmentState == TaintState.TAINTED) return assignmentState;
        if (taintState == TaintState.SAFE) taintState = assignmentState;
      }
      return taintState;
    }


    private static Variable generate(ImperativeCommand.Environment env) {
      Declaration declaration = Declaration.generate(env);
      int nAssignments = env.generateValue(Generator.integers(0, 3), null);
      List<Assignment> assignments = Stream.generate(() -> Assignment.generate(env)).limit(nAssignments).collect(Collectors.toList());
      return new Variable(declaration, assignments);
    }
  }

  private static class Declaration implements Statement {

    private final VarType myVarType;
    private final Expression myExpression;

    private Declaration(@NotNull VarType type, @NotNull Expression expression) {
      myVarType = type;
      myExpression = expression;
    }

    @Override
    public void add(JavaContext javaContext) {
      if (myVarType == VarType.LOCAL_VAR) {
        javaContext.declareLocalVariable(myExpression.getText() + ";");
      }
      else {
        javaContext.declareField(myExpression.getText() + ";");
      }
    }

    @Override
    public TaintState taintState() {
      return myExpression.taintState();
    }

    public static Declaration generate(ImperativeCommand.Environment env) {
      VarType varType = env.generateValue(Generator.sampledFrom(VarType.values()), null);
      boolean isCallExpression = env.generateValue(Generator.booleans(), null);
      Expression expression = isCallExpression ? CallExpression.generate(env) : LiteralExpression.generate(env);
      return new Declaration(varType, expression);
    }

    private enum VarType {
      LOCAL_VAR,
      FIELD
    }
  }

  private static class Assignment implements Statement {

    private final Expression myExpression;

    private Assignment(Expression expression) {
      myExpression = expression;
    }

    @Override
    public void add(JavaContext javaContext) {
      javaContext.addStatement("s =" + myExpression.getText() + ";");
    }

    @Override
    public TaintState taintState() {
      return myExpression.taintState();
    }

    public static Assignment generate(ImperativeCommand.Environment env) {
      boolean isCallExpression = env.generateValue(Generator.booleans(), null);
      Expression expression = isCallExpression ? CallExpression.generate(env) : LiteralExpression.generate(env);
      return new Assignment(expression);
    }
  }
}

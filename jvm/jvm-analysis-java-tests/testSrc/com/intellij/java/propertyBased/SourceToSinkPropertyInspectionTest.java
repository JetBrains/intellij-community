// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SourceToSinkPropertyInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  private static final @Language("JAVA") String A_CLASS = """
    package com.jetbrains;
    import com.jetbrains.OtherClass;
    import org.checkerframework.checker.tainting.qual.*;
    class A {
        void callSink() {}
        void sink(@Untainted String s) {}
        static @Tainted String source() { return "unsafe"; }
        static String foo() { return "bar"; }
        static @Untainted String safe() { return "safe"; }
    }""";


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SourceToSinkFlowInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testSinkToSourceFlowIsReported() {
    myFixture.addClass("""
                         package org.checkerframework.checker.tainting.qual;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         @Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD, ElementType.METHOD})
                         public @interface Tainted {
                         }""");
    myFixture.addClass("""
                         package org.checkerframework.checker.tainting.qual;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         @Target({TYPE_USE,TYPE_PARAMETER})
                         public @interface Untainted {
                         }""");

    myFixture.addClass(A_CLASS);
    myFixture.addClass("""
                         package com.jetbrains;
                         import org.checkerframework.checker.tainting.qual.*;
                         class OtherClass {
                             static @Tainted String source() { return "unsafe"; }
                             static String foo() { return "bar"; }
                             static @Untainted String safe() { return "safe"; }
                         }""");
    PropertyChecker.customized()
      .checkScenarios(() -> this::doTestSinkToSourceFlowIsReported);
  }

  private void doTestSinkToSourceFlowIsReported(ImperativeCommand.Environment env) {
    JavaPsiFacadeEx facade = myFixture.getJavaFacade();
    Project project = getProject();
    PsiClass psiClass = Objects.requireNonNull(facade.findClass("com.jetbrains.A", GlobalSearchScope.allScope(project)));
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());
    MethodBody methodBody = MethodBody.generate(env);
    PsiClass aClass = WriteCommandAction.runWriteCommandAction(project, 
                                                               (Computable<PsiClass>)() -> recreateClass(facade.getElementFactory(), psiClass));
    JavaContext javaContext = JavaContext.create(aClass, facade);
    WriteCommandAction.runWriteCommandAction(project, () -> methodBody.add(javaContext));
    env.logMessage("A class:\n" + aClass.getText());
    TaintState taintState = methodBody.taintState();
    List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.WARNING);
    if (taintState == TaintState.SAFE) {
      assertEmpty(infos);
    }
    else {
      assertSize(1, infos);
      PsiElement sinkArgument = javaContext.getSinkCallArgument();
      HighlightInfo info = infos.get(0);
      assertEquals(sinkArgument.getTextRange().getStartOffset(), info.getActualStartOffset());
      String description = JvmAnalysisBundle.message(taintState == TaintState.TAINTED ?
                                                     "jvm.inspections.source.to.sink.flow.passed.unsafe" :
                                                     "jvm.inspections.source.to.sink.flow.passed.unknown");
      assertEquals(description, info.getDescription());
    }
  }

  private static @NotNull PsiClass recreateClass(@NotNull PsiElementFactory factory, @NotNull PsiClass psiClass) {
    PsiClass recreated = factory.createClassFromText(A_CLASS, null);
    return (PsiClass)psiClass.replace(recreated.getInnerClasses()[0]);
  }

  private enum TaintState {
    SAFE("safe") {
      @Override
      @NotNull
      TaintState join(@NotNull TaintState other) {
        return other;
      }
    },
    UNKNOWN("foo") {
      @Override
      @NotNull
      TaintState join(@NotNull TaintState other) {
        return other == SAFE ? this : other;
      }
    },
    TAINTED("source") {
      @Override
      @NotNull
      TaintState join(@NotNull TaintState other) {
        return this;
      }
    };

    private final String myMethodName;

    TaintState(@NotNull String methodName) {
      myMethodName = methodName;
    }

    abstract @NotNull TaintState join(@NotNull TaintState other);

    public @NotNull String methodName() {
      return myMethodName;
    }
  }

  private interface Statement {

    void add(@NotNull JavaContext javaContext);

    @NotNull TaintState taintState();
  }

  private interface Expression {
    List<Function<ImperativeCommand.Environment, Expression>> TYPES = List.of(LiteralExpression::generate,
                                                                              CallExpression::generate,
                                                                              TernaryExpression::generate,
                                                                              ParenthesizedExpression::generate);
    
    @NotNull String getText();

    @NotNull TaintState taintState();

    static @NotNull Expression generate(@NotNull ImperativeCommand.Environment env) {
      Function<ImperativeCommand.Environment, Expression> ctor = env.generateValue(Generator.sampledFrom(Expression.TYPES), null);
      return ctor.apply(env);
    }
  }

  private static class JavaContext {

    private final PsiMethod myMethod;
    private final PsiElementFactory myFactory;
    private PsiElement mySinkCallArgument;

    private JavaContext(@NotNull PsiMethod method, @NotNull PsiElementFactory factory) {
      myMethod = method;
      myFactory = factory;
    }

    public PsiStatement addStatement(@NotNull String statementText) {
      PsiStatement statement = myFactory.createStatementFromText(statementText, null);
      return (PsiStatement)myMethod.getBody().add(statement);
    }

    public void declareField(@NotNull String varName, @NotNull String declarationText) {
      declarationText = String.format("private String %s = %s", varName, declarationText);
      PsiField field = myFactory.createFieldFromText(declarationText, null);
      PsiClass containingClass = myMethod.getContainingClass();
      PsiElement lBrace = containingClass.getLBrace();
      containingClass.addAfter(field, lBrace);
    }

    public void declareLocalVariable(@NotNull String varName, @NotNull String declarationText) {
      declarationText = String.format("String %s = %s", varName, declarationText);
      addStatement(declarationText);
    }

    public void callSink(@NotNull String varName) {
      PsiStatement statement = addStatement(String.format("sink(%s);", varName));
      PsiMethodCallExpression call = PsiTreeUtil.findChildOfType(statement, PsiMethodCallExpression.class);
      mySinkCallArgument = call.getArgumentList().getExpressions()[0];
    }

    public PsiElement getSinkCallArgument() {
      return mySinkCallArgument;
    }

    private static @NotNull JavaContext create(@NotNull PsiClass psiClass, @NotNull JavaPsiFacade facade) {
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
    public @NotNull String getText() {
      return (myIsExternal ? "OtherClass." : "") + myCallType.methodName() + "()";
    }

    @Override
    public @NotNull TaintState taintState() {
      return myCallType;
    }

    private static @NotNull CallExpression generate(@NotNull ImperativeCommand.Environment env) {
      boolean isExternal = env.generateValue(Generator.booleans(), null);
      TaintState callType = env.generateValue(Generator.sampledFrom(TaintState.values()), null);
      return new CallExpression(isExternal, callType);
    }
  }

  private static class TernaryExpression implements Expression {

    private final Expression myLhs;
    private final Expression myRhs;

    private TernaryExpression(@NotNull Expression lhs, @NotNull Expression rhs) {
      myLhs = lhs;
      myRhs = rhs;
    }

    @Override
    public @NotNull String getText() {
      return String.format("((1 == 1) ? %s : %s)", myLhs.getText(), myRhs.getText());
    }

    @Override
    public @NotNull TaintState taintState() {
      return myLhs.taintState().join(myRhs.taintState());
    }

    private static @NotNull TernaryExpression generate(@NotNull ImperativeCommand.Environment env) {
      Expression lhs = Expression.generate(env);
      Expression rhs = Expression.generate(env);
      return new TernaryExpression(lhs, rhs);
    }
  }

  private static class ParenthesizedExpression implements Expression {

    private final Expression myExpression;

    private ParenthesizedExpression(@NotNull Expression expression) {
      myExpression = expression;
    }

    @Override
    public @NotNull String getText() {
      return String.format("(%s)", myExpression.getText());
    }

    @Override
    public @NotNull TaintState taintState() {
      return myExpression.taintState();
    }

    private static @NotNull ParenthesizedExpression generate(@NotNull ImperativeCommand.Environment env) {
      return new ParenthesizedExpression(Expression.generate(env));
    }
  }

  private static class LiteralExpression implements Expression {

    private final boolean myNullInitializer;

    private LiteralExpression(boolean nullInitializer) {
      myNullInitializer = nullInitializer;
    }

    @Override
    public @NotNull String getText() {
      return myNullInitializer ? "null" : "\"safe\"";
    }

    @Override
    public @NotNull TaintState taintState() {
      return TaintState.SAFE;
    }

    private static @NotNull LiteralExpression generate(@NotNull ImperativeCommand.Environment env) {
      boolean nullInitializer = env.generateValue(Generator.booleans(), null);
      return new LiteralExpression(nullInitializer);
    }
  }

  private static class MethodBody implements Statement {

    private final List<Variable> myVariables;

    private MethodBody(@NotNull List<Variable> variables) {
      this.myVariables = variables;
    }

    @Override
    public void add(@NotNull JavaContext javaContext) {
      for (int i = 0; i < myVariables.size(); i++) {
        Variable variable = myVariables.get(i);
        variable.add(javaContext);
        if (i > 0) {
          javaContext.addStatement(String.format("s%d = s%d;", i, i - 1));
        }
      }
      javaContext.callSink("s" + (myVariables.size() - 1));
    }

    @Override
    public @NotNull TaintState taintState() {
      TaintState taintState = TaintState.SAFE;
      for (Variable variable : myVariables) {
        if (variable.isField()) {
          taintState = TaintState.UNKNOWN;
        }
        else {
          taintState = taintState.join(variable.taintState());
        }
      }
      return taintState;
    }

    private static @NotNull MethodBody generate(@NotNull ImperativeCommand.Environment env) {
      int nVars = env.generateValue(Generator.integers(1, 3), null);
      List<Variable> vars = IntStream.range(0, nVars).mapToObj(i -> Variable.generate("s" + i, env)).collect(Collectors.toList());
      return new MethodBody(vars);
    }
  }

  private static class Variable implements Statement {
    private final Declaration myDeclaration;
    private final List<Assignment> myAssignments;

    private Variable(@NotNull Declaration declaration, @NotNull List<Assignment> assignments) {
      myDeclaration = declaration;
      myAssignments = assignments;
    }

    @Override
    public void add(@NotNull JavaContext javaContext) {
      myDeclaration.add(javaContext);
      myAssignments.forEach(a -> a.add(javaContext));
    }

    @Override
    public @NotNull TaintState taintState() {
      if (isField()) return TaintState.UNKNOWN;
      TaintState taintState = myDeclaration.taintState();
      if (taintState == TaintState.TAINTED) return taintState;
      for (Assignment assignment : myAssignments) {
        TaintState assignmentState = assignment.taintState();
        if (assignmentState == TaintState.TAINTED) return assignmentState;
        if (taintState == TaintState.SAFE) taintState = assignmentState;
      }
      return taintState;
    }

    public boolean isField() {
      return myDeclaration.myVarType == Declaration.VarType.FIELD;
    }

    private static @NotNull Variable generate(@NotNull String name, @NotNull ImperativeCommand.Environment env) {
      Declaration declaration = Declaration.generate(name, env);
      int nAssignments = env.generateValue(Generator.integers(0, 3), null);
      List<Assignment> assignments = Stream.generate(() -> Assignment.generate(name, env)).limit(nAssignments).collect(Collectors.toList());
      return new Variable(declaration, assignments);
    }
  }

  private static class Declaration implements Statement {

    private final String myVarName;
    private final VarType myVarType;
    private final Expression myExpression;

    private Declaration(@NotNull String varName, @NotNull VarType type, @NotNull Expression expression) {
      myVarName = varName;
      myVarType = type;
      myExpression = expression;
    }

    @Override
    public void add(@NotNull JavaContext javaContext) {
      if (myVarType == VarType.LOCAL_VAR) {
        javaContext.declareLocalVariable(myVarName, myExpression.getText() + ";");
      }
      else {
        javaContext.declareField(myVarName, myExpression.getText() + ";");
      }
    }

    @Override
    public @NotNull TaintState taintState() {
      return myExpression.taintState();
    }

    public static @NotNull Declaration generate(@NotNull String varName, @NotNull ImperativeCommand.Environment env) {
      VarType varType = env.generateValue(Generator.sampledFrom(VarType.values()), null);
      boolean isCallExpression = env.generateValue(Generator.booleans(), null);
      Expression expression = isCallExpression ? CallExpression.generate(env) : LiteralExpression.generate(env);
      return new Declaration(varName, varType, expression);
    }

    private enum VarType {
      LOCAL_VAR,
      FIELD
    }
  }

  private static class Assignment implements Statement {

    private final String myVarName;
    private final Expression myExpression;

    private Assignment(@NotNull String varName, @NotNull Expression expression) {
      myVarName = varName;
      myExpression = expression;
    }

    @Override
    public void add(@NotNull JavaContext javaContext) {
      javaContext.addStatement(String.format("%s = %s;", myVarName, myExpression.getText()));
    }

    @Override
    public @NotNull TaintState taintState() {
      return myExpression.taintState();
    }

    public static @NotNull Assignment generate(@NotNull String varName, @NotNull ImperativeCommand.Environment env) {
      return new Assignment(varName, Expression.generate(env));
    }
  }
}

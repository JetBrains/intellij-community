// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.optionalToIf.OptionalToIfInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.RehighlightAllEditors;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jetbrains.jetCheck.ImperativeCommand.Environment;

@SkipSlowTestLocally
public class OptionalToIfConversionPropertyTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new OptionalToIfInspection());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  public void testCompilabilityAfterConversion() {
    myFixture.addClass("package com.jetbrains;\n" +
                       "import java.util.Optional;\n" +
                       "import java.util.stream.Stream;\n" +
                       "\n" +
                       "class A {\n" +
                       "  void foo() {\n" +
                       "  }\n" +
                       "}");
    PropertyChecker.customized()
      .checkScenarios(() -> this::doTestCompilabilityAfterConversion);
  }

  private void doTestCompilabilityAfterConversion(@NotNull Environment env) {
    PsiClass psiClass = JavaPsiFacade.getInstance(getProject()).findClass("com.jetbrains.A", GlobalSearchScope.allScope(getProject()));
    assertNotNull(psiClass);
    myFixture.openFileInEditor(psiClass.getContainingFile().getVirtualFile());
    Context context = new Context(env);
    String methodText = context.generateOptionalCall(true);
    String afterStepText = context.afterStep;
    PsiMethod newMethod = replaceMethod(psiClass, methodText);
    PsiStatement lastStatement = addLastStatement(afterStepText, newMethod);
    env.logMessage("Code before conversion:\n" + psiClass.getText());

    Editor editor = myFixture.getEditor();
    applyConversion(newMethod, editor);
    env.logMessage("Code after conversion:\n" + psiClass.getText());

    assertFalse(hasErrors(newMethod, editor, lastStatement));
  }

  private @Nullable PsiStatement addLastStatement(@Nullable String statementText, @NotNull PsiMethod method) {
    if (statementText == null) return null;
    PsiCodeBlock methodBody = method.getBody();
    PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(methodBody);
    assertNotNull(lastStatement);
    PsiStatement newStatement = PsiElementFactory.getInstance(getProject()).createStatementFromText(statementText, methodBody);
    Computable<PsiStatement> addStatementAction = () -> (PsiStatement)methodBody.addAfter(newStatement, lastStatement);
    return WriteCommandAction.runWriteCommandAction(getProject(), addStatementAction);
  }

  private PsiMethod replaceMethod(@NotNull PsiClass psiClass, @NotNull String methodText) {
    PsiMethod[] methods = psiClass.getMethods();
    assertSize(1, methods);
    PsiMethod psiMethod = methods[0];
    Project project = getProject();
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    Computable<PsiMethod> replaceMethodAction = () -> (PsiMethod)psiMethod.replace(factory.createMethodFromText(methodText, psiMethod));
    return WriteCommandAction.runWriteCommandAction(project, replaceMethodAction);
  }

  private void applyConversion(@NotNull PsiMethod psiMethod, @NotNull Editor editor) {
    PsiExpression expression = PsiTreeUtil.findChildOfType(psiMethod, PsiExpression.class);
    assertNotNull(expression);
    int offset = expression.getTextRange().getEndOffset();
    editor.getCaretModel().moveToOffset(offset);
    String hint = "Replace Optional chain with if statements";
    List<IntentionAction> actions = myFixture.filterAvailableIntentions(hint);
    assertSize(1, actions);
    myFixture.launchAction(actions.get(0));
  }

  private boolean hasErrors(PsiMethod psiMethod, @NotNull Editor editor, @Nullable PsiStatement afterStatement) {
    if (afterStatement != null) {
      PsiStatement[] statements = psiMethod.getBody().getStatements();
      int nStatements = statements.length;
      assertTrue(nStatements >= 1 && statements[nStatements - 1] == afterStatement);
      if (nStatements >= 2) {
        PsiStatement lastConverted = statements[nStatements - 2];
        if (lastConverted instanceof PsiThrowStatement) return false;
      }
    }
    List<HighlightInfo> infos = RehighlightAllEditors.highlightEditor(editor, getProject());
    return infos.stream().anyMatch(i -> i.getSeverity() == HighlightSeverity.ERROR);
  }

  private interface Operation {

    @NotNull String generate(@NotNull Context context);

    @NotNull Generator<Operation> possibleNextOperations();

    default boolean isNested() {
      return false;
    }

    abstract class SourceOperation implements Operation {

      @Override
      public @NotNull Generator<Operation> possibleNextOperations() {
        return IntermediateOperation.intermediateOps();
      }

      private static @NotNull Generator<Operation> sourceOps() {
        return Generator.sampledFrom(new Empty(), new Of());
      }

      private static class Empty extends SourceOperation {
        @Override
        public @NotNull String generate(@NotNull Context context) {
          return "<String>empty()";
        }
      }

      private static class Of extends SourceOperation {

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String sourceVar = context.getTargetExpression(null);
          boolean isNullable = context.myEnv.generateValue(Generator.booleans(), null);
          return "of" + (isNullable ? "Nullable" : "") + "(" + sourceVar + ")";
        }
      }
    }

    abstract class IntermediateOperation implements Operation {

      @Override
      public @NotNull Generator<Operation> possibleNextOperations() {
        return intermediateOps();
      }

      private static @NotNull Generator<Operation> intermediateOps() {
        return Generator.sampledFrom(new Filter(), new Map(), new FlatMap(), new Or());
      }

      private static class Map extends IntermediateOperation {

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String targetExpression = context.getTargetExpression("s");
          return ".map(s ->" + targetExpression + ".toLowerCase())";
        }
      }

      private static class Filter extends IntermediateOperation {

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String targetExpression = context.getTargetExpression("s");
          return ".filter(s ->" + targetExpression + ".length() > 2)";
        }
      }

      private static class FlatMap extends IntermediateOperation {

        @Override
        public @NotNull String generate(@NotNull Context context) {
          ArrayList<String> registeredVars = new ArrayList<>(context.myRegisteredVars);
          String flatMapVar = context.registerSourceVar();
          String code = ".flatMap( " + flatMapVar + " -> " + context.generateOptionalCall(false) + ")";
          context.myRegisteredVars = registeredVars;
          return code;
        }

        @Override
        public boolean isNested() {
          return true;
        }
      }

      private static class Or extends IntermediateOperation {

        @Override
        public @NotNull String generate(@NotNull Context context) {
          return ".or(() ->" + context.generateOptionalCall(false) + ")";
        }

        @Override
        public boolean isNested() {
          return true;
        }
      }
    }

    abstract class TerminalOperation implements Operation {

      private static final Generator<Operation> TERMINAL_OPS = Generator.sampledFrom(
        Arrays.asList(
          create(".get()", "String"),
          create(".isPresent()", "boolean"),
          create(".isEmpty()", "boolean"),
          create(".stream()", "Stream<?>"),
          new IfPresent(),
          new IfPresentOrElse(),
          new OrElseThrow(),
          new OrElse("String")
        ));

      private final Generator<UsageContext> myContextGenerator;

      protected TerminalOperation(@NotNull Generator<UsageContext> contextGenerator) {
        myContextGenerator = contextGenerator;
      }

      @Override
      public @NotNull Generator<Operation> possibleNextOperations() {
        return Generator.constant(this);
      }

      private @NotNull String wrapInUsageContext(@NotNull Context context, @NotNull String optionalCall) {
        UsageContext usageContext = context.myEnv.generateValue(myContextGenerator, null);
        return usageContext.generateMethod(context, optionalCall);
      }

      private static TerminalOperation create(@NotNull String code, @NotNull String type) {
        return new TerminalOperation(getDefaultContexts(type)) {
          @Override
          public @NotNull String generate(@NotNull Context context) {
            return code;
          }
        };
      }

      private static Generator<UsageContext> getDefaultContexts(@NotNull String type) {
        return Generator.sampledFrom(new UsageContext.Statement(), new UsageContext.Assignment(type),
                                     new UsageContext.Declaration(type), new UsageContext.Return(type));
      }

      public static @NotNull Generator<Operation> terminalOps() {
        return TERMINAL_OPS;
      }

      private static @NotNull String generateStatement(@NotNull Environment env, @NotNull String target) {
        boolean inBlock = env.generateValue(Generator.booleans(), null);
        String statement = "System.out.println(" + target + ")";
        return inBlock ? "{ " + statement + " }" : statement;
      }

      private static final class IfPresent extends TerminalOperation {

        private IfPresent() {
          super(Generator.constant(new UsageContext.Statement()));
        }

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String target = context.getTargetExpression("s");
          return ".ifPresent(s -> " + generateStatement(context.myEnv, target) + ")";
        }
      }

      private static final class IfPresentOrElse extends TerminalOperation {

        private IfPresentOrElse() {
          super(Generator.constant(new UsageContext.Statement()));
        }

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String ifPresentTarget = context.getTargetExpression("s");
          String orElseTarget = context.getTargetExpression(null);
          return ".ifPresentOrElse(" +
                 "s -> " + TerminalOperation.generateStatement(context.myEnv, ifPresentTarget) + ", " +
                 "() -> " + TerminalOperation.generateStatement(context.myEnv, orElseTarget) +
                 ")";
        }
      }

      private static final class OrElseThrow extends TerminalOperation {

        private OrElseThrow() {
          super(Generator.constant(new UsageContext.Statement()));
        }

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String target = context.getTargetExpression(null);
          return ".orElseThrow(() -> new IllegalArgumentException(" + target + "))";
        }
      }

      private static final class OrElse extends TerminalOperation {

        private OrElse(@NotNull String type) {
          super(TerminalOperation.getDefaultContexts(type));
        }

        @Override
        public @NotNull String generate(@NotNull Context context) {
          String target = context.getTargetExpression(null);
          boolean isGet = context.myEnv.generateValue(Generator.booleans(), null);
          return isGet ? ".orElseGet(() -> " + target + ")" : ".orElse(" + target + ")";
        }
      }
    }
  }

  private static final class Context {

    private final List<String> myMethodParams = new ArrayList<>();
    private final Environment myEnv;
    private final int maxDepth;
    private List<String> myRegisteredVars = new ArrayList<>();
    private String afterStep;
    private int curDepth = 0;

    private Context(@NotNull Environment env) {
      myEnv = env;
      maxDepth = myEnv.generateValue(Generator.integers(1, 3), null);
    }

    private @NotNull String generateOptionalCall(boolean generateTerminalOperation) {
      StringBuilder optionalCall = new StringBuilder("Optional.");
      int maxOperations = myEnv.generateValue(Generator.integers(2, 4), null);
      int curDepth = this.curDepth++;

      Operation operation = chooseOperation(Operation.SourceOperation.sourceOps());
      for (int nOperation = 1; nOperation <= maxOperations; nOperation++) {
        boolean isTerminalOperation = nOperation == maxOperations && generateTerminalOperation;
        if (isTerminalOperation) {
          operation = chooseOperation(Operation.TerminalOperation.terminalOps());
        }
        else if (curDepth >= maxDepth) {
          while (operation.isNested()) {
            operation = chooseOperation(operation.possibleNextOperations());
          }
        }

        optionalCall.append(operation.generate(this));
        operation = chooseOperation(operation.possibleNextOperations());
      }

      String code = optionalCall.toString();
      if (!generateTerminalOperation) return code;
      return ((Operation.TerminalOperation)operation).wrapInUsageContext(this, code);
    }

    private @NotNull Operation chooseOperation(@NotNull Generator<Operation> operationGenerator) {
      return myEnv.generateValue(operationGenerator, null);
    }

    private @NotNull String registerSourceVar() {
      String varName = "var" + myRegisteredVars.size();
      myRegisteredVars.add(varName);
      return varName;
    }

    private @NotNull String declareParameter() {
      String paramName = "param" + myMethodParams.size();
      myMethodParams.add(paramName);
      return paramName;
    }

    private @NotNull String getTargetExpression(@Nullable String defaultTarget) {
      boolean useDefaultTarget = defaultTarget != null && myEnv.generateValue(Generator.booleans(), null);
      if (useDefaultTarget) return defaultTarget;
      boolean useConstant = myEnv.generateValue(Generator.booleans(), null);
      if (useConstant) return "\"foo\"";
      if (myMethodParams.isEmpty() && myRegisteredVars.isEmpty()) return declareParameter();
      List<String> targets = new ArrayList<>(myMethodParams);
      targets.addAll(myRegisteredVars);
      targets.add("\"foo\"");
      if (defaultTarget != null) targets.add(defaultTarget);
      int nTargets = myEnv.generateValue(Generator.integers(1, 3), null);
      String target = IntStream.range(1, nTargets + 1)
        .mapToObj(i -> myEnv.generateValue(Generator.sampledFrom(targets), null))
        .collect(Collectors.joining("+"));
      return nTargets > 1 ? "(" + target + ")" : target;
    }
  }

  private abstract static class UsageContext {

    protected final String type;

    protected UsageContext(@Nullable String type) {
      this.type = type;
    }

    protected @NotNull String generateMethod(@NotNull Context context, @NotNull String optionalCall) {
      List<String> params = context.myMethodParams;
      StringBuilder signature = new StringBuilder("void foo(");
      if (!params.isEmpty()) signature.append("String ").append(String.join(", String ", params));
      signature.append(")");
      return signature.append("{\n").append(generateMethodBody(context, optionalCall)).append("\n}").toString();
    }

    protected abstract @NotNull String generateMethodBody(@NotNull Context context, @NotNull String optionalCall);

    private static final class Declaration extends UsageContext {

      private Declaration(@NotNull String type) {super(type);}

      @Override
      protected @NotNull String generateMethodBody(@NotNull Context context, @NotNull String optionalCall) {
        boolean hasImplicitType = context.myEnv.generateValue(Generator.booleans(), null);
        String declaration = (hasImplicitType ? "var" : type) + " result = " + optionalCall + ";";
        boolean isFinalVar = context.myEnv.generateValue(Generator.booleans(), null);
        if (!isFinalVar) return declaration;
        context.afterStep = "Runnable r = () -> System.out.println(result);";
        return "final " + declaration;
      }
    }

    private static final class Assignment extends UsageContext {

      private Assignment(@NotNull String type) {
        super(type);
      }

      @Override
      protected @NotNull String generateMethodBody(@NotNull Context context, @NotNull String optionalCall) {
        return type + " result;\n " + "result = " + optionalCall + ";";
      }
    }

    private static final class Return extends UsageContext {

      private Return(@NotNull String type) {
        super(type);
      }

      @Override
      protected @NotNull String generateMethod(@NotNull Context context, @NotNull String optionalCall) {
        return super.generateMethod(context, optionalCall).replace("void", type);
      }

      @Override
      protected @NotNull String generateMethodBody(@NotNull Context context, @NotNull String optionalCall) {
        return "return " + optionalCall + ";";
      }
    }

    private static final class Statement extends UsageContext {

      private Statement() {
        super(null);
      }

      @Override
      protected @NotNull String generateMethodBody(@NotNull Context context, @NotNull String optionalCall) {
        return optionalCall + ";";
      }
    }
  }
}

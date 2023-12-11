// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.inst.ArrayStoreInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.ThrowInstruction;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Properties;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CatchMayIgnoreExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean m_ignoreCatchBlocksWithComments = true;
  public boolean m_ignoreNonEmptyCatchBlock = true;
  public boolean m_ignoreUsedIgnoredName = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreCatchBlocksWithComments", InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.comments")),
      checkbox("m_ignoreNonEmptyCatchBlock", InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.nonempty")),
      checkbox("m_ignoreUsedIgnoredName", InspectionGadgetsBundle.message("inspection.catch.ignores.exception.option.ignored.used")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitTryStatement(@NotNull PsiTryStatement statement) {
        super.visitTryStatement(statement);
        final PsiCatchSection[] catchSections = statement.getCatchSections();
        for (final PsiCatchSection section : catchSections) {
          checkCatchSection(section);
        }
      }

      private void checkCatchSection(PsiCatchSection section) {
        final PsiParameter parameter = section.getParameter();
        if (parameter == null || parameter.isUnnamed()) return;
        final PsiIdentifier identifier = parameter.getNameIdentifier();
        if (identifier == null) return;
        final String parameterName = parameter.getName();
        if (PsiUtil.isIgnoredName(parameterName)) {
          if (!m_ignoreUsedIgnoredName && VariableAccessUtils.variableIsUsed(parameter, section)) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.used.message"));
          }
          return;
        }
        if (parameterName.equals("cannotHappen")) {
          // Just ignore parameter named as 'cannotHappen' (this code style appears in OpenJDK source)
          return;
        }
        if ((parameterName.equals("expected") || parameterName.equals("ok")) && TestUtils.isInTestSourceContent(section)) {
          return;
        }
        final PsiElement catchToken = section.getFirstChild();
        if (catchToken == null) return;

        final PsiCodeBlock block = section.getCatchBlock();
        if (block == null) return;
        SuppressForTestsScopeFix fix = SuppressForTestsScopeFix.build(CatchMayIgnoreExceptionInspection.this, section);
        if (ControlFlowUtils.isEmpty(block, m_ignoreCatchBlocksWithComments, true)) {
          var renameFix = QuickFixFactory.getInstance().createRenameToIgnoredFix(parameter, false);
          AddCatchBodyFix addBodyFix = getAddBodyFix(block);
          holder.registerProblem(catchToken, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.empty.message"),
                                 LocalQuickFix.notNullElements(renameFix, addBodyFix, fix));
        }
        else if (!VariableAccessUtils.variableIsUsed(parameter, section)) {
          if (!m_ignoreNonEmptyCatchBlock &&
              (!m_ignoreCatchBlocksWithComments || PsiTreeUtil.getChildOfType(block, PsiComment.class) == null)) {
            holder.registerProblem(identifier, InspectionGadgetsBundle.message("inspection.catch.ignores.exception.unused.message"),
                                   LocalQuickFix.notNullElements(
                                     QuickFixFactory.getInstance().createRenameToIgnoredFix(parameter, false), fix));
          }
        }
        else {
          String className = mayIgnoreVMException(parameter, block);
          if (className != null) {
            String message = InspectionGadgetsBundle.message("inspection.catch.ignores.exception.vm.ignored.message", className);
            holder.registerProblem(catchToken, message, LocalQuickFix.notNullElements(fix));
          }
        }
      }

      @Nullable
      private AddCatchBodyFix getAddBodyFix(PsiCodeBlock block) {
        if (ControlFlowUtils.isEmpty(block, true, true)) {
          try {
            FileTemplate template =
              FileTemplateManager.getInstance(holder.getProject()).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
            if (!StringUtil.isEmptyOrSpaces(template.getText())) {
              return new AddCatchBodyFix();
            }
          }
          catch (IllegalStateException ignored) { }
        }
        return null;
      }

      /**
       * Returns class name of important VM exception (like NullPointerException) if given catch block may ignore it
       *
       * @param parameter a catch block parameter
       * @param block     a catch block body
       * @return class name of important VM exception (like NullPointerException) if given catch block may ignore it
       */
      private static @Nullable String mayIgnoreVMException(PsiParameter parameter, PsiCodeBlock block) {
        PsiType type = parameter.getType();
        if (!type.equalsToText(CommonClassNames.JAVA_LANG_THROWABLE) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_EXCEPTION) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) &&
            !type.equalsToText(CommonClassNames.JAVA_LANG_ERROR)) {
          return null;
        }
        // Let's assume that exception is NPE or SOE with null cause and null message and see what happens during dataflow.
        // Will it produce any side-effect?
        String className = type.equalsToText(CommonClassNames.JAVA_LANG_ERROR) ? "java.lang.StackOverflowError"
                                                                               : CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION;
        Project project = parameter.getProject();
        PsiClassType exception = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(className, parameter.getResolveScope());
        PsiClass exceptionClass = exception.resolve();
        if (exceptionClass == null) return null;

        DfaValueFactory factory = new DfaValueFactory(project);
        ControlFlow flow = ControlFlowAnalyzer.buildFlow(block, factory, true);
        if (flow == null) return null;
        var interpreter = new CatchDataFlowInterpreter(exception, parameter, flow);
        DfaMemoryState memState = new JvmDfaMemoryStateImpl(factory);
        DfaVariableValue stableExceptionVar = PlainDescriptor.createVariableValue(factory, new LightParameter("tmp", exception, block));
        DfaVariableValue exceptionVar = PlainDescriptor.createVariableValue(factory, parameter);
        memState.applyCondition(exceptionVar.eq(stableExceptionVar));
        memState.applyCondition(exceptionVar.cond(RelationType.IS, DfTypes.typedObject(exception, Nullability.NOT_NULL)));
        return interpreter.interpret(memState) == RunnerResult.OK ? className : null;
      }
    };
  }

  private static class CatchDataFlowInterpreter extends StandardDataFlowInterpreter {
    final DfaVariableValue myExceptionVar;
    final @NotNull List<PsiMethod> myMethods;
    final @NotNull PsiParameter myParameter;
    final @NotNull PsiCodeBlock myBlock;

    CatchDataFlowInterpreter(@NotNull PsiClassType exception, @NotNull PsiParameter parameter, @NotNull ControlFlow flow) {
      super(flow, DfaListener.EMPTY);
      myParameter = parameter;
      myBlock = (PsiCodeBlock)flow.getPsiAnchor();
      PsiClass exceptionClass = Objects.requireNonNull(exception.resolve());
      myExceptionVar = PlainDescriptor.createVariableValue(getFactory(), parameter);
      myMethods = StreamEx.of("getMessage", "getLocalizedMessage", "getCause")
        .flatArray(name -> exceptionClass.findMethodsByName(name, true))
        .filter(m -> m.getParameterList().isEmpty())
        .toList();
    }

    @Override
    protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
      Instruction instruction = instructionState.getInstruction();
      DfaMemoryState memState = instructionState.getMemoryState();
      if (instruction instanceof EnsureInstruction) {
        if (((EnsureInstruction)instruction).getProblem() instanceof ContractFailureProblem &&
            memState.peek().getDfType().equals(DfType.FAIL)) {
          cancel();
        }
      }
      if (instruction instanceof MethodCallInstruction) {
        if (myMethods.contains(((MethodCallInstruction)instruction).getTargetMethod())) {
          DfaValue qualifier = memState.peek();
          // Methods like "getCause" and "getMessage" return "null" for our test exception
          if (memState.areEqual(qualifier, myExceptionVar)) {
            memState.pop();
            memState.push(getFactory().fromDfType(DfTypes.NULL));
            return instructionState.nextStates(this);
          }
        }
      }
      if (isSideEffect(instruction, memState)) {
        cancel();
      }
      return super.acceptInstruction(instructionState);
    }

    private boolean isSideEffect(Instruction instruction, DfaMemoryState memState) {
      if (instruction instanceof FlushFieldsInstruction || instruction instanceof ThrowInstruction) {
        return true;
      }
      if (instruction instanceof FlushVariableInstruction) {
        return !isModificationAllowed(((FlushVariableInstruction)instruction).getVariable());
      }
      if (instruction instanceof AssignInstruction) {
        return !isModificationAllowed(memState.getStackValue(1));
      }
      if (instruction instanceof ReturnInstruction) {
        return ((ReturnInstruction)instruction).getAnchor() != null;
      }
      if (instruction instanceof MethodCallInstruction) {
        return !((MethodCallInstruction)instruction).getMutationSignature().isPure();
      }
      if (instruction instanceof ArrayStoreInstruction) {
        return true;
      }
      return false;
    }

    protected boolean isModificationAllowed(DfaValue variable) {
      if (!(variable instanceof DfaVariableValue)) return false;
      PsiElement owner = ((DfaVariableValue)variable).getPsiVariable();
      return owner == myParameter || owner != null && PsiTreeUtil.isAncestor(myBlock, owner, false);
    }
  }

  private static class AddCatchBodyFix extends PsiUpdateModCommandQuickFix implements LowPriorityAction {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiCatchSection catchSection = ObjectUtils.tryCast(element.getParent(), PsiCatchSection.class);
      if (catchSection == null) return;
      PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) return;
      String parameterName = parameter.getName();
      FileTemplate template = FileTemplateManager.getInstance(project).getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);

      Properties props = FileTemplateManager.getInstance(project).getDefaultProperties();
      props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, parameterName);
      props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION_TYPE, parameter.getType().getCanonicalText());
      PsiDirectory directory = catchSection.getContainingFile().getContainingDirectory();
      if (directory != null) {
        JavaTemplateUtil.setPackageNameAttribute(props, directory);
      }

      try {
        PsiCodeBlock block =
          PsiElementFactory.getInstance(project).createCodeBlockFromText("{\n" + template.getText(props) + "\n}", null);
        Objects.requireNonNull(catchSection.getCatchBlock()).replace(block);
      }
      catch (ProcessCanceledException ce) {
        throw ce;
      }
      catch (Exception e) {
        throw new IncorrectOperationException("Incorrect file template", (Throwable)e);
      }
    }
  }
}

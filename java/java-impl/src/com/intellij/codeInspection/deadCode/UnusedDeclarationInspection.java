// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableFix;
import com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.options.JavaInspectionButtons.ButtonKind;
import static com.intellij.codeInsight.options.JavaInspectionControls.button;
import static com.intellij.codeInspection.options.OptPane.*;

public final class UnusedDeclarationInspection extends UnusedDeclarationInspectionBase {
  private final UnusedParametersInspection myUnusedParameters = new UnusedParametersInspection();

  public UnusedDeclarationInspection() { }

  @TestOnly
  public UnusedDeclarationInspection(boolean enabledInEditor) {
    super(enabledInEditor);
  }

  @Override
  public String getAlternativeID() {
    return UnusedSymbolLocalInspection.UNUSED_PARAMETERS_SHORT_NAME;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (localInspectionBase.PARAMETER) {
      globalContext.getRefManager().iterate(new RefJavaVisitor() {
        @Override
        public void visitMethod(@NotNull RefMethod refMethod) {
          try {
            if (!globalContext.shouldCheck(refMethod, UnusedDeclarationInspection.this) ||
                !UnusedDeclarationPresentation.compareVisibilities(refMethod, localInspectionBase.getParameterVisibility())) {
              return;
            }
            CommonProblemDescriptor[] descriptors = myUnusedParameters.checkElement(refMethod, scope, manager, globalContext, problemDescriptionsProcessor);
            if (descriptors != null) {
              problemDescriptionsProcessor.addProblemElement(refMethod, descriptors);
            }
          }
          catch (ProcessCanceledException | IndexNotReadyException e) {
            throw e;
          }
          catch (Throwable e) {
            LOG.error("Exception on '" + refMethod.getExternalName() + "'", e);
          }
        }
      });
    }
    super.runInspection(scope, manager, globalContext, problemDescriptionsProcessor);
  }

  @Override
  public RefGraphAnnotator getAnnotator(@NotNull RefManager refManager) {
    return new UnusedVariablesGraphAnnotator(InspectionManager.getInstance(refManager.getProject()), refManager);
  }

  @Override
  public boolean queryExternalUsagesRequests(@NotNull InspectionManager manager,
                                             @NotNull GlobalInspectionContext globalContext,
                                             @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    boolean requests = super.queryExternalUsagesRequests(manager, globalContext, problemDescriptionsProcessor);
    if (!requests && localInspectionBase.PARAMETER) {
      myUnusedParameters.queryExternalUsagesRequests(manager, globalContext, problemDescriptionsProcessor);
    }
    return requests;
  }

  @Override
  public @Nullable String getHint(@NotNull QuickFix fix) {
    return myUnusedParameters.getHint(fix);
  }

  @Override
  public @Nullable LocalQuickFix getQuickFix(String hint) {
    return myUnusedParameters.getQuickFix(hint);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      tabs(
        localInspectionBase.getOptionsPane().asTab(JavaBundle.message("tab.title.members.to.report")).prefix("members"),
        getEntryPointsPane().asTab(JavaBundle.message("tab.title.entry.points"))
      )
    );
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return super.getOptionController()
      .onPrefix("members", localInspectionBase.getOptionController())
      .onPrefix("ext", idx -> getExtensions().get(Integer.parseInt(idx)).isSelected(),
                (idx, value) -> {
                  EntryPoint point = getExtensions().get(Integer.parseInt(idx));
                  point.setSelected((Boolean)value);
                  saveEntryPointElement(point);
                });
  }

  private @NotNull OptPane getEntryPointsPane() {
    List<OptRegularComponent> content = new ArrayList<>();
    content.add(dropdown("TEST_ENTRY_POINTS", JavaBundle.message("label.unused.declaration.reachable.from.tests.option"),
                         option("true", JavaBundle.message("radio.button.unused.declaration.used.option")),
                         option("false", JavaBundle.message("radio.button.unused.declaration.unused.option"))));
    content.add(group(
      JavaBundle.message("label.entry.points"),
      horizontalStack(button(ButtonKind.ENTRY_POINT_CODE_PATTERNS),
                      button(ButtonKind.ENTRY_POINT_ANNOTATIONS))));
    content.add(checkbox("ADD_MAINS_TO_ENTRIES", JavaBundle.message("inspection.dead.code.option.main")));
    content.add(checkbox("ADD_APPLET_TO_ENTRIES", JavaBundle.message("inspection.dead.code.option.applet")));
    content.add(checkbox("ADD_SERVLET_TO_ENTRIES", JavaBundle.message("inspection.dead.code.option.servlet")));
    EntryStream.of(getExtensions())
      .filterValues(EntryPoint::showUI)
      .mapKeyValue((idx, ext) ->
                     checkbox(idx.toString(), ext.getDisplayName()).prefix("ext"))
      .into(content);
    content.add(checkbox("ADD_NONJAVA_TO_ENTRIES", JavaBundle.message("inspection.dead.code.option.external")));
    return new OptPane(content);
  }

  private class UnusedVariablesGraphAnnotator extends RefGraphAnnotator {
    private final InspectionManager myInspectionManager;
    private final GlobalInspectionContextImpl myContext;
    private Tools myTools;

    UnusedVariablesGraphAnnotator(@NotNull InspectionManager inspectionManager, @NotNull RefManager refManager) {
      myInspectionManager = inspectionManager;
      myContext = (GlobalInspectionContextImpl)((RefManagerImpl)refManager).getContext();
    }

    @Override
    public void onReferencesBuild(RefElement refElement) {
      if (refElement instanceof RefClass refClass) {
        UClass uClass = refClass.getUastElement();
        if (uClass != null) {
          for (UClassInitializer initializer : uClass.getInitializers()) {
            findUnusedLocalVariables(initializer.getUastBody(), refElement);
          }
        }
      }
      else if (refElement instanceof RefMethod refMethod) {
        UMethod element = refMethod.getUastElement();
        if (element != null) {
          UExpression body = element.getUastBody();
          if (body != null) {
            findUnusedLocalVariables(body, refElement);
          }
        }
      }
      else if (refElement instanceof RefField refField) {
        UField field = refField.getUastElement();
        if (field != null) {
          UExpression initializer = field.getUastInitializer();
          if (initializer != null) {
            findUnusedLocalVariables(initializer, refElement);
          }
        }
      }
    }

    private void findUnusedLocalVariables(@NotNull UExpression body, @NotNull RefElement refElement) {
      PsiElement psiBody = body.getSourcePsi();
      if (psiBody == null) return;
      Tools tools = myTools;
      if (tools == null) {
        myTools = tools = myContext.getTools().get(getShortName());
      }
      if (!tools.isEnabled(psiBody)) return;
      InspectionToolWrapper<?,?> toolWrapper = tools.getInspectionTool(psiBody);
      InspectionToolPresentation presentation = myContext.getPresentation(toolWrapper);
      if (((UnusedDeclarationInspection)toolWrapper.getTool()).getSharedLocalInspectionTool().LOCAL_VARIABLE) {
        List<CommonProblemDescriptor> descriptors = new ArrayList<>();
        findUnusedLocalVariablesInElement(psiBody, descriptors);
        if (!descriptors.isEmpty()) {
          presentation.addProblemElement(refElement, descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY));
        }
      }
    }

    private void findUnusedLocalVariablesInElement(@NotNull PsiElement element, @NotNull List<? super CommonProblemDescriptor> descriptors) {
      Set<PsiVariable> usedVariables = new HashSet<>();
      List<DefUseUtil.Info> unusedDefs = DefUseUtil.getUnusedDefs(element, usedVariables);
      if (unusedDefs != null && !unusedDefs.isEmpty()) {
        for (DefUseUtil.Info varDefInfo : unusedDefs) {
          PsiElement parent = varDefInfo.getContext();
          PsiVariable variable = varDefInfo.getVariable();
          if (variable.isUnnamed() || PsiUtil.isIgnoredName(variable.getName())) continue;
          if (parent instanceof PsiDeclarationStatement || parent instanceof PsiForeachStatement ||
              variable instanceof PsiResourceVariable || variable instanceof PsiPatternVariable) {
            if (!varDefInfo.isRead() && !SuppressionUtil.inspectionResultSuppressed(variable, UnusedDeclarationInspection.this)) {
              descriptors.add(createProblemDescriptor(variable));
            }
          }
        }
      }
      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitClass(@NotNull PsiClass aClass) {
          // prevent going to local classes
        }

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression lambdaExpr) {
          PsiElement body = lambdaExpr.getBody();
          if (body == null) return;
          findUnusedLocalVariablesInElement(body, descriptors);
        }

        @Override
        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
          super.visitLocalVariable(variable);
          if (!usedVariables.contains(variable) && variable.getInitializer() == null &&
              !SuppressionUtil.inspectionResultSuppressed(variable, UnusedDeclarationInspection.this)) {
            descriptors.add(createProblemDescriptor(variable));
          }
        }
      });
    }

    private @NotNull ProblemDescriptor createProblemDescriptor(@NotNull PsiVariable psiVariable) {
      PsiElement toHighlight = ObjectUtils.notNull(psiVariable.getNameIdentifier(), psiVariable);
      return myInspectionManager.createProblemDescriptor(
        toHighlight, JavaBundle.message("inspection.unused.assignment.problem.descriptor1"), 
        psiVariable instanceof PsiLocalVariable ? LocalQuickFix.from(new RemoveUnusedVariableFix(psiVariable)) : new SafeDeleteFix(psiVariable),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
    }
  }

  @Override
  public boolean isReadActionNeeded() {
    return true;
  }
}

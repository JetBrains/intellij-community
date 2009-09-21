package com.intellij.refactoring.typeCook;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.typeCook.deductive.builder.ReductionSystem;
import com.intellij.refactoring.typeCook.deductive.builder.Result;
import com.intellij.refactoring.typeCook.deductive.builder.SystemBuilder;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;
import com.intellij.refactoring.typeCook.deductive.resolver.ResolverTree;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class TypeCookProcessor extends BaseRefactoringProcessor {
  private PsiElement[] myElements;
  private final Settings mySettings;
  private Result myResult;

  public TypeCookProcessor(Project project, PsiElement[] elements, Settings settings) {
    super(project);

    myElements = elements;
    mySettings = settings;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new TypeCookViewDescriptor(myElements);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final SystemBuilder systemBuilder = new SystemBuilder(myProject, mySettings);

    final ReductionSystem commonSystem = systemBuilder.build(myElements);
    myResult = new Result(commonSystem);

    final ReductionSystem[] systems = commonSystem.isolate();

    for (final ReductionSystem system : systems) {
      if (system != null) {
        final ResolverTree tree = new ResolverTree(system);

        tree.resolve();

        final Binding solution = tree.getBestSolution();

        if (solution != null) {
          myResult.incorporateSolution(solution);
        }
      }
    }

    final HashSet<PsiElement> changedItems = myResult.getCookedElements();
    final UsageInfo[] usages = new UsageInfo[changedItems.size()];

    int i = 0;
    for (final PsiElement element : changedItems) {
      if (!(element instanceof PsiTypeCastExpression)) {
        usages[i++] = new UsageInfo(element) {
          public String getTooltipText() {
            return myResult.getCookedType(element).getCanonicalText();
          }
        };
      }
      else {
        usages[i++] = new UsageInfo(element);
      }
    }

    return usages;
  }

  protected void refreshElements(PsiElement[] elements) {
    myElements = elements;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    final HashSet<PsiElement> victims = new HashSet<PsiElement>();

    for (UsageInfo usage : usages) {
      victims.add(usage.getElement());
    }

    myResult.apply (victims);

    WindowManager.getInstance().getStatusBar(myProject).setInfo(myResult.getReport());
  }

  @Override
  protected boolean isGlobalUndoAction() {
    return true;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("type.cook.command");
  }

  public List<PsiElement> getElements() {
    return Collections.unmodifiableList(Arrays.asList(myElements));
  }
}

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Consumer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HighlightOverridingMethodsHandler extends HighlightUsagesHandlerBase<PsiClass> {
  private final PsiElement myTarget;
  private final PsiClass myClass;

  public HighlightOverridingMethodsHandler(final Editor editor, final PsiFile file, final PsiElement target, final PsiClass psiClass) {
    super(editor, file);
    myTarget = target;
    myClass = psiClass;
  }

  public List<PsiClass> getTargets() {
    PsiReferenceList list = PsiKeyword.EXTENDS.equals(myTarget.getText()) ? myClass.getExtendsList() : myClass.getImplementsList();
    if (list == null) return Collections.emptyList();
    final PsiClassType[] classTypes = list.getReferencedTypes();
    return ChooseClassAndDoHighlightRunnable.resolveClasses(classTypes);
  }

  protected void selectTargets(final List<PsiClass> targets, final Consumer<List<PsiClass>> selectionConsumer) {
    new ChooseClassAndDoHighlightRunnable(targets, myEditor, CodeInsightBundle.message("highlight.overridden.classes.chooser.title")) {
      protected void selected(PsiClass... classes) {
        selectionConsumer.consume(Arrays.asList(classes));
      }
    }.run();
  }

  public void computeUsages(final List<PsiClass> classes) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.implements");
    for (PsiMethod method : myClass.getMethods()) {
      List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
      for (HierarchicalMethodSignature superSignature : superSignatures) {
        PsiClass containingClass = superSignature.getMethod().getContainingClass();
        if (containingClass == null) continue;
        for (PsiClass classToAnalyze : classes) {
          if (InheritanceUtil.isInheritorOrSelf(classToAnalyze, containingClass, true)) {
            addOccurrence(method.getNameIdentifier());
            break;
          }
        }
      }
    }
    if (myReadUsages.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      String name;
      if (classes.size() == 1) {
        final ItemPresentation presentation = classes.get(0).getPresentation();
        name = presentation != null ? presentation.getPresentableText() : "";
      }
      else {
        name = "";
      }
      myHintText = CodeInsightBundle.message("no.methods.overriding.0.are.found", classes.size(), name);
    }
    else {
      addOccurrence(myTarget);
      final int methodCount = myReadUsages.size()-1;  // exclude 'target' keyword
      myStatusText = CodeInsightBundle.message("status.bar.overridden.methods.highlighted.message", methodCount,
                                                                        HighlightUsagesHandler.getShortcutText());
    }
  }
}

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;

import java.util.ArrayList;
import java.util.List;

public class HighlightOverridingMethodsHandler extends HighlightUsagesHandlerBase {
  private PsiElement myTarget;
  private PsiClass myClass;

  public HighlightOverridingMethodsHandler(final Editor editor, final PsiFile file, final PsiElement target, final PsiClass psiClass) {
    super(editor, file);
    myTarget = target;
    myClass = psiClass;
  }

  public void highlightUsages() {
    PsiReferenceList list = PsiKeyword.EXTENDS.equals(myTarget.getText()) ? myClass.getExtendsList() : myClass.getImplementsList();
    if (list == null) return;
    final PsiClassType[] classTypes = list.getReferencedTypes();

    if (classTypes.length == 0) return;
    new ChooseClassAndDoHighlightRunnable(classTypes, myEditor, CodeInsightBundle.message("highlight.overridden.classes.chooser.title")) {
      protected void selected(PsiClass... classes) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.implements");
        List<PsiElement> toHighlight = new ArrayList<PsiElement>();
        for (PsiMethod method : myClass.getMethods()) {
          List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
          for (HierarchicalMethodSignature superSignature : superSignatures) {
            PsiClass containingClass = superSignature.getMethod().getContainingClass();
            if (containingClass == null) continue;
            for (PsiClass classToAnalyze : classes) {
              if (InheritanceUtil.isInheritorOrSelf(classToAnalyze, containingClass, true)) {
                toHighlight.add(method.getNameIdentifier());
                break;
              }
            }
          }
        }
        if (toHighlight.isEmpty()) {
          if (ApplicationManager.getApplication().isUnitTestMode()) return;
          String name = classes.length == 1 ? classes[0].getPresentation().getPresentableText() : "";
          String text = CodeInsightBundle.message("no.methods.overriding.0.are.found", classes.length, name);
          HintManager.getInstance().showInformationHint(myEditor, text);
        }
        else {
          Project project = myTarget.getProject();
          final boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(myEditor, HighlightManager.getInstance(project));
          toHighlight.add(myTarget);
          HighlightUsagesHandler.highlightOtherOccurrences(toHighlight, myEditor, clearHighlights);
          HighlightUsagesHandler.setupFindModel(project);
          final int methodCount = toHighlight.size()-1;  // exclude 'target' keyword
          String message = clearHighlights ? "" : CodeInsightBundle.message("status.bar.overridden.methods.highlighted.message", methodCount,
                                                                            HighlightUsagesHandler.getShortcutText());
          WindowManager.getInstance().getStatusBar(project).setInfo(message);
        }
      }
    }.run();
  }
}

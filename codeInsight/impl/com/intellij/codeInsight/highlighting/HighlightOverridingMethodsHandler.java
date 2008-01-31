package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
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

public class HighlightOverridingMethodsHandler implements HighlightUsagesHandlerDelegate {
  public boolean highlightUsages(final Editor editor, final PsiFile file) {
    int offset = TargetElementUtilBase.adjustOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    final PsiElement target = file.findElementAt(offset);
    if (target instanceof PsiKeyword && (PsiKeyword.EXTENDS.equals(target.getText()) || PsiKeyword.IMPLEMENTS.equals(target.getText()))) {
      PsiElement parent = target.getParent();
      if (!(parent instanceof PsiReferenceList)) return false;
      PsiElement grand = parent.getParent();
      if (!(grand instanceof PsiClass)) return false;
      final PsiClass aClass = (PsiClass)grand;
      PsiReferenceList list = PsiKeyword.EXTENDS.equals(target.getText()) ? aClass.getExtendsList() : aClass.getImplementsList();
      if (list == null) return true;
      final PsiClassType[] classTypes = list.getReferencedTypes();

      if (classTypes.length == 0) return true;
      new ChooseClassAndDoHighlightRunnable(classTypes, editor, CodeInsightBundle.message("highlight.overridden.classes.chooser.title")) {
        protected void selected(PsiClass... classes) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.implements");
          List<PsiElement> toHighlight = new ArrayList<PsiElement>();
          for (PsiMethod method : aClass.getMethods()) {
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
            HintManager.getInstance().showInformationHint(editor, text);
          }
          else {
            Project project = target.getProject();
            final boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(editor, HighlightManager.getInstance(project));
            toHighlight.add(target);
            HighlightUsagesHandler.highlightOtherOccurrences(toHighlight, project, editor, clearHighlights);
            HighlightUsagesHandler.setupFindModel(project);
            final int methodCount = toHighlight.size()-1;  // exclude 'target' keyword
            String message = clearHighlights ? "" : CodeInsightBundle.message("status.bar.overridden.methods.highlighted.message", methodCount,
                                                                              HighlightUsagesHandler.getShortcutText());
            WindowManager.getInstance().getStatusBar(project).setInfo(message);
          }
        }
      }.run();
      return true;
    }
    return false;
  }
}

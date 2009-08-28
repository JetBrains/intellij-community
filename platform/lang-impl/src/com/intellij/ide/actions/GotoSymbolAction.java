package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoSymbolModel2;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.ChooseByNameRegistry;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public class GotoSymbolAction extends GotoActionBase {

  public void gotoActionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.symbol");
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoSymbolModel2(project), getPsiContext(e));
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose ()
      {
        if (GotoSymbolAction.class.equals (myInAction))
          myInAction = null;
      }
      public void elementChosen(Object element) {
        ((NavigationItem)element).navigate(true);
      }
    }, ModalityState.current(), true);
  }

  protected boolean hasContributors() {
    return ChooseByNameRegistry.getInstance().getSymbolModelContributors().length > 0;
  }
}
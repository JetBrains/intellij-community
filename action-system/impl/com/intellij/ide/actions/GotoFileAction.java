
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene Belyaev
 */
public class GotoFileAction extends GotoActionBase {
  public void gotoActionPerformed(AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file");
    final Project project = e.getData(DataKeys.PROJECT);
    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoFileModel(project));
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose () {
        if (GotoFileAction.class.equals(myInAction)) myInAction = null;
      }
      public void elementChosen(Object element){
        final PsiFile file = (PsiFile)element;
        if (file == null) return;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file.getVirtualFile());
            if (descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
          }
        }, ModalityState.NON_MODAL);
      }
    }, ModalityState.current(), true);
  }
}
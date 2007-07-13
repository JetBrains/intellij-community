package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;

import java.awt.*;
import java.util.Map;

public class GotoActionAction extends GotoActionBase {
  public void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final Component component = e.getData(DataKeys.CONTEXT_COMPONENT);

    final ChooseByNamePopup popup = ChooseByNamePopup.createPopup(project, new GotoActionModel(project, component), getPsiContext(e));
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose() {
        if (GotoActionAction.class.equals(myInAction)) {
          myInAction = null;
        }
      }

      public void elementChosen(Object element) {
        final AnAction action = (AnAction)((Map.Entry)element).getKey();
        if (action != null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final AnActionEvent event = new AnActionEvent(e.getInputEvent(), DataManager.getInstance().getDataContext(component),
                                                            e.getPlace(), e.getPresentation(), ActionManager.getInstance(),
                                                            e.getModifiers());
              action.beforeActionPerformedUpdate(event);
              action.update(event);

              if (event.getPresentation().isEnabled() && event.getPresentation().isVisible()) {
                action.actionPerformed(event);
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    }, ModalityState.current(), true);
  }
}
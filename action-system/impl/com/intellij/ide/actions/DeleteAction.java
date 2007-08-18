
package com.intellij.ide.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;

public class DeleteAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.DeleteAction");

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    DeleteProvider provider = getDeleteProvider(dataContext);
    if (provider == null) return;
    try {
      provider.deleteElement(dataContext);
    }
    catch (Throwable t) {
      if (t instanceof StackOverflowError){
        t.printStackTrace();
      }
      LOG.error(t);
    }
  }

  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return (DeleteProvider)dataContext.getData(DataConstants.DELETE_ELEMENT_PROVIDER);
  }

  public void update(AnActionEvent event){
    String place = event.getPlace();
    Presentation presentation = event.getPresentation();
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(place) || ActionPlaces.COMMANDER_POPUP.equals(place))
      presentation.setText(IdeBundle.message("action.delete.ellipsis"));
    else
      presentation.setText(IdeBundle.message("action.delete"));
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    DeleteProvider provider = getDeleteProvider(dataContext);
    if (event.getInputEvent() instanceof KeyEvent) {
      Object component = dataContext.getData(DataConstants.CONTEXT_COMPONENT);
      if (component instanceof JTextComponent) provider = null; // Do not override text deletion
    }
    presentation.setEnabled(provider != null && provider.canDeleteElement(dataContext));
  }

  public DeleteAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  public DeleteAction() {
  }
}

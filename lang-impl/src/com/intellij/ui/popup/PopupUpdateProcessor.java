package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import java.awt.*;

/**
 * @author yole
 */
public abstract class PopupUpdateProcessor extends JBPopupAdapter {

  private Project myProject;

  protected PopupUpdateProcessor(Project project) {
    myProject = project;
  }

  public abstract void updatePopup(Object lookupItemObject);

  public void beforeShown(final LightweightWindowEvent windowEvent) {
    final Lookup activeLookup = LookupManager.getInstance(myProject).getActiveLookup();
    if (activeLookup != null) {
      activeLookup.addLookupListener(new LookupAdapter() {
        public void currentItemChanged(LookupEvent event) {
          if (windowEvent.asPopup().isVisible()) { //was not canceled yet
            final LookupElement item = event.getItem();
            if (item != null) {
              windowEvent.asPopup().cancel(); //close this one
              updatePopup(item.getObject()); //open next
            }
          }
          activeLookup.removeLookupListener(this); //do not multiply listeners
        }
      });
    }
    else {
      final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
      boolean fromQuickSearch = focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
      if (fromQuickSearch) {
        ChooseByNameBase.JPanelProvider panelProvider = (ChooseByNameBase.JPanelProvider)focusedComponent.getParent();
        panelProvider.registerHint(windowEvent.asPopup());
      }
    }
  }
}

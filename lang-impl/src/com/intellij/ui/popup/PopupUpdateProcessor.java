package com.intellij.ui.popup;

import com.intellij.codeInsight.lookup.*;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.wm.ex.WindowManagerEx;

import java.awt.*;

/**
 * @author yole
 */
public abstract class PopupUpdateProcessor extends JBPopupAdapter {

  public abstract void updatePopup(Object lookupItemObject);

  public void beforeShown(final Project project, final JBPopup jbPopup) {
    final Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null) {
      activeLookup.addLookupListener(new LookupAdapter() {
        public void currentItemChanged(LookupEvent event) {
          if (jbPopup.isVisible()) { //was not canceled yet
            final LookupElement item = event.getItem();
            if (item != null) {
              jbPopup.cancel(); //close this one
              updatePopup(item.getObject()); //open next
            }
          }
          activeLookup.removeLookupListener(this); //do not multiply listeners
        }
      });
    }
    else {
      final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
      boolean fromQuickSearch = focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
      if (fromQuickSearch) {
        ChooseByNameBase.JPanelProvider panelProvider = (ChooseByNameBase.JPanelProvider)focusedComponent.getParent();
        panelProvider.registerHint(jbPopup);
      }
    }
  }
}

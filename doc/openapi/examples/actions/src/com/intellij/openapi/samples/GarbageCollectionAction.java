package com.intellij.openapi.samples;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public class GarbageCollectionAction extends AnAction {
  private ImageIcon myIcon;

  public GarbageCollectionAction() {
    super("GC", "Run garbage collection", null);
  }

  public void actionPerformed(AnActionEvent event) {
    System.gc();
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
      if (myIcon == null) {
        java.net.URL resource = GarbageCollectionAction.class.getResource("/icons/garbage.png");
        myIcon = new ImageIcon(resource);
      }
      presentation.setIcon(myIcon);
    }
  }
}

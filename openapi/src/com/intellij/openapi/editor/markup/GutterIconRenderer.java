/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;

import javax.swing.*;

/**
 * @author max
 */
public abstract class GutterIconRenderer {
  public abstract Icon getIcon();

  public ActionGroup getPopupMenuActions() {
    return null;
  }

  public String getTooltipText() {
    return null;
  }

  public AnAction getClickAction() {
    return null;
  }

  public AnAction getMiddleButtonClickAction() {
    return null;
  }

  public boolean isNavigateAction() {
    return false;
  }

  public Alignment getAlignment() {
    return Alignment.CENTER;
  }

  public GutterDraggableObject getDraggableObject() {
    return null;
  }

  public static class Alignment {
    public static final Alignment LEFT = new Alignment("LEFT", 1);
    public static final Alignment RIGHT = new Alignment("RIGHT", 3);
    public static final Alignment CENTER = new Alignment("CENTER", 2);

    private final String myName;
    private int myWeight;

    private Alignment(String name, int weight) {
      myName = name;
      myWeight = weight;
    }

    public int getWeight() {
      return myWeight;
    }

    public String toString() {
      return myName;
    }
  }
}

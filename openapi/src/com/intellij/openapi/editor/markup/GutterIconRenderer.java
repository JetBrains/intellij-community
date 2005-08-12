/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

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

    private Alignment(@NonNls String name, int weight) {
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

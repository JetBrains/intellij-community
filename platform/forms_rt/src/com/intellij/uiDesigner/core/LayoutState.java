/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.core;

import java.awt.*;
import java.util.ArrayList;

/**
 * @noinspection unchecked
 */
public final class LayoutState {
  private final Component[] myComponents;
  private final GridConstraints[] myConstraints;
  private final int myColumnCount;
  private final int myRowCount;
  final Dimension[] myPreferredSizes;
  final Dimension[] myMinimumSizes;

  public LayoutState(final GridLayoutManager layout, final boolean ignoreInvisibleComponents) {
    // collect all visible components
    final ArrayList componentsList = new ArrayList(layout.getComponentCount());
    final ArrayList constraintsList = new ArrayList(layout.getComponentCount());
    for (int i=0; i < layout.getComponentCount(); i++){
      final Component component = layout.getComponent(i);
      if (!ignoreInvisibleComponents || component.isVisible()) {
        componentsList.add(component);
        final GridConstraints constraints = layout.getConstraints(i);
        constraintsList.add(constraints);
      }
    }

    myComponents = (Component[])componentsList.toArray(new Component[componentsList.size()]);
    myConstraints = (GridConstraints[])constraintsList.toArray(new GridConstraints[constraintsList.size()]);

    myMinimumSizes = new Dimension[myComponents.length];
    myPreferredSizes = new Dimension[myComponents.length];

    myColumnCount = layout.getColumnCount();
    myRowCount = layout.getRowCount();
  }

  public int getComponentCount(){
    return myComponents.length;
  }

  public Component getComponent(final int index){
    return myComponents[index];
  }

  public GridConstraints getConstraints(final int index){
    return myConstraints[index];
  }

  public int getColumnCount(){
    return myColumnCount;
  }

  public int getRowCount(){
    return myRowCount;
  }
}

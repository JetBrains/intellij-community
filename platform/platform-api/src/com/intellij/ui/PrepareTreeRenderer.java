/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/12/12
 * Time: 4:40 PM
 */
public class PrepareTreeRenderer {
/*  public static void prepare(final JTree tree, final JComponent component, final boolean selected, final boolean hasFocus) {
    final boolean treeFocused = tree.hasFocus();
    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isFullRowSelectionLAF()) {
        component.setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
    }
    else if (UIUtil.isUnderAquaLookAndFeel() && tree.getUI() instanceof MacTreeUI && ((MacTreeUI)tree.getUI()).isWideSelection()) {
      component.setPaintFocusBorder(false);
      //setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
    }
    else {
      if (selected) {
        component.setPaintFocusBorder(true);
        if (treeFocused) {
          component.setBackground(UIUtil.getTreeSelectionBackground());
        }
        else {
          component.setBackground(null);
        }
      }
      else {
        component.setBackground(null);
      }
    }

    component.setForeground(tree.getForeground());
    component.setIcon(null);

    if (UIUtil.isUnderGTKLookAndFeel()){
      component.setOpaque(false);  // avoid nasty background
      component.setIconOpaque(false);
    }
    else if (UIUtil.isUnderNimbusLookAndFeel() && selected && hasFocus) {
      component.setOpaque(false);  // avoid erasing Nimbus focus frame
      component.setIconOpaque(false);
    }
    else if (UIUtil.isUnderAquaLookAndFeel() && tree.getUI() instanceof MacTreeUI && ((MacTreeUI)tree.getUI()).isWideSelection()) {
      component.setOpaque(false);  // avoid erasing Nimbus focus frame
      component.setIconOpaque(false);
    }
    else {
      component.setOpaque(myOpaque || selected && hasFocus || selected && treeFocused); // draw selection background even for non-opaque tree
    }

    if (tree.getUI() instanceof MacTreeUI) {
      component.setMyBorder(null);
      component.setIpad(new Insets(0, 2,  0, 2));
    }

    if (component.getFont() == null) {
      component.setFont(tree.getFont());
    }
  }      */
}

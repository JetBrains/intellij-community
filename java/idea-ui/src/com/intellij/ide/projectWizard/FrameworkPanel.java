/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportOptionsComponent;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 *         Date: 09.09.13
 */
class FrameworkPanel extends JPanel {

  private final FrameworkSupportInModuleProvider myFramework;
  private final FrameworkSupportModelBase myModel;
  private JComponent myComponent;

  public FrameworkPanel(final FrameworkSupportInModuleProvider framework, FrameworkSupportModelBase model) {
    super(new BorderLayout());
    myFramework = framework;
    myModel = model;
    String title = framework.getPresentableName();
    final JBCheckBox checkBox = new JBCheckBox(title);
    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectFramework(checkBox.isSelected());
      }
    });
    add(checkBox, BorderLayout.NORTH);
  }

  private void selectFramework(boolean selected) {
    if (selected) {
      addComponent();
    }
    else {
      removeComponent();
    }
    revalidate();
    repaint();
  }

  private void removeComponent() {
    if (myComponent != null) {
      remove(myComponent);
    }
  }

  protected void addComponent() {
    myComponent = createComponent();
    if (myComponent != null) {
      myComponent.setBorder(IdeBorderFactory.createEmptyBorder(0, 20, 0, 0));
      add(myComponent, BorderLayout.CENTER);
    }
  }

  private JPanel createComponent() {
    FrameworkSupportInModuleConfigurable configurable = myFramework.createConfigurable(myModel);
    configurable.onFrameworkSelectionChanged(true);
    FrameworkSupportOptionsComponent component =
      new FrameworkSupportOptionsComponent(myModel, myModel.getLibrariesContainer(), configurable, myFramework, configurable);
    return component.getMainPanel();
  }

  static class HeaderPanel extends FrameworkPanel {

    public HeaderPanel(FrameworkSupportInModuleProvider framework,
                       FrameworkSupportModelBase model) {
      super(framework, model);
      addComponent();
    }
  }
}

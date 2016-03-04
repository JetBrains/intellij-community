/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * User: anna
 * Date: 26-May-2006
 */
public abstract class NamedConfigurable<T> implements Configurable {
  private JTextField myNameField;
  private JPanel myNamePanel;
  private JPanel myWholePanel;
  private JPanel myOptionsPanel;
  private JPanel myTopRightPanel;
  private JComponent myOptionsComponent;
  private boolean myNameEditable;

  protected NamedConfigurable() {
    this(false, null);
  }

  protected NamedConfigurable(boolean isNameEditable, @Nullable final Runnable updateTree) {
    myNameEditable = isNameEditable;
    myNamePanel.setVisible(myNameEditable);
    if (myNameEditable) {
      myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          setDisplayName(myNameField.getText());
          if (updateTree != null){
            updateTree.run();
          }
        }
      });
    }
    myNamePanel.setBorder(new EmptyBorder(10, 10, 6, 10));
  }

  public boolean isNameEditable() {
    return myNameEditable;
  }

  public void setNameFieldShown(boolean shown) {
    if (myNamePanel.isVisible() == shown) return;

    myNamePanel.setVisible(shown);
    myWholePanel.revalidate();
    myWholePanel.repaint();
  }

  public abstract void setDisplayName(String name);
  public abstract T getEditableObject();
  public abstract String getBannerSlogan();

  public final JComponent createComponent() {
    if (myOptionsComponent == null){
      myOptionsComponent = createOptionsPanel();
      final JComponent component = createTopRightComponent();
      if (component == null) {
        myTopRightPanel.setVisible(false);
      }
      else {
        myTopRightPanel.add(component, BorderLayout.CENTER);
      }
    }
    if (myOptionsComponent != null) {
      myOptionsPanel.add(myOptionsComponent, BorderLayout.CENTER);
    }
    else {
      Logger.getInstance(getClass().getName()).error("Options component is null for "+getClass());
    }
    updateName();
    return myWholePanel;
  }

  @Nullable
  protected JComponent createTopRightComponent() {
    return null;
  }

  protected void resetOptionsPanel() {
    myOptionsComponent = null;
    myOptionsPanel.removeAll();
  }

  public void updateName() {
    myNameField.setText(getDisplayName());
  }

  public abstract JComponent createOptionsPanel();

  @Nullable
  public Icon getIcon(boolean expanded) {
    return null;
  }
}

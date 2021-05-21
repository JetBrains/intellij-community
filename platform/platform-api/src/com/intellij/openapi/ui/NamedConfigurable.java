// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public abstract class NamedConfigurable<T> implements Configurable {
  private JTextField myNameField;
  private JPanel myNamePanel;
  private JPanel myWholePanel;
  private JPanel myOptionsPanel;
  private JPanel myTopRightPanel;
  private ErrorLabel myErrorLabel;
  private JComponent myOptionsComponent;
  private final boolean myNameEditable;
  @Nullable private final Runnable myUpdateTree;

  protected NamedConfigurable() {
    this(false, null);
  }

  protected NamedConfigurable(boolean isNameEditable, @Nullable final Runnable updateTree) {
    myNameEditable = isNameEditable;
    myUpdateTree = updateTree;
  }

  /**
   * This is a fake constructor which is needed to ensure that UI Form compiler won't insert calls of {@link #$$$setupUI$$$} method to other
   * constructors.
   */
  @SuppressWarnings("unused")
  private NamedConfigurable(boolean fake) {
    this();
    $$$setupUI$$$();
  }

  public boolean isNameEditable() {
    return myNameEditable;
  }

  public void setNameFieldShown(boolean shown) {
    ensureUiInitialized();
    if (myNamePanel.isVisible() == shown) return;

    myNamePanel.setVisible(shown);
    myWholePanel.revalidate();
    myWholePanel.repaint();
  }

  public abstract void setDisplayName(@NlsSafe String name);
  public abstract T getEditableObject();
  public abstract @NlsContexts.DetailedDescription String getBannerSlogan();

  @Override
  public final JComponent createComponent() {
    ensureUiInitialized();
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
      Logger.getInstance(getClass().getName()).error("Options component is null for " + getClass());
    }
    updateName();
    return myWholePanel;
  }

  private void ensureUiInitialized() {
    if (myWholePanel == null) {
      $$$setupUI$$$();
      myNamePanel.setVisible(myNameEditable);
      if (myNameEditable) {
        myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            @NlsSafe String name = myNameField.getText().trim();
            try {
              checkName(name);
              myErrorLabel.setErrorText(null, null);
              setDisplayName(name);
              if (myUpdateTree != null) {
                myUpdateTree.run();
              }
            }
            catch (ConfigurationException exc) {
              myErrorLabel.setErrorText(exc.getMessage(), JBColor.RED);
            }
          }
        });
      }
      myNamePanel.setBorder(JBUI.Borders.empty(10, 10, 6, 10));
    }
  }

  private void $$$setupUI$$$() {
  }

  protected void checkName(@NonNls @NotNull String name) throws ConfigurationException {
    if (name.isEmpty()) {
      throw new ConfigurationException(IdeBundle.message("error.name.cannot.be.empty"));
    }
  }

  @Nullable
  protected JComponent createTopRightComponent() {
    return null;
  }

  protected void resetOptionsPanel() {
    ensureUiInitialized();
    myOptionsComponent = null;
    myOptionsPanel.removeAll();
  }

  public void updateName() {
    ensureUiInitialized();
    myNameField.setText(getDisplayName());
  }

  public abstract JComponent createOptionsPanel();

  @Nullable
  public Icon getIcon(boolean expanded) {
    return null;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}

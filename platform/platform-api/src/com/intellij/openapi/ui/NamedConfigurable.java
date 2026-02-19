// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.ResourceBundle;

public abstract class NamedConfigurable<T> implements Configurable {
  private JTextField myNameField;
  private JPanel myNamePanel;
  private JPanel myWholePanel;
  private JPanel myOptionsPanel;
  private JPanel myTopRightPanel;
  private ErrorLabel myErrorLabel;
  private JComponent myOptionsComponent;
  private final boolean myNameEditable;
  private final @Nullable Runnable myUpdateTree;
  private boolean myUpdatingNameFieldFromDisplayName;

  protected NamedConfigurable() {
    this(false, null);
  }

  protected NamedConfigurable(boolean isNameEditable, final @Nullable Runnable updateTree) {
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
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(@NonNls String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myWholePanel; }

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
    if (myOptionsComponent == null) {
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

  private void $$$setupUI$$$() {
    myWholePanel = new JPanel();
    myWholePanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myOptionsPanel = new JPanel();
    myOptionsPanel.setLayout(new BorderLayout(0, 0));
    myWholePanel.add(myOptionsPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         null, null, null, 0, false));
    myNamePanel = new JPanel();
    myNamePanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myWholePanel.add(myNamePanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myErrorLabel = new ErrorLabel();
    this.$$$loadLabelText$$$(myErrorLabel, this.$$$getMessageFromBundle$$$("messages/CommonBundle", "name.label.text"));
    myNamePanel.add(myErrorLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                      null, 0, false));
    myNameField = new JTextField();
    myNamePanel.add(myNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                     new Dimension(150, -1), null, 0, false));
    myTopRightPanel = new JPanel();
    myTopRightPanel.setLayout(new BorderLayout(0, 0));
    myNamePanel.add(myTopRightPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         null, null, null, 0, false));
    myErrorLabel.setLabelFor(myNameField);
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
              if (!isUpdatingNameFieldFromDisplayName()) {
                setDisplayName(name);
                if (myUpdateTree != null) {
                  myUpdateTree.run();
                }
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

  protected void checkName(@NonNls @NotNull String name) throws ConfigurationException {
    if (name.isEmpty()) {
      throw new ConfigurationException(IdeBundle.message("error.name.cannot.be.empty"));
    }
  }

  protected @Nullable JComponent createTopRightComponent() {
    return null;
  }

  protected void resetOptionsPanel() {
    ensureUiInitialized();
    myOptionsComponent = null;
    myOptionsPanel.removeAll();
  }

  public void updateName() {
    myUpdatingNameFieldFromDisplayName = true;
    try {
      ensureUiInitialized();
      myNameField.setText(getDisplayName());
    }
    finally {
      myUpdatingNameFieldFromDisplayName = false;
    }
  }

  protected boolean isUpdatingNameFieldFromDisplayName() {
    return myUpdatingNameFieldFromDisplayName;
  }

  public abstract JComponent createOptionsPanel();

  public @Nullable Icon getIcon(boolean expanded) {
    return null;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }
}

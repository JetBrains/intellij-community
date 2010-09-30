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

package com.intellij.ui.tabs;

import com.intellij.notification.impl.ui.StickyButton;
import com.intellij.notification.impl.ui.StickyButtonUI;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class FileColorConfigurationEditDialog extends DialogWrapper {
  private FileColorConfiguration myConfiguration;
  private JComboBox myScopeComboBox;
  private final FileColorManagerImpl myManager;
  private HashMap<String,AbstractButton> myColorToButtonMap;
  private static final String CUSTOM_COLOR_NAME = "Custom";
  private final Map<String, NamedScope> myScopeNames = new HashMap<String, NamedScope>();

  public FileColorConfigurationEditDialog(@NotNull final FileColorManagerImpl manager, @Nullable final FileColorConfiguration configuration) {
    super(true);

    setTitle(configuration == null ? "Add color label" : "Edit color label");
    setResizable(false);

    myManager = manager;
    myConfiguration = configuration;

    init();
    updateCustomButton();
    updateOKButton();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    final NamedScopesHolder[] scopeHolders = NamedScopeManager.getAllNamedScopeHolders(myManager.getProject());
    for (final NamedScopesHolder scopeHolder : scopeHolders) {
      final NamedScope[] scopes = scopeHolder.getScopes();
      for (final NamedScope scope : scopes) {
        myScopeNames.put(scope.getName(), scope);
      }
    }

    myScopeComboBox = new JComboBox(ArrayUtil.toStringArray(myScopeNames.keySet()));
    myScopeComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateCustomButton();
        updateOKButton();
      }
    });

    final JPanel pathPanel = new JPanel();
    pathPanel.setLayout(new BorderLayout());

    final JLabel pathLabel = new JLabel("Scope:");
    pathLabel.setDisplayedMnemonic('S');
    pathLabel.setLabelFor(myScopeComboBox);
    pathPanel.add(pathLabel, BorderLayout.WEST);
    pathPanel.add(myScopeComboBox, BorderLayout.CENTER);

    /*
    final JButton newScope = new JButton("Add scope...");
    newScope.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        // TBD: refresh scope list
      }
    });
    pathPanel.add(newScope, BorderLayout.EAST);
    */

    result.add(pathPanel);

    final JPanel colorPanel = new JPanel();
    colorPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
    colorPanel.setLayout(new BoxLayout(colorPanel, BoxLayout.X_AXIS));
    final JLabel colorLabel = new JLabel("Color:");
    colorPanel.add(colorLabel);
    colorPanel.add(createColorButtonsPanel(myConfiguration));
    colorPanel.add(Box.createHorizontalGlue());
    result.add(colorPanel);

    return result;
  }

  private void updateCustomButton() {
    final Object item = myScopeComboBox.getSelectedItem();
    if (item instanceof String) {
      @SuppressWarnings({"SuspiciousMethodCalls"})
      final Color color = ColorUtil.getColor(myScopeNames.get(item).getClass());
      final CustomColorButton button = (CustomColorButton)myColorToButtonMap.get(CUSTOM_COLOR_NAME);
      if (color != null) {
        button.setColor(color);
        button.setSelected(true);
      }
    }
  }

  @Override
  protected void doOKAction() {
    close(OK_EXIT_CODE);

    if (myConfiguration != null) {
      myConfiguration.setScopeName((String) myScopeComboBox.getSelectedItem());
      myConfiguration.setColorName(getColorName());
    } else {
      myConfiguration = new FileColorConfiguration((String) myScopeComboBox.getSelectedItem(), getColorName());
    }
  }

  public FileColorConfiguration getConfiguration() {
    return myConfiguration;
  }

  private JComponent createColorButtonsPanel(final FileColorConfiguration configuration) {
    final JPanel result = new JPanel(new BorderLayout());
    result.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    final JPanel inner = new JPanel();
    inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
    inner.setBorder(
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
    inner.setBackground(Color.WHITE);
    result.add(inner, BorderLayout.CENTER);

    final ButtonGroup group = new ButtonGroup();

    myColorToButtonMap = new HashMap<String, AbstractButton>();

    final Collection<String> names = myManager.getColorNames();
    for (final String name : names) {
      final ColorButton colorButton = new ColorButton(name, myManager.getColor(name));
      group.add(colorButton);
      inner.add(colorButton);
      myColorToButtonMap.put(name, colorButton);
      inner.add(Box.createHorizontalStrut(5));
    }
    final CustomColorButton customButton = new CustomColorButton();
    group.add(customButton);
    inner.add(customButton);
    myColorToButtonMap.put(customButton.getText(), customButton);
    inner.add(Box.createHorizontalStrut(5));


    if (configuration != null) {
      final AbstractButton button = myColorToButtonMap.get(configuration.getColorName());
      if (button != null) {
        button.setSelected(true);
      }
    }

    return result;
  }

  @Nullable
  private String getColorName() {
    for (String name : myColorToButtonMap.keySet()) {
      final AbstractButton button = myColorToButtonMap.get(name);
      if (button.isSelected()) {
        return button instanceof CustomColorButton ? ColorUtil.toHex(((CustomColorButton)button).getColor()) : name;
      }
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myScopeComboBox;
  }

  private void updateOKButton() {
    getOKAction().setEnabled(isOKActionEnabled());
  }

  @Override
  public boolean isOKActionEnabled() {
    final String scopeName = (String) myScopeComboBox.getSelectedItem();
    return scopeName != null && scopeName.length() > 0 && getColorName() != null;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  private class ColorButton extends StickyButton {
    protected Color myColor;

    protected ColorButton(final String text, final Color color) {
      super(text);
      setUI(new ColorButtonUI());
      myColor = color;
      addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doPerformAction(e);
        }
      });
      setBackground(Color.WHITE);
      setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }

    protected void doPerformAction(ActionEvent e) {
      updateOKButton();
    }

    Color getColor() {
      return myColor;
    }

    public void setColor(Color color) {
      myColor = color;
    }

    @Override
    public Color getForeground() {
      if (getModel().isSelected()) {
        return Color.BLACK;
      } else if (getModel().isRollover()) {
        return Color.GRAY;
      } else {
        return getColor();
      }
    }

    @Override
    protected ButtonUI createUI() {
      return new ColorButtonUI();
    }
  }

  private class CustomColorButton extends ColorButton {
    private CustomColorButton() {
      super(CUSTOM_COLOR_NAME, Color.WHITE);
      myColor = null;
    }

    @Override
    protected void doPerformAction(ActionEvent e) {
      final Color color = ColorChooser.chooseColor(FileColorConfigurationEditDialog.this.getRootPane(), "Choose Color", myColor);
      if (color != null) {
          myColor = color;
      }
      setSelected(myColor != null);
      getOKAction().setEnabled(myColor != null);
    }

    @Override
    public Color getForeground() {
      return getModel().isSelected() ? Color.BLACK : Color.GRAY;
    }

    @Override
    Color getColor() {
      return myColor == null ? Color.WHITE : myColor;
    }
  }

  private class ColorButtonUI extends StickyButtonUI<ColorButton> {

    @Override
    protected Color getBackgroundColor(final ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getFocusColor(ColorButton button) {
      return button.getColor().darker();
    }

    @Override
    protected Color getSelectionColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getRolloverColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected int getArcSize() {
      return 20;
    }
  }
}

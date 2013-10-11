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

package com.intellij.ui.tabs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class FileColorConfigurationEditDialog extends DialogWrapper {
  private FileColorConfiguration myConfiguration;
  private JComboBox myScopeComboBox;
  private final FileColorManager myManager;
  private final ColorSelectionComponent myColorSelectionComponent;

  private final Map<String, NamedScope> myScopeNames = new HashMap<String, NamedScope>();

  public FileColorConfigurationEditDialog(@NotNull final FileColorManager manager, @Nullable final FileColorConfiguration configuration) {
    super(true);

    setTitle(configuration == null ? "Add Color Label" : "Edit Color Label");
    setResizable(false);

    myManager = manager;
    myConfiguration = configuration;
    myColorSelectionComponent = new ColorSelectionComponent();
    myColorSelectionComponent.initDefault(manager, configuration == null ? null : configuration.getColorName());
    myColorSelectionComponent.setChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateOKButton();
      }
    });

    init();
    updateCustomButton();
    if (myConfiguration != null && !StringUtil.isEmpty(myConfiguration.getScopeName())) {
      myScopeComboBox.setSelectedItem(myConfiguration.getScopeName());
    }
    updateOKButton();
  }

  public JComboBox getScopeComboBox() {
    return myScopeComboBox;
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel result = new JPanel();
    result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

    final List<NamedScope> scopeList = new ArrayList<NamedScope>();
    final Project project = myManager.getProject();
    final NamedScopesHolder[] scopeHolders = NamedScopeManager.getAllNamedScopeHolders(project);
    for (final NamedScopesHolder scopeHolder : scopeHolders) {
      final NamedScope[] scopes = scopeHolder.getScopes();
      Collections.addAll(scopeList, scopes);
    }
    CustomScopesProviderEx.filterNoSettingsScopes(project, scopeList);
    for (final NamedScope scope : scopeList) {
      myScopeNames.put(scope.getName(), scope);
    }

    myScopeComboBox = new JComboBox(ArrayUtil.toStringArray(myScopeNames.keySet()));
    myScopeComboBox.addActionListener(new ActionListener() {
      @Override
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
    colorPanel.add(myColorSelectionComponent);
    colorPanel.add(Box.createHorizontalGlue());
    result.add(colorPanel);

    return result;
  }

  private void updateCustomButton() {
    final Object item = myScopeComboBox.getSelectedItem();
    if (item instanceof String) {
      Color color = myConfiguration == null ? null : ColorUtil.fromHex(myConfiguration.getColorName(), null);
      if (color == null) {
        color = ColorUtil.getColor(myScopeNames.get(item).getClass());
      }
      if (color != null) myColorSelectionComponent.setCustomButtonColor(color);
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

  @Nullable
  private String getColorName() {
    return myColorSelectionComponent.getSelectedColorName();
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

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }
}

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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.ui.RegistryCheckBox;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.classFilter.ClassFilterEditor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Eugene Belyaev
 */
public class DebuggerDataViewsConfigurable implements SearchableConfigurable {
  private JCheckBox myCbAutoscroll;
  private JCheckBox myCbShowSyntheticFields;
  private JCheckBox myCbSort;
  private JCheckBox myCbHideNullArrayElements;
  private JCheckBox myCbShowStatic;
  private JCheckBox myCbShowDeclaredType;
  private JCheckBox myCbShowObjectId;

  private StateRestoringCheckBox myCbShowStaticFinalFields;
  private final ArrayRendererConfigurable myArrayRendererConfigurable;
  private JCheckBox myCbEnableAlternateViews;

  private JCheckBox myCbEnableToString;
  private JRadioButton myRbAllThatOverride;
  private JRadioButton myRbFromList;
  private ClassFilterEditor myToStringFilterEditor;
  private JTextField myValueTooltipDelayField;
  
  private final Project myProject;
  private RegistryCheckBox myAutoTooltip;

  public DebuggerDataViewsConfigurable(Project project) {
    myProject = project;
    myArrayRendererConfigurable = new ArrayRendererConfigurable(NodeRendererSettings.getInstance().getArrayRenderer());
  }

  public void disposeUIResources() {
    myArrayRendererConfigurable.disposeUIResources();
  }

  public String getDisplayName() {
    return DebuggerBundle.message("base.renderer.configurable.display.name");
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());

    myCbAutoscroll = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.autoscroll"));
    myCbShowSyntheticFields = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.synthetic.fields"));
    myCbSort = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.sort.alphabetically"));
    myCbHideNullArrayElements = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.hide.null.array.elements"));
    myCbShowStatic = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.static.fields"));
    myCbShowStaticFinalFields = new StateRestoringCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.static.final.fields"));
    myCbEnableAlternateViews = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.alternate.view"));
    myCbShowStatic.addChangeListener(new ChangeListener(){
      public void stateChanged(ChangeEvent e) {
        if(myCbShowStatic.isSelected()) {
          myCbShowStaticFinalFields.makeSelectable();
        }
        else {
          myCbShowStaticFinalFields.makeUnselectable(false);
        }
      }
    });
    myCbShowDeclaredType = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.declared.type"));
    myCbShowObjectId = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.show.object.id"));

    myCbEnableToString = new JCheckBox(DebuggerBundle.message("label.base.renderer.configurable.enable.tostring"));
    myRbAllThatOverride = new JRadioButton(DebuggerBundle.message("label.base.renderer.configurable.all.overridding"));
    myRbFromList = new JRadioButton(DebuggerBundle.message("label.base.renderer.configurable.classes.from.list"));
    ButtonGroup group = new ButtonGroup();
    group.add(myRbAllThatOverride);
    group.add(myRbFromList);
    myToStringFilterEditor = new ClassFilterEditor(myProject);
    myCbEnableToString.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        final boolean enabled = myCbEnableToString.isSelected();
        myRbAllThatOverride.setEnabled(enabled);
        myRbFromList.setEnabled(enabled);
        myToStringFilterEditor.setEnabled(enabled && myRbFromList.isSelected());
      }
    });
    myRbFromList.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myToStringFilterEditor.setEnabled(myCbEnableToString.isSelected() && myRbFromList.isSelected());
      }
    });

    panel.add(myCbSort, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));
    panel.add(myCbAutoscroll, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));


    myAutoTooltip = new RegistryCheckBox(Registry.get("debugger.valueTooltipAutoShow"),
                                         DebuggerBundle.message("label.base.renderer.configurable.autoTooltip"),
                                         DebuggerBundle.message("label.base.renderer.configurable.autoTooltip.description",
                                                                Registry.stringValue("ide.forcedShowTooltip")));
    panel.add(myAutoTooltip, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));

    final JLabel tooltipLabel = new JLabel(DebuggerBundle.message("label.debugger.general.configurable.tooltips.delay"));
    panel.add(tooltipLabel, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
    myValueTooltipDelayField = new JTextField(10);
    myValueTooltipDelayField.setMinimumSize(new Dimension(50, myValueTooltipDelayField.getPreferredSize().height));
    panel.add(myValueTooltipDelayField, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));
    tooltipLabel.setLabelFor(myValueTooltipDelayField);

    final JPanel showPanel = new JPanel(new GridBagLayout());
    showPanel.setBorder(BorderFactory.createTitledBorder("Show"));

    showPanel.add(myCbShowDeclaredType, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
    showPanel.add(myCbShowObjectId, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
    showPanel.add(myCbShowSyntheticFields, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 10, 0, 0), 0, 0));
    showPanel.add(myCbShowStatic, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 10, 0, 0), 0, 0));
    showPanel.add(myCbShowStaticFinalFields, new GridBagConstraints(2, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 10, 0, 0), 0, 0));

    panel.add(showPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(4, 6, 0, 0), 0, 0));

    final JPanel arraysPanel = new JPanel(new BorderLayout());
    final JComponent arraysComponent = myArrayRendererConfigurable.createComponent();
    arraysComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    arraysPanel.add(arraysComponent, BorderLayout.CENTER);
    arraysPanel.add(myCbHideNullArrayElements, BorderLayout.SOUTH);
    arraysPanel.setBorder(BorderFactory.createTitledBorder("Arrays"));
    panel.add(arraysPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(4, 6, 0, 0), 0, 0));

    panel.add(myCbEnableAlternateViews, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 6, 0, 10), 0, 0));
    // starting 4-th row
    panel.add(myCbEnableToString, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));
    panel.add(myRbAllThatOverride, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 12, 0, 0), 0, 0));
    panel.add(myRbFromList, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 12, 0, 0), 0, 0));
    panel.add(myToStringFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 12, 0, 0), 0, 0));

    return panel;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    try {
      DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY = Integer.parseInt(myValueTooltipDelayField.getText().trim());
    }
    catch (NumberFormatException e) {
    }
    generalSettings.AUTOSCROLL_TO_NEW_LOCALS  = myCbAutoscroll.isSelected();
    rendererSettings.setAlternateCollectionViewsEnabled(myCbEnableAlternateViews.isSelected());
    generalSettings.HIDE_NULL_ARRAY_ELEMENTS  = myCbHideNullArrayElements.isSelected();

    final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
    classRenderer.SORT_ASCENDING = myCbSort.isSelected();
    classRenderer.SHOW_STATIC = myCbShowStatic.isSelected();
    classRenderer.SHOW_STATIC_FINAL = myCbShowStaticFinalFields.isSelectedWhenSelectable();
    classRenderer.SHOW_SYNTHETICS = myCbShowSyntheticFields.isSelected();
    classRenderer.SHOW_DECLARED_TYPE = myCbShowDeclaredType.isSelected();
    classRenderer.SHOW_OBJECT_ID = myCbShowObjectId.isSelected();

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    toStringRenderer.setEnabled(myCbEnableToString.isSelected());
    toStringRenderer.setUseClassFilters(myRbFromList.isSelected());
    toStringRenderer.setClassFilters(myToStringFilterEditor.getFilters());

    myAutoTooltip.save();

    myArrayRendererConfigurable.apply();
  }

  public void reset() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    myValueTooltipDelayField.setText(Integer.toString(DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY));
    myCbAutoscroll.setSelected(generalSettings.AUTOSCROLL_TO_NEW_LOCALS);
    myCbHideNullArrayElements.setSelected(generalSettings.HIDE_NULL_ARRAY_ELEMENTS);
    myCbEnableAlternateViews.setSelected(rendererSettings.areAlternateCollectionViewsEnabled());

    ClassRenderer classRenderer = rendererSettings.getClassRenderer();

    myCbShowSyntheticFields.setSelected(classRenderer.SHOW_SYNTHETICS);
    myCbSort.setSelected(classRenderer.SORT_ASCENDING);
    myCbShowStatic.setSelected(classRenderer.SHOW_STATIC);
    myCbShowStaticFinalFields.setSelected(classRenderer.SHOW_STATIC_FINAL);
    if(!classRenderer.SHOW_STATIC) {
      myCbShowStaticFinalFields.makeUnselectable(false);
    }
    myCbShowDeclaredType.setSelected(classRenderer.SHOW_DECLARED_TYPE);
    myCbShowObjectId.setSelected(classRenderer.SHOW_OBJECT_ID);

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    final boolean toStringEnabled = toStringRenderer.isEnabled();
    final boolean useClassFilters = toStringRenderer.isUseClassFilters();
    myCbEnableToString.setSelected(toStringEnabled);
    myRbAllThatOverride.setSelected(!useClassFilters);
    myRbFromList.setSelected(useClassFilters);
    myToStringFilterEditor.setFilters(toStringRenderer.getClassFilters());
    myToStringFilterEditor.setEnabled(toStringEnabled && useClassFilters);
    myRbFromList.setEnabled(toStringEnabled);
    myRbAllThatOverride.setEnabled(toStringEnabled);

    myArrayRendererConfigurable.reset();
  }

  public boolean isModified() {
    return areGeneralSettingsModified() || areDefaultRenderersModified() || areDebuggerSettingsModified();
  }

  private boolean areDebuggerSettingsModified() {
    try {
      return DebuggerSettings.getInstance().VALUE_LOOKUP_DELAY != Integer.parseInt(myValueTooltipDelayField.getText().trim());
    }
    catch (NumberFormatException e) {
    }
    return false;
  }

  private boolean areGeneralSettingsModified() {
    ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    return
    (generalSettings.AUTOSCROLL_TO_NEW_LOCALS  != myCbAutoscroll.isSelected()) ||
    (generalSettings.HIDE_NULL_ARRAY_ELEMENTS  != myCbHideNullArrayElements.isSelected()) || myAutoTooltip.isChanged();
  }

  private boolean areDefaultRenderersModified() {
    if (myArrayRendererConfigurable.isModified()) {
      return true;
    }
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
    final boolean isClassRendererModified=
    (classRenderer.SORT_ASCENDING != myCbSort.isSelected()) ||
    (classRenderer.SHOW_STATIC != myCbShowStatic.isSelected()) ||
    (classRenderer.SHOW_STATIC_FINAL != myCbShowStaticFinalFields.isSelectedWhenSelectable()) ||
    (classRenderer.SHOW_SYNTHETICS != myCbShowSyntheticFields.isSelected()) ||
    (classRenderer.SHOW_DECLARED_TYPE != myCbShowDeclaredType.isSelected()) ||
    (classRenderer.SHOW_OBJECT_ID != myCbShowObjectId.isSelected());
    if (isClassRendererModified) {
      return true;
    }

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    final boolean isToStringRendererModified =
      (toStringRenderer.isEnabled() != myCbEnableToString.isSelected()) ||
      (toStringRenderer.isUseClassFilters() != myRbFromList.isSelected()) ||
      (!DebuggerUtilsEx.filterEquals(toStringRenderer.getClassFilters(), myToStringFilterEditor.getFilters()));
    if (isToStringRendererModified) {
      return true;
    }

    if (rendererSettings.areAlternateCollectionViewsEnabled() != myCbEnableAlternateViews.isSelected()) {
      return true;
    }

    return false;
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.dataviews";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }
}

package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
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
public class BaseRenderersConfigurable implements Configurable {
  private JCheckBox myCbAutoscroll;
  private JCheckBox myCbShowSyntheticFields;
  private JCheckBox myCbSort;
  private JCheckBox myCbHideNullArrayElements;
  private JCheckBox myCbShowStatic;
  private JCheckBox myCbShowDeclaredType;
  private JCheckBox myCbShowObjectId;

  private StateRestoringCheckBox myCbShowStaticFinalFields;
  private ArrayRendererConfigurable myArrayRendererConfigurable;
  private JCheckBox myCbEnableAlternateViews;

  private JCheckBox myCbEnableToString;
  private JRadioButton myRbAllThatOverride;
  private JRadioButton myRbFromList;
  private ClassFilterEditor myToStringFilterEditor;
  private final Project myProject;

  public BaseRenderersConfigurable(Project project) {
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
    myCbShowDeclaredType = new JCheckBox("Show declared type");
    myCbShowObjectId = new JCheckBox("Show object id");

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

    panel.add(myCbSort, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 4, 0, 10), 0, 0));
    panel.add(myCbAutoscroll, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 4, 0, 10), 0, 0));
    panel.add(myCbEnableAlternateViews, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 2, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 10), 0, 0));

    panel.add(myCbShowStatic, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 10), 0, 0));
    panel.add(myCbShowStaticFinalFields, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 12, 0, 10), 0, 0));
    panel.add(myCbShowSyntheticFields, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 2, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 10), 0, 0));

    panel.add(myCbShowDeclaredType, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 10), 0, 0));
    panel.add(myCbShowObjectId, new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 10), 0, 0));

    panel.add(myArrayRendererConfigurable.createComponent(), new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 2, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(2, 8, 0, 0), 0, 0));
    panel.add(myCbHideNullArrayElements, new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(4, 6, 0, 0), 0, 0));

    // starting 4-th row
    panel.add(myCbEnableToString, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 4, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(10, 4, 0, 0), 0, 0));
    panel.add(myRbAllThatOverride, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 4, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0));
    panel.add(myRbFromList, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 4, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 8, 0, 0), 0, 0));
    panel.add(myToStringFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 4, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 16, 0, 0), 0, 0));

    return panel;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

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

    myArrayRendererConfigurable.apply();
  }

  public void reset() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

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
    return areGeneralSettingsModified() || areDefaultRenderersModified();
  }

  private boolean areGeneralSettingsModified() {
    ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    return
    (generalSettings.AUTOSCROLL_TO_NEW_LOCALS  != myCbAutoscroll.isSelected()) ||
    (generalSettings.HIDE_NULL_ARRAY_ELEMENTS  != myCbHideNullArrayElements.isSelected());
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
    return "topicId367253";
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.PrimitiveRenderer;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static java.awt.GridBagConstraints.*;

/**
 * @author Eugene Belyaev
 */
public class DebuggerDataViewsConfigurable implements SearchableConfigurable {
  private JCheckBox myCbAutoscroll;
  private JCheckBox myCbDfaAssist;
  private JCheckBox myCbDfaAssistGrayOut;
  private JCheckBox myCbShowSyntheticFields;
  private StateRestoringCheckBox myCbShowValFieldsAsLocalVariables;
  private JCheckBox myCbHideNullArrayElements;
  private JCheckBox myCbShowStatic;
  private JCheckBox myCbShowDeclaredType;
  private JCheckBox myCbShowFQNames;
  private JCheckBox myCbShowObjectId;
  private JCheckBox myCbShowStringsType;
  private JCheckBox myCbHexValue;
  private JCheckBox myCbPopulateThrowableStack;

  private StateRestoringCheckBox myCbShowStaticFinalFields;
  //private final ArrayRendererConfigurable myArrayRendererConfigurable;
  private JCheckBox myCbEnableAlternateViews;

  private JCheckBox myCbEnableToString;
  private JRadioButton myRbAllThatOverride;
  private JRadioButton myRbFromList;
  private ClassFilterEditor myToStringFilterEditor;

  private Project myProject;

  public DebuggerDataViewsConfigurable(@Nullable Project project) {
    myProject = project;
    //myArrayRendererConfigurable = new ArrayRendererConfigurable(NodeRendererSettings.getInstance().getArrayRenderer());
  }

  @Override
  public void disposeUIResources() {
    //myArrayRendererConfigurable.disposeUIResources();
    myToStringFilterEditor = null;
    myProject = null;
  }

  @Override
  public String getDisplayName() {
    return JavaBundle.message("options.java.display.name");
  }

  @Override
  public JComponent createComponent() {
    if (myProject == null) {
      myProject = JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables();
    }
    final JPanel panel = new JPanel(new GridBagLayout());

    myCbAutoscroll = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.autoscroll"));
    myCbDfaAssist = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.dfa.assist"));
    myCbDfaAssistGrayOut = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.dfa.assist.gray.out"));
    myCbShowSyntheticFields = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.synthetic.fields"));
    myCbShowValFieldsAsLocalVariables = new StateRestoringCheckBox(
      JavaDebuggerBundle.message("label.base.renderer.configurable.show.val.fields.as.locals"));
    myCbHideNullArrayElements = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.hide.null.array.elements"));
    myCbShowStatic = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.static.fields"));
    myCbShowStaticFinalFields = new StateRestoringCheckBox(
      JavaDebuggerBundle.message("label.base.renderer.configurable.show.static.final.fields"));
    myCbEnableAlternateViews = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.alternate.view"));
    myCbShowStatic.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myCbShowStatic.isSelected()) {
          myCbShowStaticFinalFields.makeSelectable();
        }
        else {
          myCbShowStaticFinalFields.makeUnselectable(false);
        }
      }
    });
    myCbShowSyntheticFields.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        if (myCbShowSyntheticFields.isSelected()) {
          myCbShowValFieldsAsLocalVariables.makeSelectable();
        }
        else {
          myCbShowValFieldsAsLocalVariables.makeUnselectable(false);
        }
      }
    });
    myCbShowDeclaredType = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.declared.type"));
    myCbShowFQNames = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.fq.names"));
    myCbShowObjectId = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.object.id"));
    myCbHexValue = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.hex.value"));
    myCbShowStringsType = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.show.strings.type"));
    myCbPopulateThrowableStack = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.populate.throwable.stack"));

    myCbEnableToString = new JCheckBox(JavaDebuggerBundle.message("label.base.renderer.configurable.enable.toString"));
    myRbAllThatOverride = new JRadioButton(JavaDebuggerBundle.message("label.base.renderer.configurable.all.overriding"));
    myRbFromList = new JRadioButton(JavaDebuggerBundle.message("label.base.renderer.configurable.classes.from.list"));
    ButtonGroup group = new ButtonGroup();
    group.add(myRbAllThatOverride);
    group.add(myRbFromList);
    myToStringFilterEditor = new ClassFilterEditor(myProject, null, "reference.viewBreakpoints.classFilters.newPattern");
    myCbEnableToString.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        final boolean enabled = myCbEnableToString.isSelected();
        myRbAllThatOverride.setEnabled(enabled);
        myRbFromList.setEnabled(enabled);
        myToStringFilterEditor.setEnabled(enabled && myRbFromList.isSelected());
      }
    });
    myRbFromList.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myToStringFilterEditor.setEnabled(myCbEnableToString.isSelected() && myRbFromList.isSelected());
      }
    });

    panel.add(myCbAutoscroll, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsTop(4), 0, 0));
    panel.add(myCbDfaAssist, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsTop(4), 0, 0));
    panel.add(myCbDfaAssistGrayOut, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsTop(4), 0, 0));


    final JPanel showPanel = new JPanel(new GridBagLayout());
    showPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaDebuggerBundle.message("debugger.data.views.configurable.show.title")));

    showPanel.add(myCbShowDeclaredType, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBInsets.emptyInsets(), 0, 0));
    showPanel.add(myCbShowObjectId, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsTop(4), 0, 0));
    showPanel.add(myCbShowSyntheticFields, new GridBagConstraints(1, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsLeft(10), 0, 0));
    showPanel.add(myCbShowStatic, new GridBagConstraints(1, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(4, 10, 0, 0), 0, 0));
    showPanel.add(myCbShowValFieldsAsLocalVariables, new GridBagConstraints(2, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insetsLeft(10), 0, 0));
    showPanel.add(myCbShowStaticFinalFields, new GridBagConstraints(2, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(4, 10, 0, 0), 0, 0));
    showPanel.add(myCbShowFQNames, new GridBagConstraints(3, RELATIVE, 1, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsLeft(10), 0, 0));

    panel.add(showPanel, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, WEST, HORIZONTAL, JBUI.insetsTop(4), 0, 0));

    //final JPanel arraysPanel = new JPanel(new BorderLayout(0, UIUtil.DEFAULT_VGAP));
    //final JComponent arraysComponent = myArrayRendererConfigurable.createComponent();
    //assert arraysComponent != null;
    //arraysPanel.add(arraysComponent, BorderLayout.CENTER);
    //arraysPanel.add(myCbHideNullArrayElements, BorderLayout.SOUTH);
    //arraysPanel.setBorder(IdeBorderFactory.createTitledBorder("Arrays", true));
    //panel.add(arraysPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 3, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    panel.add(myCbShowStringsType, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, NORTH, HORIZONTAL, JBInsets.emptyInsets(), 0, 0));
    panel.add(myCbHexValue, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, NORTH, HORIZONTAL, JBUI.insetsTop(4), 0, 0));
    panel.add(myCbHideNullArrayElements, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, NORTH, HORIZONTAL, JBUI.insetsTop(4), 0, 0));
    panel.add(myCbPopulateThrowableStack, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, NORTH, HORIZONTAL, JBUI.insetsTop(4), 0, 0));

    panel.add(myCbEnableAlternateViews, new GridBagConstraints(0, RELATIVE, 1, 1, 0.0, 0.0, WEST, NONE, JBUI.insets(4, 0, 0, 10), 0, 0));
    // starting 4-th row
    panel.add(myCbEnableToString, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsTop(4), 0, 0));
    panel.add(myRbAllThatOverride, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsLeft(12), 0, 0));
    panel.add(myRbFromList, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 0.0, WEST, NONE, JBUI.insetsLeft(12), 0, 0));
    myToStringFilterEditor.setMinimumSize(JBUI.size(50, 100));
    panel.add(myToStringFilterEditor, new GridBagConstraints(0, RELATIVE, 3, 1, 1.0, 1.0, CENTER, BOTH, JBUI.insetsLeft(12), 0, 0));

    return panel;
  }

  @Override
  public void apply() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    generalSettings.AUTOSCROLL_TO_NEW_LOCALS = myCbAutoscroll.isSelected();
    generalSettings.USE_DFA_ASSIST = myCbDfaAssist.isSelected();
    generalSettings.USE_DFA_ASSIST_GRAY_OUT = myCbDfaAssistGrayOut.isSelected();
    rendererSettings.setAlternateCollectionViewsEnabled(myCbEnableAlternateViews.isSelected());
    generalSettings.HIDE_NULL_ARRAY_ELEMENTS = myCbHideNullArrayElements.isSelected();
    generalSettings.POPULATE_THROWABLE_STACKTRACE = myCbPopulateThrowableStack.isSelected();

    final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
    classRenderer.SHOW_STATIC = myCbShowStatic.isSelected();
    classRenderer.SHOW_STATIC_FINAL = myCbShowStaticFinalFields.isSelectedWhenSelectable();
    classRenderer.SHOW_SYNTHETICS = myCbShowSyntheticFields.isSelected();
    classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES = myCbShowValFieldsAsLocalVariables.isSelectedWhenSelectable();
    classRenderer.SHOW_DECLARED_TYPE = myCbShowDeclaredType.isSelected();
    classRenderer.SHOW_FQ_TYPE_NAMES = myCbShowFQNames.isSelected();
    classRenderer.SHOW_OBJECT_ID = myCbShowObjectId.isSelected();
    classRenderer.SHOW_STRINGS_TYPE = myCbShowStringsType.isSelected();

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    toStringRenderer.setOnDemand(!myCbEnableToString.isSelected());
    toStringRenderer.setUseClassFilters(myRbFromList.isSelected());
    toStringRenderer.setClassFilters(myToStringFilterEditor.getFilters());

    PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
    primitiveRenderer.setShowHexValue(myCbHexValue.isSelected());

    rendererSettings.fireRenderersChanged();
  }

  @Override
  public void reset() {
    final ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    myCbAutoscroll.setSelected(generalSettings.AUTOSCROLL_TO_NEW_LOCALS);
    myCbDfaAssist.setSelected(generalSettings.USE_DFA_ASSIST);
    myCbDfaAssistGrayOut.setSelected(generalSettings.USE_DFA_ASSIST_GRAY_OUT);
    myCbHideNullArrayElements.setSelected(generalSettings.HIDE_NULL_ARRAY_ELEMENTS);
    myCbEnableAlternateViews.setSelected(rendererSettings.areAlternateCollectionViewsEnabled());
    myCbPopulateThrowableStack.setSelected(generalSettings.POPULATE_THROWABLE_STACKTRACE);

    ClassRenderer classRenderer = rendererSettings.getClassRenderer();

    myCbShowSyntheticFields.setSelected(classRenderer.SHOW_SYNTHETICS);
    myCbShowValFieldsAsLocalVariables.setSelected(classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES);
    if (!classRenderer.SHOW_SYNTHETICS) {
      myCbShowValFieldsAsLocalVariables.makeUnselectable(false);
    }
    myCbShowStatic.setSelected(classRenderer.SHOW_STATIC);
    myCbShowStaticFinalFields.setSelected(classRenderer.SHOW_STATIC_FINAL);
    if (!classRenderer.SHOW_STATIC) {
      myCbShowStaticFinalFields.makeUnselectable(false);
    }
    myCbShowDeclaredType.setSelected(classRenderer.SHOW_DECLARED_TYPE);
    myCbShowFQNames.setSelected(classRenderer.SHOW_FQ_TYPE_NAMES);
    myCbShowObjectId.setSelected(classRenderer.SHOW_OBJECT_ID);
    myCbShowStringsType.setSelected(classRenderer.SHOW_STRINGS_TYPE);

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    final boolean toStringEnabled = !toStringRenderer.isOnDemand();
    final boolean useClassFilters = toStringRenderer.isUseClassFilters();
    myCbEnableToString.setSelected(toStringEnabled);
    myRbAllThatOverride.setSelected(!useClassFilters);
    myRbFromList.setSelected(useClassFilters);
    myToStringFilterEditor.setFilters(toStringRenderer.getClassFilters());
    myToStringFilterEditor.setEnabled(toStringEnabled && useClassFilters);
    myRbFromList.setEnabled(toStringEnabled);
    myRbAllThatOverride.setEnabled(toStringEnabled);

    PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
    myCbHexValue.setSelected(primitiveRenderer.isShowHexValue());
  }

  @Override
  public boolean isModified() {
    return areGeneralSettingsModified() || areDefaultRenderersModified();
  }

  private boolean areGeneralSettingsModified() {
    ViewsGeneralSettings generalSettings = ViewsGeneralSettings.getInstance();
    return generalSettings.AUTOSCROLL_TO_NEW_LOCALS != myCbAutoscroll.isSelected() ||
           generalSettings.USE_DFA_ASSIST != myCbDfaAssist.isSelected() ||
           generalSettings.USE_DFA_ASSIST_GRAY_OUT != myCbDfaAssistGrayOut.isSelected() ||
           generalSettings.HIDE_NULL_ARRAY_ELEMENTS != myCbHideNullArrayElements.isSelected() ||
           generalSettings.POPULATE_THROWABLE_STACKTRACE != myCbPopulateThrowableStack.isSelected();
  }

  private boolean areDefaultRenderersModified() {
    //if (myArrayRendererConfigurable.isModified()) {
    //  return true;
    //}

    final NodeRendererSettings rendererSettings = NodeRendererSettings.getInstance();

    final ClassRenderer classRenderer = rendererSettings.getClassRenderer();
    final boolean isClassRendererModified =
      (classRenderer.SHOW_STATIC != myCbShowStatic.isSelected()) ||
      (classRenderer.SHOW_STATIC_FINAL != myCbShowStaticFinalFields.isSelectedWhenSelectable()) ||
      (classRenderer.SHOW_SYNTHETICS != myCbShowSyntheticFields.isSelected()) ||
      (classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES != myCbShowValFieldsAsLocalVariables.isSelectedWhenSelectable()) ||
      (classRenderer.SHOW_DECLARED_TYPE != myCbShowDeclaredType.isSelected()) ||
      (classRenderer.SHOW_FQ_TYPE_NAMES != myCbShowFQNames.isSelected()) ||
      (classRenderer.SHOW_OBJECT_ID != myCbShowObjectId.isSelected()) ||
      (classRenderer.SHOW_STRINGS_TYPE != myCbShowStringsType.isSelected());

    if (isClassRendererModified) {
      return true;
    }

    final ToStringRenderer toStringRenderer = rendererSettings.getToStringRenderer();
    final boolean isToStringRendererModified =
      (toStringRenderer.isOnDemand() == myCbEnableToString.isSelected()) ||
      (toStringRenderer.isUseClassFilters() != myRbFromList.isSelected()) ||
      (!DebuggerUtilsEx.filterEquals(toStringRenderer.getClassFilters(), myToStringFilterEditor.getFilters()));
    if (isToStringRendererModified) {
      return true;
    }

    if (rendererSettings.areAlternateCollectionViewsEnabled() != myCbEnableAlternateViews.isSelected()) {
      return true;
    }

    PrimitiveRenderer primitiveRenderer = rendererSettings.getPrimitiveRenderer();
    if (primitiveRenderer.isShowHexValue() != myCbHexValue.isSelected()) {
      return true;
    }

    return false;
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "Debugger_Data_Views_Java";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}

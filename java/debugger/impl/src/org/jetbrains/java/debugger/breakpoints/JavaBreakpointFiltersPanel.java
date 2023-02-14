// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.ui.breakpoints.CallerFiltersField;
import com.intellij.debugger.ui.breakpoints.ClassFiltersField;
import com.intellij.debugger.ui.breakpoints.EditInstanceFiltersDialog;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.text.LiteralFormatUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JavaBreakpointFiltersPanel<T extends JavaBreakpointProperties, B extends XBreakpoint<T>> extends XBreakpointCustomPropertiesPanel<B> {
  private JPanel myConditionsPanel;
  private JCheckBox myInstanceFiltersCheckBox;
  private JPanel myInstanceFiltersFieldPanel;
  private JCheckBox myClassFiltersCheckBox;
  private ClassFiltersField myClassFiltersField;
  private JCheckBox myPassCountCheckbox;
  private JTextField myPassCountField;
  private JCheckBox myCatchCheckBox;
  private ClassFiltersField myCatchClassFilters;
  private JPanel myCatchFiltersPanel;
  private JPanel myPassCountFieldPanel;
  private JCheckBox myCallerFiltersCheckBox;
  private CallerFiltersField myCallerFilters;
  private JPanel myCallerFiltersPanel;

  private final FieldPanel myInstanceFiltersField;

  private InstanceFilter[] myInstanceFilters = InstanceFilter.EMPTY_ARRAY;
  protected final Project myProject;

  private PsiClass myBreakpointPsiClass;

  public JavaBreakpointFiltersPanel(Project project) {
    myProject = project;
    myInstanceFiltersField = new FieldPanel(new MyTextField(), "", null,
                                            new ActionListener() {
                                              @Override
                                              public void actionPerformed(ActionEvent e) {
                                                reloadInstanceFilters();
                                                EditInstanceFiltersDialog _dialog = new EditInstanceFiltersDialog(myProject);
                                                _dialog.setFilters(myInstanceFilters);
                                                _dialog.show();
                                                if (_dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
                                                  myInstanceFilters = _dialog.getFilters();
                                                  updateInstanceFilterEditor(true);
                                                }
                                              }
                                            },
                                            null
    );

    ActionListener updateListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateCheckboxes();
      }
    };

    myPassCountCheckbox.addActionListener(updateListener);
    myInstanceFiltersCheckBox.addActionListener(updateListener);
    myClassFiltersCheckBox.addActionListener(updateListener);
    myCatchCheckBox.addActionListener(updateListener);
    myCallerFiltersCheckBox.addActionListener(updateListener);

    ToolTipManager.sharedInstance().registerComponent(myInstanceFiltersField.getTextField());

    insert(myInstanceFiltersFieldPanel, myInstanceFiltersField);

    myCatchClassFilters.setBorder(JBUI.Borders.emptyLeft(myCatchCheckBox.getInsets().left));
    myInstanceFiltersField.setBorder(JBUI.Borders.emptyLeft(myInstanceFiltersCheckBox.getInsets().left));
    myClassFiltersField.setBorder(JBUI.Borders.emptyLeft(myClassFiltersCheckBox.getInsets().left));
    myPassCountFieldPanel.setBorder(JBUI.Borders.emptyLeft(myPassCountCheckbox.getInsets().left));
    myCallerFilters.setBorder(JBUI.Borders.emptyLeft(myCallerFiltersCheckBox.getInsets().left));

    DebuggerUIUtil.focusEditorOnCheck(myPassCountCheckbox, myPassCountField);
    DebuggerUIUtil.focusEditorOnCheck(myInstanceFiltersCheckBox, myInstanceFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myClassFiltersCheckBox, myClassFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myCatchCheckBox, myCatchClassFilters.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myCallerFiltersCheckBox, myCallerFilters);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myConditionsPanel;
  }

  @Override
  public boolean isVisibleOnPopup(@NotNull B breakpoint) {
    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties != null) {
      return properties.isCOUNT_FILTER_ENABLED() ||
             properties.isCLASS_FILTERS_ENABLED() ||
             properties.isINSTANCE_FILTERS_ENABLED() ||
             properties.isCALLER_FILTERS_ENABLED() ||
             (properties instanceof JavaExceptionBreakpointProperties &&
              ((JavaExceptionBreakpointProperties)properties).isCatchFiltersEnabled());
    }
    return false;
  }

  @Override
  public void saveTo(@NotNull B breakpoint) {
    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties == null) {
      return;
    }

    boolean changed = false;
    try {
      String text = LiteralFormatUtil.removeUnderscores(myPassCountField.getText().trim());
      int filter = !text.isEmpty() ? Integer.parseInt(text) : 0;
      if (filter < 0) filter = 0;
      changed = properties.setCOUNT_FILTER(filter);
    }
    catch (Exception ignored) {
    }

    changed = properties.setCOUNT_FILTER_ENABLED(properties.getCOUNT_FILTER() > 0 && myPassCountCheckbox.isSelected()) || changed;
    reloadInstanceFilters();
    updateInstanceFilterEditor(true);

    if (properties instanceof JavaExceptionBreakpointProperties exceptionBreakpointProperties) {
      changed = exceptionBreakpointProperties.setCatchFiltersEnabled(!myCatchClassFilters.getText().isEmpty() && myCatchCheckBox.isSelected()) || changed;
      changed = exceptionBreakpointProperties.setCatchClassFilters(myCatchClassFilters.getClassFilters()) || changed;
      changed = exceptionBreakpointProperties.setCatchClassExclusionFilters(myCatchClassFilters.getClassExclusionFilters()) || changed;
    }

    changed = properties.setCLASS_FILTERS_ENABLED(!myClassFiltersField.getText().isEmpty() && myClassFiltersCheckBox.isSelected()) || changed;
    changed = properties.setClassFilters(myClassFiltersField.getClassFilters()) || changed;
    changed = properties.setClassExclusionFilters(myClassFiltersField.getClassExclusionFilters()) || changed;

    changed = properties.setINSTANCE_FILTERS_ENABLED(!myInstanceFiltersField.getText().isEmpty() && myInstanceFiltersCheckBox.isSelected()) || changed;
    changed = properties.setInstanceFilters(myInstanceFilters) || changed;

    changed = properties.setCALLER_FILTERS_ENABLED(!myCallerFilters.getText().isEmpty() && myCallerFiltersCheckBox.isSelected()) || changed;
    changed = properties.setCallerFilters(myCallerFilters.getClassFilters()) || changed;
    changed = properties.setCallerExclusionFilters(myCallerFilters.getClassExclusionFilters()) || changed;

    if (changed) {
      ((XBreakpointBase<?, ?, ?>)breakpoint).fireBreakpointChanged();
    }
  }

  private static void insert(JPanel panel, JComponent component) {
    panel.setLayout(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
  }

  @Override
  public void loadFrom(@NotNull B breakpoint) {
    myCatchFiltersPanel.setVisible(false);
    myCallerFiltersPanel.setVisible(false);

    JavaBreakpointProperties properties = breakpoint.getProperties();
    if (properties != null) {
      if (properties.getCOUNT_FILTER() > 0) {
        myPassCountField.setText(Integer.toString(properties.getCOUNT_FILTER()));
      }
      else {
        myPassCountField.setText("");
      }

      myPassCountCheckbox.setSelected(properties.isCOUNT_FILTER_ENABLED());

      myInstanceFiltersCheckBox.setSelected(properties.isINSTANCE_FILTERS_ENABLED());
      myInstanceFilters = properties.getInstanceFilters();
      updateInstanceFilterEditor(true);

      myClassFiltersCheckBox.setSelected(properties.isCLASS_FILTERS_ENABLED());
      myClassFiltersField.setClassFilters(properties.getClassFilters(), properties.getClassExclusionFilters());

      if (properties instanceof JavaExceptionBreakpointProperties exceptionBreakpointProperties) {
        myCatchFiltersPanel.setVisible(true);
        myCatchCheckBox.setSelected(exceptionBreakpointProperties.isCatchFiltersEnabled());
        myCatchClassFilters.setClassFilters(exceptionBreakpointProperties.getCatchClassFilters(),
                                            exceptionBreakpointProperties.getCatchClassExclusionFilters());
      }

      myCallerFiltersPanel.setVisible(true);
      myCallerFiltersCheckBox.setSelected(properties.isCALLER_FILTERS_ENABLED());
      myCallerFilters.setClassFilters(properties.getCallerFilters(), properties.getCallerExclusionFilters());

      //XSourcePosition position = breakpoint.getSourcePosition();
      // TODO: need to calculate psi class
      //myBreakpointPsiClass = breakpoint.getPsiClass();
    }
    updateCheckboxes();
  }

  private void updateInstanceFilterEditor(boolean updateText) {
    List<String> filters = StreamEx.of(myInstanceFilters).filter(InstanceFilter::isEnabled).map(f -> Long.toString(f.getId())).toList();
    if (updateText) {
      myInstanceFiltersField.setText(StringUtil.join(filters, " "));
    }

    String tipText = concatWithEx(filters, " ", (int)Math.sqrt(myInstanceFilters.length) + 1, "\n");
    myInstanceFiltersField.getTextField().setToolTipText(tipText);
  }

  private void createUIComponents() {
    myClassFiltersField = new ClassFiltersField(myProject, this);
    myCatchClassFilters = new ClassFiltersField(myProject, this);
    myCallerFilters = new CallerFiltersField(myProject, this);
  }

  private class MyTextField extends ExtendableTextField {
    MyTextField() {
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      reloadInstanceFilters();
      updateInstanceFilterEditor(false);
      String toolTipText = super.getToolTipText(event);
      return getToolTipText().length() == 0 ? null : toolTipText;
    }

    @Override
    public JToolTip createToolTip() {
      JToolTip toolTip = new JToolTip() {{
        setUI(new MultiLineTooltipUI());
      }};
      toolTip.setComponent(this);
      return toolTip;
    }
  }

  private void reloadInstanceFilters() {
    String filtersText = myInstanceFiltersField.getText();

    ArrayList<InstanceFilter> idxs = new ArrayList<>();
    int startNumber = -1;
    for (int i = 0; i <= filtersText.length(); i++) {
      if (i < filtersText.length() && Character.isDigit(filtersText.charAt(i))) {
        if (startNumber == -1) {
          startNumber = i;
        }
      }
      else {
        if (startNumber >= 0) {
          idxs.add(InstanceFilter.create(filtersText.substring(startNumber, i)));
          startNumber = -1;
        }
      }
    }
    myInstanceFilters = StreamEx.of(myInstanceFilters).remove(InstanceFilter::isEnabled).prepend(idxs).toArray(InstanceFilter[]::new);
  }

  @Contract(pure = true)
  private static String concatWithEx(List<String> s, String concator, int N, String NthConcator) {
    StringBuilder result = new StringBuilder();
    int i = 1;
    for (Iterator iterator = s.iterator(); iterator.hasNext(); i++) {
      String str = (String)iterator.next();
      result.append(str);
      if (iterator.hasNext()) {
        if (i % N == 0) {
          result.append(NthConcator);
        }
        else {
          result.append(concator);
        }
      }
    }
    return result.toString();
  }

  protected ClassFilter createClassConditionFilter() {
    ClassFilter classFilter;
    if (myBreakpointPsiClass != null) {
      classFilter = new ClassFilter() {
        @Override
        public boolean isAccepted(PsiClass aClass) {
          return myBreakpointPsiClass == aClass || aClass.isInheritor(myBreakpointPsiClass, true);
        }
      };
    }
    else {
      classFilter = null;
    }
    return classFilter;
  }

  protected void updateCheckboxes() {
    boolean passCountApplicable = true;
    if (myInstanceFiltersCheckBox.isSelected() || myClassFiltersCheckBox.isSelected()) {
      passCountApplicable = false;
    }
    myPassCountCheckbox.setEnabled(passCountApplicable);

    final boolean passCountSelected = myPassCountCheckbox.isSelected();
    myInstanceFiltersCheckBox.setEnabled(!passCountSelected);
    myClassFiltersCheckBox.setEnabled(!passCountSelected);

    myPassCountField.setEditable(myPassCountCheckbox.isSelected());
    myPassCountField.setEnabled(myPassCountCheckbox.isSelected());

    myInstanceFiltersField.setEnabled(myInstanceFiltersCheckBox.isSelected());
    myInstanceFiltersField.getTextField().setEditable(myInstanceFiltersCheckBox.isSelected());

    myClassFiltersField.setEnabled(myClassFiltersCheckBox.isSelected());
    myClassFiltersField.setEditable(myClassFiltersCheckBox.isSelected());

    myCatchClassFilters.setEnabled(myCatchCheckBox.isSelected());
    myCatchClassFilters.setEditable(myCatchCheckBox.isSelected());

    myCallerFilters.setEnabled(myCallerFiltersCheckBox.isSelected());
    myCallerFilters.setEditable(myCallerFiltersCheckBox.isSelected());
  }
}

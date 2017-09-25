/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.ui.breakpoints.ClassFiltersField;
import com.intellij.debugger.ui.breakpoints.EditInstanceFiltersDialog;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import one.util.streamex.StreamEx;
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

/**
 * @author egor
 */
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

    ToolTipManager.sharedInstance().registerComponent(myInstanceFiltersField.getTextField());

    insert(myInstanceFiltersFieldPanel, myInstanceFiltersField);

    myCatchClassFilters.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myCatchCheckBox)));
    myInstanceFiltersField.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myInstanceFiltersCheckBox)));
    myClassFiltersField.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myClassFiltersCheckBox)));
    myPassCountFieldPanel.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myPassCountCheckbox)));

    DebuggerUIUtil.focusEditorOnCheck(myPassCountCheckbox, myPassCountField);
    DebuggerUIUtil.focusEditorOnCheck(myInstanceFiltersCheckBox, myInstanceFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myClassFiltersCheckBox, myClassFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myCatchCheckBox, myCatchClassFilters.getTextField());
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
      String text = myPassCountField.getText().trim();
      int filter = !text.isEmpty() ? Integer.parseInt(text) : 0;
      if (filter < 0) filter = 0;
      changed = properties.setCOUNT_FILTER(filter);
    }
    catch (Exception ignored) {
    }

    changed = properties.setCOUNT_FILTER_ENABLED(properties.getCOUNT_FILTER() > 0 && myPassCountCheckbox.isSelected()) || changed;
    reloadInstanceFilters();
    updateInstanceFilterEditor(true);

    if (properties instanceof JavaExceptionBreakpointProperties) {
      JavaExceptionBreakpointProperties exceptionBreakpointProperties = (JavaExceptionBreakpointProperties)properties;
      changed = exceptionBreakpointProperties.setCatchFiltersEnabled(!myCatchClassFilters.getText().isEmpty() && myCatchCheckBox.isSelected()) || changed;
      changed = exceptionBreakpointProperties.setCatchClassFilters(myCatchClassFilters.getClassFilters()) || changed;
      changed = exceptionBreakpointProperties.setCatchClassExclusionFilters(myCatchClassFilters.getClassExclusionFilters()) || changed;
    }

    changed = properties.setCLASS_FILTERS_ENABLED(!myClassFiltersField.getText().isEmpty() && myClassFiltersCheckBox.isSelected()) || changed;
    changed = properties.setClassFilters(myClassFiltersField.getClassFilters()) || changed;
    changed = properties.setClassExclusionFilters(myClassFiltersField.getClassExclusionFilters()) || changed;

    changed = properties.setINSTANCE_FILTERS_ENABLED(!myInstanceFiltersField.getText().isEmpty() && myInstanceFiltersCheckBox.isSelected()) || changed;
    changed = properties.setInstanceFilters(myInstanceFilters) || changed;

    if (changed) {
      ((XBreakpointBase)breakpoint).fireBreakpointChanged();
    }
  }

  private static void insert(JPanel panel, JComponent component) {
    panel.setLayout(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
  }

  @Override
  public void loadFrom(@NotNull B breakpoint) {
    myCatchFiltersPanel.setVisible(false);
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

      if (properties instanceof JavaExceptionBreakpointProperties) {
        myCatchFiltersPanel.setVisible(true);
        JavaExceptionBreakpointProperties exceptionBreakpointProperties = (JavaExceptionBreakpointProperties)properties;
        myCatchCheckBox.setSelected(exceptionBreakpointProperties.isCatchFiltersEnabled());
        myCatchClassFilters.setClassFilters(exceptionBreakpointProperties.getCatchClassFilters(),
                                            exceptionBreakpointProperties.getCatchClassExclusionFilters());
      }

      XSourcePosition position = breakpoint.getSourcePosition();
      // TODO: need to calculate psi class
      //myBreakpointPsiClass = breakpoint.getPsiClass();
    }
    updateCheckboxes();
  }

  private void updateInstanceFilterEditor(boolean updateText) {
    List<String> filters = new ArrayList<>();
    for (InstanceFilter instanceFilter : myInstanceFilters) {
      if (instanceFilter.isEnabled()) {
        filters.add(Long.toString(instanceFilter.getId()));
      }
    }
    if (updateText) {
      myInstanceFiltersField.setText(StringUtil.join(filters, " "));
    }

    String tipText = concatWithEx(filters, " ", (int)Math.sqrt(myInstanceFilters.length) + 1, "\n");
    myInstanceFiltersField.getTextField().setToolTipText(tipText);
  }

  private void createUIComponents() {
    myClassFiltersField = new ClassFiltersField(myProject);
    myCatchClassFilters = new ClassFiltersField(myProject);
  }

  private class MyTextField extends JTextField {
    public MyTextField() {
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
      JToolTip toolTip = new JToolTip(){{
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
    for(int i = 0; i <= filtersText.length(); i++) {
      if(i < filtersText.length() && Character.isDigit(filtersText.charAt(i))) {
        if(startNumber == -1) {
          startNumber = i;
        }
      }
      else {
        if(startNumber >=0) {
          idxs.add(InstanceFilter.create(filtersText.substring(startNumber, i)));
          startNumber = -1;
        }
      }
    }
    myInstanceFilters = StreamEx.of(myInstanceFilters).remove(InstanceFilter::isEnabled).prepend(idxs).toArray(InstanceFilter[]::new);
  }

  private static String concatWithEx(List<String> s, String concator, int N, String NthConcator) {
    String result = "";
    int i = 1;
    for (Iterator iterator = s.iterator(); iterator.hasNext(); i++) {
      String str = (String) iterator.next();
      result += str;
      if(iterator.hasNext()){
        if(i % N == 0){
          result += NthConcator;
        }
        else {
          result += concator;
        }
      }
    }
    return result;
  }

  protected ClassFilter createClassConditionFilter() {
    ClassFilter classFilter;
    if(myBreakpointPsiClass != null) {
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
    myPassCountField.setEnabled (myPassCountCheckbox.isSelected());

    myInstanceFiltersField.setEnabled(myInstanceFiltersCheckBox.isSelected());
    myInstanceFiltersField.getTextField().setEditable(myInstanceFiltersCheckBox.isSelected());

    myClassFiltersField.setEnabled(myClassFiltersCheckBox.isSelected());
    myClassFiltersField.setEditable(myClassFiltersCheckBox.isSelected());

    myCatchClassFilters.setEnabled(myCatchCheckBox.isSelected());
    myCatchClassFilters.setEditable(myCatchCheckBox.isSelected());
  }
}

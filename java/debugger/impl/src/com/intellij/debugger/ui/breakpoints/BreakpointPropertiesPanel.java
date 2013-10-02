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

/*
 * Class BreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.DebuggerStatementEditor;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.util.IJSwingUtilities;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointChooser;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointNoneItem;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class BreakpointPropertiesPanel {

  private BreakpointChooser myMasterBreakpointChooser;

  public void setDetailView(DetailView detailView) {
    myMasterBreakpointChooser.setDetailView(detailView);
  }

  protected final Project myProject;
  private final Key<? extends Breakpoint> myBreakpointCategory;
  private boolean myCompact;
  private JPanel myPanel;
  private final DebuggerExpressionComboBox myConditionCombo;
  private final DebuggerExpressionComboBox myLogExpressionCombo;
  private JTextField myPassCountField;
  private final FieldPanel myInstanceFiltersField;

  private final FieldPanel myClassFiltersField;
  private com.intellij.ui.classFilter.ClassFilter[] myClassFilters;
  private com.intellij.ui.classFilter.ClassFilter[] myClassExclusionFilters;
  private InstanceFilter[] myInstanceFilters;

  private JCheckBox myLogExpressionCheckBox;
  private JCheckBox myLogMessageCheckBox;
  protected JCheckBox myPassCountCheckbox;
  private JCheckBox myInstanceFiltersCheckBox;
  private JCheckBox myClassFiltersCheckBox;

  private JPanel myInstanceFiltersFieldPanel;
  private JPanel myClassFiltersFieldPanel;
  private JPanel myConditionComboPanel;
  private JPanel myLogExpressionComboPanel;
  private JPanel myDependentBreakpointComboPanel;
  private JPanel mySpecialBoxPanel;
  private PsiClass myBreakpointPsiClass;

  private JRadioButton myRbSuspendThread;
  private JRadioButton myRbSuspendAll;
  private JCheckBox myCbSuspend;
  private JButton myMakeDefaultButton;

  private JRadioButton myDisableAgainRadio;
  private JRadioButton myLeaveEnabledRadioButton;

  private JLabel myEnableOrDisableLabel;
  private JPanel myDependsOnPanel;
  private JPanel myInstanceFiltersPanel;
  private JPanel myClassFiltersPanel;
  private JPanel myPassCountPanel;
  private JPanel myConditionsPanel;
  private JPanel myActionsPanel;
  private JCheckBox myConditionCheckbox;
  private JCheckBox myTemporaryCheckBox;
  private JCheckBox myEnabledCheckbox;

  ButtonGroup mySuspendPolicyGroup;
  public static final String CONTROL_LOG_MESSAGE = "logMessage";
  private final FixedSizeButton myConditionMagnifierButton;
  private boolean myMoreOptionsVisible = true;
  private Breakpoint myBreakpoint;

  public boolean isMoreOptionsVisible() {
    return myMoreOptionsVisible;
  }

  public interface Delegate {

    void showActionsPanel();
  }
  private Delegate myDelegate;

  public JComponent getControl(String control) {
    if(CONTROL_LOG_MESSAGE.equals(control)) {
      return myLogExpressionCombo;
    }
    return null;
  }

  public void dispose() {
    if (myConditionCombo != null) {
      myConditionCombo.dispose();
    }
    if (myLogExpressionCombo != null) {
      myLogExpressionCombo.dispose();
    }
  }

  public void setActionsPanelVisible(boolean b) {
    myActionsPanel.setVisible(b);
  }

  public void setMoreOptionsVisible(boolean b) {
    myMoreOptionsVisible = b;
    myDependsOnPanel.setVisible(b);
    myConditionsPanel.setVisible(b);
    if (b) {
      myActionsPanel.setVisible(true);
    }
    if (!b) {
      myPanel.setPreferredSize(new Dimension(500, -1));
    }
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  private class MyTextField extends JTextField {
    public MyTextField() {
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      reloadClassFilters();
      updateClassFilterEditor(false);
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

  private static void insert(JPanel panel, JComponent component) {
    panel.setLayout(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
  }

  public BreakpointPropertiesPanel(final Project project, final Key<? extends Breakpoint> breakpointCategory, boolean compact) {
    myProject = project;
    myBreakpointCategory = breakpointCategory;
    myCompact = compact;

    mySuspendPolicyGroup = new ButtonGroup();
    mySuspendPolicyGroup.add(myRbSuspendAll);
    mySuspendPolicyGroup.add(myRbSuspendThread);

    updateSuspendPolicyRbFont();
    final ItemListener suspendPolicyChangeListener = new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        final BreakpointDefaults defaults = getBreakpointManager(myProject).getBreakpointDefaults(breakpointCategory);
        myMakeDefaultButton.setEnabled(!defaults.getSuspendPolicy().equals(getSelectedSuspendPolicy()) || defaults.isConditionEnabled() != myConditionCheckbox.isSelected());
      }
    };

    myCbSuspend.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        final boolean enabled = myCbSuspend.isSelected();
        myRbSuspendAll.setEnabled(enabled);
        myRbSuspendThread.setEnabled(enabled);
      }
    });


    myRbSuspendAll.addItemListener(suspendPolicyChangeListener);
    myRbSuspendThread.addItemListener(suspendPolicyChangeListener);
    myConditionCheckbox.addItemListener(suspendPolicyChangeListener);

    myMakeDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final BreakpointManager breakpointManager = getBreakpointManager(myProject);
        final String suspendPolicy = getSelectedSuspendPolicy();
        breakpointManager.setBreakpointDefaults(breakpointCategory, new BreakpointDefaults(suspendPolicy, myConditionCheckbox.isSelected()));
        updateSuspendPolicyRbFont();
        if (DebuggerSettings.SUSPEND_THREAD.equals(suspendPolicy)) {
          myRbSuspendThread.requestFocus();
        }
        else {
          myRbSuspendAll.requestFocus();
        }
        myMakeDefaultButton.setEnabled(false);
      }
    });

    myConditionCombo = new DebuggerExpressionComboBox(project, "LineBreakpoint condition");
    myConditionCombo.addDocumentListener(new DocumentListener() {
      @Override
      public void beforeDocumentChange(DocumentEvent event) {
        myConditionCheckbox.setSelected(true);
      }

      @Override
      public void documentChanged(DocumentEvent event) {

      }
    });

    myConditionCombo.getEditorComponent().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        myConditionCombo.setEnabled(true);
        myConditionCheckbox.setSelected(true);
      }
    });

    if (myCompact) {
      myPanel.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
          DebuggerExpressionComboBox focus;
          if (myLogExpressionCheckBox.isSelected()) {
            focus = myLogExpressionCombo;
          } else {
            focus = myConditionCombo;
          }
          IdeFocusManager.findInstance().requestFocus(focus, true);
        }
      });
    }

    myLogExpressionCombo = new DebuggerExpressionComboBox(project, "LineBreakpoint logMessage");

    myInstanceFiltersField = new FieldPanel(new MyTextField(), "", null,
     new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reloadInstanceFilters();
        EditInstanceFiltersDialog _dialog = new EditInstanceFiltersDialog(myProject);
        _dialog.setFilters(myInstanceFilters);
        _dialog.show();
        if(_dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          myInstanceFilters = _dialog.getFilters();
          updateInstanceFilterEditor(true);
        }
      }
    },
     null
    );

    myClassFiltersField = new FieldPanel(new MyTextField(), "", null,
     new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        reloadClassFilters();

        ClassFilter classFilter = createClassConditionFilter();

        EditClassFiltersDialog _dialog = new EditClassFiltersDialog(myProject, classFilter);
        _dialog.setFilters(myClassFilters, myClassExclusionFilters);
        _dialog.show();
        if (_dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          myClassFilters = _dialog.getFilters();
          myClassExclusionFilters = _dialog.getExclusionFilters();
          updateClassFilterEditor(true);
        }
      }
    },
     null
    );
    ToolTipManager.sharedInstance().registerComponent(myClassFiltersField.getTextField());
    ToolTipManager.sharedInstance().registerComponent(myInstanceFiltersField.getTextField());

    JComponent specialBox = createSpecialBox();
    if(specialBox != null) {
      insert(mySpecialBoxPanel, specialBox);
    } 
    else {
      mySpecialBoxPanel.setVisible(false);
    }

    final JPanel conditionPanel = new JPanel(new BorderLayout());
    conditionPanel.add(myConditionCombo, BorderLayout.CENTER);
    myConditionMagnifierButton = new FixedSizeButton(myConditionCombo);
    conditionPanel.add(myConditionMagnifierButton, BorderLayout.EAST);
    myConditionMagnifierButton.setFocusable(false);
    myConditionMagnifierButton.addActionListener(new MagnifierButtonAction(project, myConditionCombo, "Condition"));

    insert(myConditionComboPanel, conditionPanel);
    insert(myLogExpressionComboPanel, myLogExpressionCombo);

    insert(myInstanceFiltersFieldPanel, myInstanceFiltersField);
    insert(myClassFiltersFieldPanel, myClassFiltersField);

    DebuggerUIUtil.enableEditorOnCheck(myLogExpressionCheckBox, myLogExpressionCombo);
    ActionListener updateListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateCheckboxes();
      }
    };

    myPassCountCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (myPassCountCheckbox.isSelected()) {
          myConditionCheckbox.setSelected(false);
        }
        updateCheckboxes();
      }
    });
    myInstanceFiltersCheckBox.addActionListener(updateListener);
    myClassFiltersCheckBox.addActionListener(updateListener);
    myConditionCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (myConditionCheckbox.isSelected()) {
          myPassCountCheckbox.setSelected(false);
        }
        updateCheckboxes();
      }
    });
    DebuggerUIUtil.focusEditorOnCheck(myPassCountCheckbox, myPassCountField);
    DebuggerUIUtil.focusEditorOnCheck(myLogExpressionCheckBox, myLogExpressionCombo);
    DebuggerUIUtil.focusEditorOnCheck(myInstanceFiltersCheckBox, myInstanceFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myClassFiltersCheckBox, myClassFiltersField.getTextField());
    DebuggerUIUtil.focusEditorOnCheck(myConditionCheckbox, myConditionCombo);

    IJSwingUtilities.adjustComponentsOnMac(myCbSuspend);
    IJSwingUtilities.adjustComponentsOnMac(myLogExpressionCheckBox);
    IJSwingUtilities.adjustComponentsOnMac(myLogMessageCheckBox);
    IJSwingUtilities.adjustComponentsOnMac(myTemporaryCheckBox);
    IJSwingUtilities.adjustComponentsOnMac(myEnabledCheckbox);
  }

  private List<BreakpointItem> getBreakpointItemsExceptMy() {
    List<BreakpointItem> items = new ArrayList<BreakpointItem>();
    final DebuggerSupport support = DebuggerSupport.getDebuggerSupport(JavaDebuggerSupport.class);
    support.getBreakpointPanelProvider().provideBreakpointItems(myProject, items);
    for (BreakpointItem item : items) {
      if (item.getBreakpoint() == myBreakpoint) {
        items.remove(item);
        break;
      }
    }
    items.add(new BreakpointNoneItem());
    return items;
  }

  private void saveMasterBreakpoint() {
    Breakpoint masterBreakpoint = (Breakpoint)myMasterBreakpointChooser.getSelectedBreakpoint();
    if (masterBreakpoint == null) {
      getBreakpointManager(myProject).removeBreakpointRule(myBreakpoint);
    }
    else {
      EnableBreakpointRule rule = findMasterBreakpointRule();
      boolean selected = myLeaveEnabledRadioButton.isSelected();
      if (rule != null) {
        if (rule.getMasterBreakpoint() != masterBreakpoint || rule.isLeaveEnabled() != selected) {
          getBreakpointManager(myProject).removeBreakpointRule(rule);
        }
        else {
          return;
        }
      }
      getBreakpointManager(myProject).addBreakpointRule(new EnableBreakpointRule(getBreakpointManager(myProject), masterBreakpoint, myBreakpoint, selected));
    }
  }

  private String getSelectedSuspendPolicy() {
    if (myRbSuspendThread.isSelected()) {
      return DebuggerSettings.SUSPEND_THREAD;
    }
    return DebuggerSettings.SUSPEND_ALL;
  }

  private void updateSuspendPolicyRbFont() {
    final String defPolicy = getBreakpointManager(myProject).getBreakpointDefaults(myBreakpointCategory).getSuspendPolicy();
    
    final Font font = myRbSuspendAll.getFont().deriveFont(Font.PLAIN);
    final Font boldFont = font.deriveFont(Font.BOLD);
    
    myRbSuspendAll.setFont(DebuggerSettings.SUSPEND_ALL.equals(defPolicy)? boldFont : font);
    myRbSuspendThread.setFont(DebuggerSettings.SUSPEND_THREAD.equals(defPolicy)? boldFont : font);
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

  protected JComponent createSpecialBox() {
    return null;
  }

  /**
   * Init UI components with the values from Breakpoint
   */
  public void initFrom(Breakpoint breakpoint, boolean moreOptionsVisible1) {
    myBreakpoint = breakpoint;
    boolean moreOptionsVisible = moreOptionsVisible1;
    boolean actionsPanelVisible = moreOptionsVisible1;

    initMasterBreakpointPanel();

    if (breakpoint.COUNT_FILTER > 0) {
      myPassCountField.setText(Integer.toString(breakpoint.COUNT_FILTER));
      moreOptionsVisible = true;
    }
    else {
      myPassCountField.setText("");
    }

    PsiElement context = breakpoint.getEvaluationElement();
    myPassCountCheckbox.setSelected(breakpoint.COUNT_FILTER_ENABLED);

    myConditionCheckbox.setSelected(breakpoint.CONDITION_ENABLED);

    myConditionCombo.setEnabled(breakpoint.CONDITION_ENABLED);

    myConditionCombo.setContext(context);
    myConditionCombo.setText(breakpoint.getCondition() != null ? breakpoint.getCondition() : emptyText());

    myCbSuspend.setSelected(breakpoint.SUSPEND);
    myRbSuspendThread.setEnabled(myCbSuspend.isSelected());
    myRbSuspendAll.setEnabled(myCbSuspend.isSelected());

    if(!breakpoint.SUSPEND) {
      actionsPanelVisible = true;
    }
    if(DebuggerSettings.SUSPEND_THREAD.equals(breakpoint.SUSPEND_POLICY)){
      myRbSuspendThread.setSelected(true);
    }
    else {
      myRbSuspendAll.setSelected(true);
    }

    myCbSuspend.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (!myActionsPanel.isVisible()) {
          if (!myCbSuspend.isSelected()) {
            if (myDelegate != null) {
              myDelegate.showActionsPanel();
            }
          }
        }
        myRbSuspendThread.setEnabled(myCbSuspend.isSelected());
        myRbSuspendAll.setEnabled(myCbSuspend.isSelected());
      }
    });
    myLogMessageCheckBox.setSelected(breakpoint.LOG_ENABLED);
    myTemporaryCheckBox.setSelected(breakpoint.REMOVE_AFTER_HIT);
    myEnabledCheckbox.setSelected(breakpoint.ENABLED);
    myEnabledCheckbox.setText(breakpoint.getShortName() + " enabled");

    DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addBreakpointManagerListener(new BreakpointManagerListener() {
      @Override
      public void breakpointsChanged() {
        myEnabledCheckbox.setSelected(myBreakpoint.ENABLED);
      }
    });

    myEnabledCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        if (myBreakpoint.ENABLED != myEnabledCheckbox.isSelected()) {
          myBreakpoint.ENABLED = myEnabledCheckbox.isSelected();
          getBreakpointManager(myProject).fireBreakpointChanged(myBreakpoint);
          myBreakpoint.updateUI();
        }
      }
    });
    myTemporaryCheckBox.setVisible(breakpoint instanceof LineBreakpoint);
    myLogExpressionCheckBox.setSelected(breakpoint.LOG_EXPRESSION_ENABLED);
    if (breakpoint.LOG_ENABLED || breakpoint.LOG_EXPRESSION_ENABLED || (breakpoint instanceof LineBreakpoint && breakpoint.REMOVE_AFTER_HIT)) {
      actionsPanelVisible = true;
    }

    myLogExpressionCombo.setContext(context);

    if (breakpoint.getLogMessage() != null) {
      myLogExpressionCombo.setText(breakpoint.getLogMessage());
    }
    else {
      myLogExpressionCombo.setText(emptyText());
    }

    myLogExpressionCombo.setEnabled(breakpoint.LOG_EXPRESSION_ENABLED);
    if (breakpoint.LOG_EXPRESSION_ENABLED) {
      actionsPanelVisible = true;
    }

    myInstanceFiltersCheckBox.setSelected(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFiltersField.setEnabled(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFiltersField.getTextField().setEditable(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFilters = breakpoint.getInstanceFilters();
    updateInstanceFilterEditor(true);
    if (breakpoint.INSTANCE_FILTERS_ENABLED) {
      moreOptionsVisible = true;
    }

    myClassFiltersCheckBox.setSelected(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFiltersField.setEnabled(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFiltersField.getTextField().setEditable(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFilters = breakpoint.getClassFilters();
    myClassExclusionFilters = breakpoint.getClassExclusionFilters();
    updateClassFilterEditor(true);
    if (breakpoint.CLASS_FILTERS_ENABLED) {
      moreOptionsVisible = true;
    }

    myBreakpointPsiClass = breakpoint.getPsiClass();

    updateCheckboxes();

    setActionsPanelVisible(actionsPanelVisible && !moreOptionsVisible1);
    setMoreOptionsVisible(moreOptionsVisible);
  }

  private void initMasterBreakpointPanel() {
    final EnableBreakpointRule rule = findMasterBreakpointRule();

    final Breakpoint baseBreakpoint = rule != null ? rule.getMasterBreakpoint() : null;
    updateMasterBreakpointPanel(rule);


    myMasterBreakpointChooser = new BreakpointChooser(myProject, new BreakpointChooser.Delegate() {
      @Override
      public void breakpointChosen(Project project, BreakpointItem item) {
        final boolean enabled = item != null && item.getBreakpoint() != null;
        myLeaveEnabledRadioButton.setEnabled(enabled);
        myDisableAgainRadio.setEnabled(enabled);
        myEnableOrDisableLabel.setEnabled(enabled);

        if (item != null) {

          saveMasterBreakpoint();
        }

        updateMasterBreakpointPanel(findMasterBreakpointRule());

      }
    }, baseBreakpoint, getBreakpointItemsExceptMy());

    insert(myDependentBreakpointComboPanel, myMasterBreakpointChooser.getComponent());

  }

  private @Nullable EnableBreakpointRule findMasterBreakpointRule() {
    return myBreakpoint != null? getBreakpointManager(myProject).findBreakpointRule(myBreakpoint) : null;
  }

  private void updateMasterBreakpointPanel(@Nullable EnableBreakpointRule rule) {
    final boolean leaveEnabled = rule != null && rule.isLeaveEnabled();
    if (leaveEnabled) {
      myLeaveEnabledRadioButton.setSelected(true);
    }
    else {
      myDisableAgainRadio.setSelected(true);
    }
  }

  private static TextWithImportsImpl emptyText() {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
  }

  /**
   * Save values in the UI components to the breakpoint object
   */
  public void saveTo(Breakpoint breakpoint, @NotNull Runnable afterUpdate) {

    saveMasterBreakpoint();
    try {
      String text = myPassCountField.getText().trim();
      breakpoint.COUNT_FILTER = !text.isEmpty() ? Integer.parseInt(text) : 0;
      if (breakpoint.COUNT_FILTER < 0) {
        breakpoint.COUNT_FILTER = 0;
      }
    }
    catch (Exception ignored) {
    }

    breakpoint.COUNT_FILTER_ENABLED = breakpoint.COUNT_FILTER > 0 && myPassCountCheckbox.isSelected();
    breakpoint.setCondition(myConditionCombo.getText());
    breakpoint.CONDITION_ENABLED = myConditionCheckbox.isSelected();
    breakpoint.setLogMessage(myLogExpressionCombo.getText());
    breakpoint.LOG_EXPRESSION_ENABLED = !breakpoint.getLogMessage().isEmpty() && myLogExpressionCheckBox.isSelected();
    breakpoint.LOG_ENABLED = myLogMessageCheckBox.isSelected();
    breakpoint.ENABLED = myEnabledCheckbox.isSelected();
    breakpoint.REMOVE_AFTER_HIT = myTemporaryCheckBox.isSelected();
    breakpoint.SUSPEND = myCbSuspend.isSelected();
    breakpoint.SUSPEND_POLICY = getSelectedSuspendPolicy();
    reloadInstanceFilters();
    reloadClassFilters();
    updateInstanceFilterEditor(true);
    updateClassFilterEditor(true);

    breakpoint.INSTANCE_FILTERS_ENABLED = myInstanceFiltersField.getText().length() > 0 && myInstanceFiltersCheckBox.isSelected();
    breakpoint.CLASS_FILTERS_ENABLED = myClassFiltersField.getText().length() > 0 && myClassFiltersCheckBox.isSelected();
    breakpoint.setClassFilters(myClassFilters);
    breakpoint.setClassExclusionFilters(myClassExclusionFilters);
    breakpoint.setInstanceFilters(myInstanceFilters);

    myConditionCombo.addRecent(myConditionCombo.getText());
    myLogExpressionCombo.addRecent(myLogExpressionCombo.getText());
    breakpoint.updateUI(afterUpdate);
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

  private void updateInstanceFilterEditor(boolean updateText) {
    List<String> filters = new ArrayList<String>();
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

  private void reloadInstanceFilters() {
    String filtersText = myInstanceFiltersField.getText();

    ArrayList<InstanceFilter> idxs = new ArrayList<InstanceFilter>();
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
    for (InstanceFilter instanceFilter : myInstanceFilters) {
      if (!instanceFilter.isEnabled()) {
        idxs.add(instanceFilter);
      }
    }
    myInstanceFilters = idxs.toArray(new InstanceFilter[idxs.size()]);
  }

  private void updateClassFilterEditor(boolean updateText) {
    List<String> filters = new ArrayList<String>();
    for (com.intellij.ui.classFilter.ClassFilter classFilter : myClassFilters) {
      if (classFilter.isEnabled()) {
        filters.add(classFilter.getPattern());
      }
    }
    List<String> excludeFilters = new ArrayList<String>();
    for (com.intellij.ui.classFilter.ClassFilter classFilter : myClassExclusionFilters) {
      if (classFilter.isEnabled()) {
        excludeFilters.add("-" + classFilter.getPattern());
      }
    }
    if (updateText) {
      String editorText = StringUtil.join(filters, " ");
      if(!filters.isEmpty()) {
        editorText += " ";
      }
      editorText += StringUtil.join(excludeFilters, " ");
      myClassFiltersField.setText(editorText);
    }

    int width = (int)Math.sqrt(myClassExclusionFilters.length + myClassFilters.length) + 1;
    String tipText = concatWithEx(filters, " ", width, "\n");
    if(!filters.isEmpty()) {
      tipText += "\n";
    }
    tipText += concatWithEx(excludeFilters, " ", width, "\n");
    myClassFiltersField.getTextField().setToolTipText(tipText);
  }

  private void reloadClassFilters() {
    String filtersText = myClassFiltersField.getText();

    ArrayList<com.intellij.ui.classFilter.ClassFilter> classFilters     = new ArrayList<com.intellij.ui.classFilter.ClassFilter>();
    ArrayList<com.intellij.ui.classFilter.ClassFilter> exclusionFilters = new ArrayList<com.intellij.ui.classFilter.ClassFilter>();
    int startFilter = -1;
    for(int i = 0; i <= filtersText.length(); i++) {
      if(i < filtersText.length() && !Character.isWhitespace(filtersText.charAt(i))){
        if(startFilter == -1) {
          startFilter = i;
        }
      }
      else {
        if(startFilter >=0) {
          if(filtersText.charAt(startFilter) == '-') {
            exclusionFilters.add(new com.intellij.ui.classFilter.ClassFilter(filtersText.substring(startFilter + 1, i)));
          }
          else {
            classFilters.add(new com.intellij.ui.classFilter.ClassFilter(filtersText.substring(startFilter, i)));
          }
          startFilter = -1;
        }
      }
    }
    for (com.intellij.ui.classFilter.ClassFilter classFilter : myClassFilters) {
      if (!classFilter.isEnabled()) {
        classFilters.add(classFilter);
      }
    }
    for (com.intellij.ui.classFilter.ClassFilter classFilter : myClassExclusionFilters) {
      if (!classFilter.isEnabled()) {
        exclusionFilters.add(classFilter);
      }
    }
    myClassFilters          = classFilters    .toArray(new com.intellij.ui.classFilter.ClassFilter[classFilters    .size()]);
    myClassExclusionFilters = exclusionFilters.toArray(new com.intellij.ui.classFilter.ClassFilter[exclusionFilters.size()]);
  }

  public void setEnabled(boolean enabled) {
    myPanel.setEnabled(enabled);
    Component[] components = myPanel.getComponents();
    for (Component component : components) {
      component.setEnabled(enabled);
    }
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

    myConditionCombo.setEnabled(myConditionCheckbox.isSelected());
    myConditionMagnifierButton.setEnabled(myConditionCheckbox.isSelected());

    myInstanceFiltersField.setEnabled(myInstanceFiltersCheckBox.isSelected());
    myInstanceFiltersField.getTextField().setEditable(myInstanceFiltersCheckBox.isSelected());

    myClassFiltersField.setEnabled(myClassFiltersCheckBox.isSelected());
    myClassFiltersField.getTextField().setEditable(myClassFiltersCheckBox.isSelected());
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private static BreakpointManager getBreakpointManager(Project project) {
    return DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
  }

  private static class MagnifierButtonAction implements ActionListener {
    private final Project myProject;
    private final CompletionEditor myTargetEditor;
    private final String myDialogTitle;
    private DebuggerStatementEditor myEditor;

    private MagnifierButtonAction(final Project project, final CompletionEditor targetEditor, final String dialogTitle) {
      myProject = project;
      myTargetEditor = targetEditor;
      myDialogTitle = dialogTitle;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
      new DialogWrapper(myTargetEditor, true){
        @Override
        public void show() {
          setTitle(myDialogTitle);
          setModal(true);
          init();
          super.show();
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myEditor;
        }

        @Override
        @Nullable
        protected JComponent createCenterPanel() {
          final JPanel panel = new JPanel(new BorderLayout());
          myEditor = new DebuggerStatementEditor(myProject, myTargetEditor.getContext(), myTargetEditor.getRecentsId(), DefaultCodeFragmentFactory.getInstance());
          myEditor.setPreferredSize(new Dimension(400, 150));
          myEditor.setText(myTargetEditor.getText());
          panel.add(myEditor, BorderLayout.CENTER);
          return panel;
        }

        @Override
        protected void doOKAction() {
          myTargetEditor.setText(myEditor.getText());
          super.doOKAction();
        }
      }.show();
    }
  }
}

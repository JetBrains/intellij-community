/*
 * Class BreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.impl.UIUtil;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.ui.FieldPanel;
import com.intellij.ui.MultiLineTooltipUI;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class BreakpointPropertiesPanel {
  protected final Project myProject;
  private JPanel myPanel;
  private DebuggerExpressionComboBox myConditionCombo;
  private DebuggerExpressionComboBox myLogExpressionCombo;
  private JTextField myPassCountField;
  private FieldPanel myInstanceFiltersField;

  private FieldPanel myClassFiltersField;
  private ClassFilter[] myClassFilters;
  private ClassFilter[] myClassExclusionFilters;
  private InstanceFilter[] myInstanceFilters;

  private JCheckBox myLogExpressionCheckBox;
  private JCheckBox myLogMessageCheckBox;
  protected JCheckBox myPassCountCheckbox;
  private JCheckBox myConditionCheckbox;
  private JCheckBox myInstanceFiltersCheckBox;
  private JCheckBox myClassFiltersCheckBox;

  private JPanel myInstanceFiltersFieldPanel;
  private JPanel myClassFiltersFieldPanel;
  private JPanel myConditionComboPanel;
  private JPanel myLogExpressionComboPanel;
  private JPanel myDependentBreakpointComboPanel;
  private JPanel mySpecialBoxPanel;
  private PsiClass myBreakpointPsiClass;

  private JRadioButton mySuspendThreadRadio;
  private JRadioButton mySuspendNoneRadio;
  private JRadioButton mySuspendAllRadio;

  ButtonGroup mySuspendPolicyGroup;
  public static final @NonNls String CONTROL_LOG_MESSAGE = "logMessage";
  private BreakpointComboboxHandler myBreakpointComboboxHandler;
  private static final int MAX_COMBO_WIDTH = 300;

  public JComponent getControl(String control) {
    if(CONTROL_LOG_MESSAGE.equals(control)) {
      return myLogExpressionCombo;
    }
    return null;
  }

  public void dispose() {
    if (myBreakpointComboboxHandler != null) {
      myBreakpointComboboxHandler.dispose();
      myBreakpointComboboxHandler = null;
    }
    if (myConditionCombo != null) {
      myConditionCombo.dispose();
    }
    if (myLogExpressionCombo != null) {
      myLogExpressionCombo.dispose();
    }
  }

  private class MyTextField extends JTextField {
    public MyTextField() {
    }

    public String getToolTipText(MouseEvent event) {
      reloadClassFilters();
      updateClassFilterEditor(false);
      reloadInstanceFilters();
      updateInstanceFilterEditor(false);
      String toolTipText = super.getToolTipText(event);
      return getToolTipText().length() == 0 ? null : toolTipText;
    }

    public JToolTip createToolTip() {
      JToolTip toolTip = new JToolTip(){
        {
          setUI(new MultiLineTooltipUI());
        }
      };
      toolTip.setComponent(this);
      return toolTip;
    }
  }

  private void insert(JPanel panel, JComponent component) {
    panel.setLayout(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
  }

  public BreakpointPropertiesPanel(Project project) {
    myProject = project;

    mySuspendPolicyGroup = new ButtonGroup();
    mySuspendPolicyGroup.add(mySuspendAllRadio);
    mySuspendPolicyGroup.add(mySuspendThreadRadio);
    mySuspendPolicyGroup.add(mySuspendNoneRadio);

    myConditionCombo = new DebuggerExpressionComboBox(project, "LineBreakpoint condition");
    myLogExpressionCombo = new DebuggerExpressionComboBox(project, "LineBreakpoint logMessage");
    
    final JComboBox baseBreakpointCombo = new ComboBox();
    myBreakpointComboboxHandler = new BreakpointComboboxHandler(myProject, baseBreakpointCombo);
    baseBreakpointCombo.setRenderer(new BreakpointComboRenderer());
    myInstanceFiltersField = new FieldPanel(new MyTextField(), "", null,
     new ActionListener() {
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
      public void actionPerformed(ActionEvent e) {
        reloadClassFilters();

        TreeClassChooser.ClassFilter classFilter;
        classFilter = createClassConditionFilter();

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
    } else {
      mySpecialBoxPanel.setVisible(false);
    }

    insert(myConditionComboPanel, myConditionCombo);
    insert(myLogExpressionComboPanel, myLogExpressionCombo);
    insert(myDependentBreakpointComboPanel, baseBreakpointCombo);
    insert(myInstanceFiltersFieldPanel, myInstanceFiltersField);
    insert(myClassFiltersFieldPanel, myClassFiltersField);

    UIUtil.enableEditorOnCheck(myLogExpressionCheckBox, myLogExpressionCombo);
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateCheckboxes();
      }
    };
    myPassCountCheckbox.addActionListener(listener);
    myConditionCheckbox.addActionListener(listener);
    myInstanceFiltersCheckBox.addActionListener(listener);
    myClassFiltersCheckBox.addActionListener(listener);
    UIUtil.focusEditorOnCheck(myPassCountCheckbox, myPassCountField);
    UIUtil.focusEditorOnCheck(myConditionCheckbox, myConditionCombo);
    UIUtil.focusEditorOnCheck(myLogExpressionCheckBox, myLogExpressionCombo);
    UIUtil.focusEditorOnCheck(myInstanceFiltersCheckBox, myInstanceFiltersField.getTextField());
    UIUtil.focusEditorOnCheck(myClassFiltersCheckBox, myClassFiltersField.getTextField());
  }

  protected TreeClassChooser.ClassFilter createClassConditionFilter() {
    TreeClassChooser.ClassFilter classFilter;
    if(myBreakpointPsiClass != null) {
      classFilter = new TreeClassChooser.ClassFilter() {
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
  public void initFrom(Breakpoint breakpoint) {
    myBreakpointComboboxHandler.initFrom(breakpoint);
    myPassCountField.setText((breakpoint.COUNT_FILTER > 0)? Integer.toString(breakpoint.COUNT_FILTER) : "");

    PsiElement context = breakpoint.getEvaluationElement();
    myPassCountCheckbox.setSelected(breakpoint.COUNT_FILTER_ENABLED);
    myConditionCheckbox.setSelected(breakpoint.CONDITION_ENABLED);
    if(DebuggerSettings.SUSPEND_NONE.equals(breakpoint.SUSPEND_POLICY)) {
      mySuspendPolicyGroup.setSelected(mySuspendNoneRadio.getModel(), true);
    }
    else if(DebuggerSettings.SUSPEND_THREAD.equals(breakpoint.SUSPEND_POLICY)){
      mySuspendPolicyGroup.setSelected(mySuspendThreadRadio.getModel(), true);
    }
    else {
      mySuspendPolicyGroup.setSelected(mySuspendAllRadio.getModel(), true);
    }
    myLogMessageCheckBox.setSelected(breakpoint.LOG_ENABLED);
    myLogExpressionCheckBox.setSelected(breakpoint.LOG_EXPRESSION_ENABLED);

    myConditionCombo.setContext(context);
    myConditionCombo.setText(breakpoint.getCondition() != null ? breakpoint.getCondition() : new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));

    myLogExpressionCombo.setContext(context);
    myLogExpressionCombo.setText(breakpoint.getLogMessage() != null? breakpoint.getLogMessage() : new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, ""));

    myLogExpressionCombo.setEnabled(breakpoint.LOG_EXPRESSION_ENABLED);

    myInstanceFiltersCheckBox.setSelected(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFiltersField.setEnabled(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFiltersField.getTextField().setEditable(breakpoint.INSTANCE_FILTERS_ENABLED);
    myInstanceFilters = breakpoint.getInstanceFilters();
    updateInstanceFilterEditor(true);

    myClassFiltersCheckBox.setSelected(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFiltersField.setEnabled(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFiltersField.getTextField().setEditable(breakpoint.CLASS_FILTERS_ENABLED);
    myClassFilters = breakpoint.getClassFilters();
    myClassExclusionFilters = breakpoint.getClassExclusionFilters();
    updateClassFilterEditor(true);

    myBreakpointPsiClass = breakpoint.getPsiClass();

    updateCheckboxes();
  }

  /**
   * Save values in the UI components to the breakpoint object
   */
  public void saveTo(Breakpoint breakpoint, Runnable afterUpdate) {
    myBreakpointComboboxHandler.saveTo(breakpoint);
    try {
      String text = myPassCountField.getText().trim();
      int count = !"".equals(text)? Integer.parseInt(text) : 0;
      breakpoint.COUNT_FILTER = count;
      if (breakpoint.COUNT_FILTER < 0) {
        breakpoint.COUNT_FILTER = 0;
      }
    }
    catch (Exception e) {
    }
    breakpoint.COUNT_FILTER_ENABLED = breakpoint.COUNT_FILTER > 0? myPassCountCheckbox.isSelected() : false;
    breakpoint.setCondition(myConditionCombo.getText());
    breakpoint.CONDITION_ENABLED = !breakpoint.getCondition().isEmpty() ? myConditionCheckbox.isSelected() : false;
    breakpoint.setLogMessage(myLogExpressionCombo.getText());
    breakpoint.LOG_EXPRESSION_ENABLED = !breakpoint.getLogMessage().isEmpty()? myLogExpressionCheckBox.isSelected() : false;
    breakpoint.LOG_ENABLED = myLogMessageCheckBox.isSelected();
    if(mySuspendAllRadio.isSelected()) {
      breakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_ALL;
    }
    else if(mySuspendThreadRadio.isSelected()) {
      breakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_THREAD;
    }
    else {
      breakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_NONE;
    }
    reloadInstanceFilters();
    reloadClassFilters();
    updateInstanceFilterEditor(true);
    updateClassFilterEditor(true);

    breakpoint.INSTANCE_FILTERS_ENABLED = myInstanceFiltersField.getText().length() > 0 ? myInstanceFiltersCheckBox.isSelected() : false;
    breakpoint.CLASS_FILTERS_ENABLED = myClassFiltersField.getText().length() > 0 ? myClassFiltersCheckBox.isSelected() : false;
    breakpoint.setClassFilters(myClassFilters);
    breakpoint.setClassExclusionFilters(myClassExclusionFilters);
    breakpoint.setInstanceFilters(myInstanceFilters);

    myConditionCombo.addRecent(myConditionCombo.getText());
    myLogExpressionCombo.addRecent(myLogExpressionCombo.getText());
    breakpoint.updateUI(afterUpdate);
  }

  private String concatWith(List<String> s, String concator) {
    String result = "";
    for (Iterator iterator = s.iterator(); iterator.hasNext();) {
      String str = (String) iterator.next();
      result += str + concator;
    }
    if (result.length() > 0) {
      return result.substring(0, result.length() - concator.length());
    }
    else {
      return "";
    }
  }

  private String concatWithEx(List<String> s, String concator, int N, String NthConcator) {
    String result = "";
    int i = 1;
    for (Iterator iterator = s.iterator(); iterator.hasNext(); i++) {
      String str = (String) iterator.next();
      result += str;
      if(iterator.hasNext()){
        if(i % N == 0){
          result += NthConcator;
        } else {
          result += concator;
        }
      }
    }
    return result;
  }

  private void updateInstanceFilterEditor(boolean updateText) {
    List<String> filters = new ArrayList<String>();
    for (int i = 0; i < myInstanceFilters.length; i++) {
      InstanceFilter instanceFilter = myInstanceFilters[i];
      if(instanceFilter.isEnabled()) {
        filters.add(Long.toString(instanceFilter.getId()));
      }
    }
    if (updateText) {
      String editorText = concatWith(filters, " ");
      myInstanceFiltersField.setText(editorText);
    }

    String tipText = concatWithEx(filters, " ", (int)Math.sqrt(myInstanceFilters.length) + 1, "\n");
    myInstanceFiltersField.getTextField().setToolTipText(tipText);
  }

  private void reloadInstanceFilters() {
    String filtersText = myInstanceFiltersField.getText();

    ArrayList<InstanceFilter> idxs = new ArrayList<InstanceFilter>();
    int startNumber = -1;
    for(int i = 0; i <= filtersText.length(); i++) {
      if(i < filtersText.length() && Character.isDigit(filtersText.charAt(i))){
        if(startNumber == -1) startNumber = i;
      } else {
        if(startNumber >=0) {
          idxs.add(InstanceFilter.create(filtersText.substring(startNumber, i)));
          startNumber = -1;
        }
      }
    }
    for (int i = 0; i < myInstanceFilters.length; i++) {
      InstanceFilter instanceFilter = myInstanceFilters[i];
      if(!instanceFilter.isEnabled()) idxs.add(instanceFilter);
    }
    myInstanceFilters = idxs.toArray(new InstanceFilter[idxs.size()]);
  }

  private void updateClassFilterEditor(boolean updateText) {
    List<String> filters = new ArrayList<String>();
    for (int i = 0; i < myClassFilters.length; i++) {
      ClassFilter classFilter = myClassFilters[i];
      if(classFilter.isEnabled()) {
        filters.add(classFilter.getPattern());
      }
    }
    List<String> excludeFilters = new ArrayList<String>();
    for (int i = 0; i < myClassExclusionFilters.length; i++) {
      ClassFilter classFilter = myClassExclusionFilters[i];
      if(classFilter.isEnabled()) {
        excludeFilters.add("-" + classFilter.getPattern());
      }
    }
    if (updateText) {
      String editorText = concatWith(filters, " ");
      if(filters.size() > 0) editorText += " ";
      editorText += concatWith(excludeFilters, " ");
      myClassFiltersField.setText(editorText);
    }

    int width = (int)Math.sqrt(myClassExclusionFilters.length + myClassFilters.length) + 1;
    String tipText = concatWithEx(filters, " ", width, "\n");
    if(filters.size() > 0) tipText += "\n";
    tipText += concatWithEx(excludeFilters, " ", width, "\n");
    myClassFiltersField.getTextField().setToolTipText(tipText);

  }

  private void reloadClassFilters() {
    String filtersText = myClassFiltersField.getText();

    ArrayList<ClassFilter> classFilters     = new ArrayList<ClassFilter>();
    ArrayList<ClassFilter> exclusionFilters = new ArrayList<ClassFilter>();
    int startFilter = -1;
    for(int i = 0; i <= filtersText.length(); i++) {
      if(i < filtersText.length() && !Character.isWhitespace(filtersText.charAt(i))){
        if(startFilter == -1) startFilter = i;
      } else {
        if(startFilter >=0) {
          if(filtersText.charAt(startFilter) == '-'){
            exclusionFilters.add(new ClassFilter(filtersText.substring(startFilter + 1, i)));
          } else {
            classFilters.add(new ClassFilter(filtersText.substring(startFilter, i)));
          }
          startFilter = -1;
        }
      }
    }
    for (int i = 0; i < myClassFilters.length; i++) {
      ClassFilter classFilter = myClassFilters[i];
      if(!classFilter.isEnabled()) classFilters.add(classFilter);
    }
    for (int i = 0; i < myClassExclusionFilters.length; i++) {
      ClassFilter classFilter = myClassExclusionFilters[i];
      if(!classFilter.isEnabled()) exclusionFilters.add(classFilter);
    }
    myClassFilters          = classFilters    .toArray(new ClassFilter[classFilters    .size()]);
    myClassExclusionFilters = exclusionFilters.toArray(new ClassFilter[exclusionFilters.size()]);
  }

  public void setEnabled(boolean enabled) {
    myPanel.setEnabled(enabled);
    Component[] components = myPanel.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      component.setEnabled(enabled);
    }
  }

  protected void updateCheckboxes() {
    JCheckBox [] checkBoxes = new JCheckBox[] { myConditionCheckbox, myInstanceFiltersCheckBox, myClassFiltersCheckBox};
    JCheckBox    selected   = null;
    for(int i =0; i < checkBoxes.length; i++) {
      if(checkBoxes[i].isSelected()) {
        selected = checkBoxes[i];
        break;
      }
    }

    if(selected != null){
      myPassCountCheckbox.setEnabled(false);
    } else {
      myPassCountCheckbox.setEnabled(true);
    }

    for(int i =0; i < checkBoxes.length; i++) {
      checkBoxes[i].setEnabled (!myPassCountCheckbox.isSelected());
    }

    myPassCountField.setEditable(myPassCountCheckbox.isSelected());
    myPassCountField.setEnabled (myPassCountCheckbox.isSelected());
    myConditionCombo.setEnabled(myConditionCheckbox.isSelected());
    myInstanceFiltersField.setEnabled(myInstanceFiltersCheckBox.isSelected());
    myInstanceFiltersField.getTextField().setEditable(myInstanceFiltersCheckBox.isSelected());
    myClassFiltersField.setEnabled(myClassFiltersCheckBox.isSelected());
    myClassFiltersField.getTextField().setEditable(myClassFiltersCheckBox.isSelected());
  }

  public JPanel getPanel() {
    return myPanel;
  }
  
  private static class BreakpointComboboxModel extends DefaultComboBoxModel {
    private final ComboboxItem[] myItems;

    public BreakpointComboboxModel(Breakpoint[] breakpoints) {
      myItems = new ComboboxItem[breakpoints.length + 1];
      myItems[0] = new ComboboxItem();
      for (int idx = 0; idx < breakpoints.length; idx++) {
        myItems[idx + 1] = new ComboboxItem(breakpoints[idx]);
      }
    }

    public int getSize() {
      return myItems.length;
    }

    public ComboboxItem getElementAt(int index) {
      return myItems[index];
    }

    public void selectBreakpoint(Breakpoint breakpoint) {
      for (int idx = 0; idx < myItems.length; idx++) {
        final ComboboxItem item = myItems[idx];
        if (breakpoint == null? item.getBreakpoint() == null : breakpoint.equals(item.getBreakpoint())) {
          if (!item.equals(getSelectedItem())) {
            setSelectedItem(item);
          }
          break;
        }
      }
    }
  }

  private static class ComboboxItem {
    private final Breakpoint breakpoint;

    public ComboboxItem() {
      breakpoint = null;
    }

    public ComboboxItem(@NotNull final Breakpoint breakpoint) {
      this.breakpoint = breakpoint;
    }

    public Breakpoint getBreakpoint() {
      return breakpoint;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ComboboxItem comboboxItem = (ComboboxItem)o;

      if (breakpoint != null ? !breakpoint.equals(comboboxItem.breakpoint) : comboboxItem.breakpoint != null) return false;

      return true;
    }

    public int hashCode() {
      return (breakpoint != null ? breakpoint.hashCode() : 0);
    }
  }
  
  private static class BreakpointComboboxHandler implements BreakpointManagerListener{
    private final JComboBox myCombo;
    private final BreakpointManager myBreakpointManager;
    private Breakpoint myCurrentBreakpoint = null;

    public BreakpointComboboxHandler(final Project project, final JComboBox breakpointCombo) {
      myCombo = breakpointCombo;
      myBreakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
      myBreakpointManager.addBreakpointManagerListener(this);
    }

    public void initFrom(Breakpoint breakpoint) {
      myCurrentBreakpoint = breakpoint;
      fillCombobox();
    }
    
    public void saveTo(Breakpoint slaveBreakpoint) {
      myBreakpointManager.removeBreakpointRule(slaveBreakpoint);
      final Breakpoint masterBreakpoint = ((ComboboxItem)myCombo.getSelectedItem()).getBreakpoint();
      if (masterBreakpoint != null) {
        myBreakpointManager.addBreakpointRule(new EnableBreakpointRule(myBreakpointManager, masterBreakpoint, slaveBreakpoint));
      }
    }
    
    public void breakpointsChanged() {
      fillCombobox();
    }
    
    public void dispose() {
      myBreakpointManager.removeBreakpointManagerListener(this);
    }
    
    private void fillCombobox() {
      final List<Breakpoint> breakpoints = new ArrayList<Breakpoint>(myBreakpointManager.getBreakpoints());
      if (myCurrentBreakpoint != null) {
        // avoid depending on itself
        breakpoints.remove(myCurrentBreakpoint);
        /*
        for (Iterator<Breakpoint> it = breakpoints.iterator(); it.hasNext();) {
          final Breakpoint breakpoint = it.next();
          if (myCurrentBreakpoint.equals(breakpoint)) {
            breakpoints.remove(breakpoint);
            break;
          }
        }
        */
      }
      myCombo.setModel(new BreakpointComboboxModel(breakpoints.toArray(new Breakpoint[breakpoints.size()])));
      final Breakpoint baseBreakpoint = myCurrentBreakpoint != null? myBreakpointManager.findMasterBreakpoint(myCurrentBreakpoint) : null;
      ((BreakpointComboboxModel)myCombo.getModel()).selectBreakpoint(baseBreakpoint);
    }
  }

  private static class BreakpointComboRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setPreferredSize(new Dimension(MAX_COMBO_WIDTH, getPreferredSize().height));
      Breakpoint breakpoint = ((ComboboxItem)value).getBreakpoint();
      final String text = breakpoint != null ? breakpoint.getDisplayName() : DebuggerBundle.message("value.none");
      setText(text);
      final Icon icon;
      if (breakpoint != null) {
        icon = (breakpoint instanceof BreakpointWithHighlighter)?
                          breakpoint.ENABLED? ((BreakpointWithHighlighter)breakpoint).getSetIcon() : ((BreakpointWithHighlighter)breakpoint).getDisabledIcon() :  
                          breakpoint.getIcon();
      }
      else {
        icon = null;
      }
      setIcon(icon);
      setDisabledIcon(icon);
      return this;
    }
  }
}
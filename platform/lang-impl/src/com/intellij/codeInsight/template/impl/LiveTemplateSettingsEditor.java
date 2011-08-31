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

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class LiveTemplateSettingsEditor {
  private final List<TemplateGroup> myTemplateGroups;
  private final TemplateImpl myTemplate;

  private final JTextField myKeyField;
  private final JTextField myDescription;
  private final ComboBox myGroupCombo;
  private final Editor myTemplateEditor;

  private JComboBox myExpandByCombo;
  private final String myDefaultShortcutItem;
  private JCheckBox myCbReformat;

  private final Map<TemplateContextType, JCheckBox> myCbContextMap = new HashMap<TemplateContextType, JCheckBox>();
  private final Map<TemplateOptionalProcessor, JCheckBox> myCbOptionalProcessorMap = new HashMap<TemplateOptionalProcessor, JCheckBox>();

  private JButton myEditVariablesButton;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");
  private final Map<TemplateOptionalProcessor, Boolean> myOptions;
  private final Map<TemplateContextType, Boolean> myContext;

  public LiveTemplateSettingsEditor(TemplateImpl template,
                                    List<TemplateGroup> groups,
                                    String defaultShortcut,
                                    Map<TemplateOptionalProcessor, Boolean> options,
                                    Map<TemplateContextType, Boolean> context) {
    myOptions = options;
    myContext = context;

    myTemplate = template;
    myTemplateGroups = groups;
    myDefaultShortcutItem = CodeInsightBundle.message("dialog.edit.template.shortcut.default", defaultShortcut);

    myKeyField=new JTextField();
    myDescription=new JTextField();
    myGroupCombo=new ComboBox(-1);
    myTemplateEditor = TemplateEditorUtil.createEditor(false, myTemplate.getString(), context);
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myTemplateEditor);
  }

  public JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBag gb = new GridBag().setDefaultInsets(4, 4, 4, 4).setDefaultWeightY(1).setDefaultFill(GridBagConstraints.BOTH);
    
    JPanel editorPanel = new JPanel(new BorderLayout());
    editorPanel.setPreferredSize(new Dimension(250, 100));
    editorPanel.setMinimumSize(editorPanel.getPreferredSize());
    editorPanel.add(myTemplateEditor.getComponent(), BorderLayout.CENTER);
    panel.add(editorPanel, gb.nextLine().next().weighty(1).weightx(1).coverColumn(2));

    myEditVariablesButton = new JButton(CodeInsightBundle.message("dialog.edit.template.button.edit.variables"));
    myEditVariablesButton.setDefaultCapable(false);
    myEditVariablesButton.setMaximumSize(myEditVariablesButton.getPreferredSize());
    panel.add(myEditVariablesButton, gb.next().weighty(0));

    panel.add(createTemplateOptionsPanel(), gb.nextLine().next().next().coverColumn(2).weighty(1));

    panel.add(createShortContextPanel(), gb.nextLine().next().fillCellNone().anchor(GridBagConstraints.WEST));

    myKeyField.getDocument().addDocumentListener(new com.intellij.ui.DocumentAdapter() {
      protected void textChanged(javax.swing.event.DocumentEvent e) {
        validateOKButton();
      }
    });

    myTemplateEditor.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          validateOKButton();
          validateEditVariablesButton();

          applyTemplateText();
          applyVariables(updateVariablesByTemplateText());
        }
      }
    );

    myEditVariablesButton.addActionListener(
      new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          editVariables();
        }
      }
    );

    JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(createNorthPanel(), BorderLayout.NORTH);
    centerPanel.add(panel, BorderLayout.CENTER);
    panel.setBorder(
      IdeBorderFactory.createTitledBorder(CodeInsightBundle.message("dialog.edit.template.template.text.title"), false, false, true));
    return centerPanel;
  }

  private void applyTemplateText() {
    myTemplate.setString(myTemplateEditor.getDocument().getText());
  }

  private void applyVariables(final List<Variable> variables) {
    myTemplate.removeAllParsed();
    for (Variable variable : variables) {
      myTemplate.addVariable(variable.getName(), variable.getExpressionString(), variable.getDefaultValueString(),
                             variable.isAlwaysStopAt());
    }
    myTemplate.parseSegments();
  }

  @Nullable
  private JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBag gb = new GridBag().setDefaultInsets(4, 4, 4, 4).setDefaultWeightY(1).setDefaultFill(GridBagConstraints.BOTH);

    JLabel keyPrompt = new JLabel(CodeInsightBundle.message("dialog.edit.template.label.abbreviation"));
    keyPrompt.setLabelFor(myKeyField);
    panel.add(keyPrompt, gb.nextLine().next());

    panel.add(myKeyField, gb.next().weightx(1));

    JLabel groupPrompt = new JLabel(CodeInsightBundle.message("dialog.edit.template.label.group"));
    groupPrompt.setLabelFor(myGroupCombo);
    panel.add(groupPrompt, gb.next());

    myGroupCombo.setEditable(true);
    panel.add(myGroupCombo, gb.next().weightx(1));

    JLabel descriptionPrompt = new JLabel(CodeInsightBundle.message("dialog.edit.template.label.description"));
    descriptionPrompt.setLabelFor(myDescription);
    panel.add(descriptionPrompt, gb.nextLine().next());

    panel.add(myDescription, gb.next().weightx(1).coverLine(3));
    return panel;
  }

  private JPanel createTemplateOptionsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeInsightBundle.message("dialog.edit.template.options.title"),
                                                        false, true, true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridy = 0;
    panel.add(new JLabel(CodeInsightBundle.message("dialog.edit.template.label.expand.with")), gbConstraints);

    gbConstraints.gridx = 1;
    myExpandByCombo = new JComboBox();
    myExpandByCombo.addItem(myDefaultShortcutItem);
    myExpandByCombo.addItem(SPACE);
    myExpandByCombo.addItem(TAB);
    myExpandByCombo.addItem(ENTER);
    panel.add(myExpandByCombo, gbConstraints);
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 2;
    panel.add(new JPanel(), gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.gridwidth = 3;
    myCbReformat = new JCheckBox(CodeInsightBundle.message("dialog.edit.template.checkbox.reformat.according.to.style"));
    panel.add(myCbReformat, gbConstraints);

    for(TemplateOptionalProcessor processor: myOptions.keySet()) {
      if (!processor.isVisible(myTemplate)) continue;
      gbConstraints.gridy++;
      JCheckBox cb = new JCheckBox(processor.getOptionName());
      panel.add(cb, gbConstraints);
      myCbOptionalProcessorMap.put(processor, cb);
    }

    gbConstraints.weighty = 1;
    gbConstraints.gridy++;
    panel.add(new JPanel(), gbConstraints);          

    return panel;
  }

  private JPanel createShortContextPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    final JLabel ctxLabel = new JLabel();
    final JLabel change = new JLabel();
    change.setForeground(Color.BLUE);
    change.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.add(ctxLabel, BorderLayout.CENTER);
    panel.add(change, BorderLayout.EAST);

    final Runnable updateLabel = new Runnable() {
      public void run() {
        List<String> contexts = new ArrayList<String>();
        for (TemplateContextType type : myContext.keySet()) {
          if (myContext.get(type).booleanValue()) {
            contexts.add(UIUtil.removeMnemonic(type.getPresentableName()));
          }
        }
        ctxLabel.setText((contexts.isEmpty() ? "No applicable contexts yet" : "Applicable in " + StringUtil.join(contexts, ", ")) + ".  ");
        ctxLabel.setForeground(contexts.isEmpty() ? Color.RED : UIUtil.getLabelForeground());
        change.setText(contexts.isEmpty() ? "Define" : "Change");
      }
    };

    change.addMouseListener(new MouseAdapter() {
      private JBPopup myPopup;
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myPopup != null && myPopup.isVisible()) {
          myPopup.cancel();
          myPopup = null;
          return;
        }

        JPanel content = createPopupContextPanel(updateLabel);
        myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null).createPopup();
        myPopup.show(new RelativePoint(change, new Point(change.getWidth() , -content.getPreferredSize().height - 10)));
      }
    });

    updateLabel.run();

    return panel;
  }

  private JPanel createPopupContextPanel(final Runnable onChange) {
    ChangeListener listener = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myExpandByCombo.setEnabled(!isEnabledInStaticContextOnly());
      }

    };

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;

    int row = 0;
    int col = 0;
    for (TemplateContextType contextType : myContext.keySet()) {
      gbConstraints.gridy = row;
      gbConstraints.gridx = col;
      JCheckBox cb = new JCheckBox(contextType.getPresentableName());
      cb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          updateContextTypesEnabledState();
        }
      });
      cb.getModel().addChangeListener(listener);
      panel.add(cb, gbConstraints);
      myCbContextMap.put(contextType, cb);

      if (row == (myContext.size() + 1) / 2 - 1) {
        row = 0;
        col = 1;
      }
      else {
        row++;
      }
    }

    for(JCheckBox checkBox: myCbContextMap.values()) {
      checkBox.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            updateHighlighter();
            onChange.run();
          }
        }
      );
    }

    for(TemplateContextType type: myCbContextMap.keySet()) {
      JCheckBox cb = myCbContextMap.get(type);
      cb.setSelected(myContext.get(type).booleanValue());
    }

    updateContextTypesEnabledState();

    new MnemonicHelper().register(panel);

    return panel;
  }

  private void updateContextTypesEnabledState() {
    for (Map.Entry<TemplateContextType, JCheckBox> entry : myCbContextMap.entrySet()) {
      TemplateContextType contextType = entry.getKey();
      TemplateContextType baseContextType = contextType.getBaseContextType();
      boolean enabled = baseContextType == null || !myCbContextMap.get(baseContextType).isSelected();
      entry.getValue().setEnabled(enabled);
    }
  }

  private boolean isEnabledInStaticContextOnly() {
    for(TemplateContextType type: myCbContextMap.keySet()) {
      final JCheckBox cb = myCbContextMap.get(type);
      if (!type.isExpandableFromEditor()) {
        if (!cb.isSelected()) return false;
      }
      else {
        if (cb.isSelected()) return false;
      }
    }
    return true;
  }

  private void updateHighlighter() {
    TemplateContext templateContext = new TemplateContext();
    updateTemplateContext();
    TemplateEditorUtil.setHighlighter(myTemplateEditor, templateContext);
    ((EditorEx) myTemplateEditor).repaint(0, myTemplateEditor.getDocument().getTextLength());
  }

  private void validateEditVariablesButton() {
    myEditVariablesButton.setEnabled(!parseVariables(myTemplateEditor.getDocument().getCharsSequence(), false).isEmpty());
  }

  private void validateOKButton() {
    boolean isEnabled = true;
    if(myKeyField.getText().trim().length() == 0) {
      isEnabled = false;
    }
    if(myTemplateEditor.getDocument().getTextLength() == 0) {
      isEnabled = false;
    }
  }

  public void reset() {
    myKeyField.setText(myTemplate.getKey());
    myDescription.setText(myTemplate.getDescription());

    if(myTemplate.getShortcutChar() == TemplateSettings.DEFAULT_CHAR) {
      myExpandByCombo.setSelectedItem(myDefaultShortcutItem);
    }
    else if(myTemplate.getShortcutChar() == TemplateSettings.TAB_CHAR) {
      myExpandByCombo.setSelectedItem(TAB);
    }
    else if(myTemplate.getShortcutChar() == TemplateSettings.ENTER_CHAR) {
      myExpandByCombo.setSelectedItem(ENTER);
    }
    else {
      myExpandByCombo.setSelectedItem(SPACE);
    }

    CommandProcessor.getInstance().executeCommand(
        null, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final Document document = myTemplateEditor.getDocument();
              document.replaceString(0, document.getTextLength(), myTemplate.getString());
            }
          });
        }
      },
      "",
      null
    );

    Set<String> groups = new TreeSet<String>();
    SchemesManager<TemplateGroup, TemplateGroup> schemesManager = TemplateSettings.getInstance().getSchemesManager();
    for (TemplateGroup group : myTemplateGroups) {
      if (!schemesManager.isShared(group)) {
        groups.add(group.getName());
      }
    }

    for (final Object group : groups) {
      String groupName = (String)group;
      myGroupCombo.addItem(groupName);
    }

    myGroupCombo.setSelectedItem(myTemplate.getGroupName());

    myCbReformat.setSelected(myTemplate.isToReformat());

    for(TemplateOptionalProcessor processor: myCbOptionalProcessorMap.keySet()) {
      JCheckBox cb = myCbOptionalProcessorMap.get(processor);
      cb.setSelected(myOptions.get(processor).booleanValue());

    }
    myExpandByCombo.setEnabled(!isEnabledInStaticContextOnly());

    updateHighlighter();
    validateOKButton();
    validateEditVariablesButton();
  }

  public void apply() {
    myTemplate.setKey(myKeyField.getText().trim());
    myTemplate.setDescription(myDescription.getText().trim());
    myTemplate.setGroupName(((String)myGroupCombo.getSelectedItem()).trim());

    Object selectedItem = myExpandByCombo.getSelectedItem();
    if(myDefaultShortcutItem.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.DEFAULT_CHAR);
    }
    else if(TAB.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.TAB_CHAR);
    }
    else if(ENTER.equals(selectedItem)) {
      myTemplate.setShortcutChar(TemplateSettings.ENTER_CHAR);
    }
    else {
      myTemplate.setShortcutChar(TemplateSettings.SPACE_CHAR);
    }

    updateTemplateContext();

    myTemplate.setToReformat(myCbReformat.isSelected());
    for(TemplateOptionalProcessor option: myCbOptionalProcessorMap.keySet()) {
      JCheckBox cb = myCbOptionalProcessorMap.get(option);
      myOptions.put(option, cb.isSelected());
    }

  }

  private void updateTemplateContext() {
    for(TemplateContextType type: myCbContextMap.keySet()) {
      JCheckBox cb = myCbContextMap.get(type);
      myContext.put(type, cb.isSelected());
    }
  }

  private void editVariables() {
    ArrayList<Variable> newVariables = updateVariablesByTemplateText();

    EditVariableDialog editVariableDialog = new EditVariableDialog(myTemplateEditor, myEditVariablesButton, newVariables);
    editVariableDialog.show();
    if (editVariableDialog.isOK()) {
      applyVariables(newVariables);
    }
  }

  private ArrayList<Variable> updateVariablesByTemplateText() {
    List<Variable> oldVariables = getCurrentVariables();
    
    Set<String> oldVariableNames = ContainerUtil.map2Set(oldVariables, new Function<Variable, String>() {
      @Override
      public String fun(Variable variable) {
        return variable.getName();
      }
    });
    

    ArrayList<Variable> parsedVariables = parseVariables(myTemplateEditor.getDocument().getCharsSequence(), false);

    Map<String,String> newVariableNames = new HashMap<String, String>();
    for (Object parsedVariable : parsedVariables) {
      Variable newVariable = (Variable)parsedVariable;
      String name = newVariable.getName();
      newVariableNames.put(name, name);
    }

    int oldVariableNumber = 0;
    for(int i = 0; i < parsedVariables.size(); i++){
      Variable variable = parsedVariables.get(i);
      if(oldVariableNames.contains(variable.getName())) {
        Variable oldVariable = null;
        for(;oldVariableNumber<oldVariables.size(); oldVariableNumber++) {
          oldVariable = oldVariables.get(oldVariableNumber);
          if(newVariableNames.get(oldVariable.getName()) != null) {
            break;
          }
          oldVariable = null;
        }
        oldVariableNumber++;
        if(oldVariable != null) {
          parsedVariables.set(i, oldVariable);
        }
      }
    }

    return parsedVariables;
  }

  private List<Variable> getCurrentVariables() {
    List<Variable> myVariables = new ArrayList<Variable>();

    for(int i = 0; i < myTemplate.getVariableCount(); i++) {
      myVariables.add(new Variable(myTemplate.getVariableNameAt(i),
                                   myTemplate.getExpressionStringAt(i),
                                   myTemplate.getDefaultValueStringAt(i),
                                   myTemplate.isAlwaysStopAt(i)));
    }
    return myVariables;
  }

  private static ArrayList<Variable> parseVariables(CharSequence text, boolean includeInternal) {
    ArrayList<Variable> variables = new ArrayList<Variable>();
    TemplateImplUtil.parseVariables(text, variables, TemplateImpl.INTERNAL_VARS_SET);
    if (!includeInternal) {
      for (Iterator<Variable> iterator = variables.iterator(); iterator.hasNext(); ) {
        if (TemplateImpl.INTERNAL_VARS_SET.contains(iterator.next().getName())) {
          iterator.remove();
        }
      }
    }
    return variables;
  }

  /* todo
  protected void doOKAction() {
    String key = myKeyField.getText().trim();

    final String newGroup = (String)myGroupCombo.getSelectedItem();

    for (TemplateGroup templateGroup : myTemplateGroups) {
      if (templateGroup.getName().equals(newGroup)) {
        for (TemplateImpl template : templateGroup.getElements()) {
          if (template.getKey().equals(key) && myTemplate != template) {
            Messages.showMessageDialog(getContentPane(),
                                       CodeInsightBundle.message("dialog.edit.template.error.already.exists", key, template.getGroupName()),
                                       CodeInsightBundle.message("dialog.edit.template.error.title"), Messages.getErrorIcon());
            return;
          }
        }
      }
    }

    if (!TemplateImplUtil.validateTemplateText(myTemplateEditor.getDocument().getText())) {
      Messages.showMessageDialog (
          getContentPane(),
          CodeInsightBundle.message("dialog.edit.template.error.malformed.template"),
          CodeInsightBundle.message("dialog.edit.template.error.title"),
          Messages.getErrorIcon()
      );
      return;
    }

    SchemesManager<TemplateGroup, TemplateGroup> schemesManager = TemplateSettings.getInstance().getSchemesManager();
    TemplateGroup group = schemesManager.findSchemeByName(newGroup);
    if (group != null && schemesManager.isShared(group)) {
      Messages.showMessageDialog (
          getContentPane(),
          "Group " + group.getName() + " is shared, cannot be modified",
          CodeInsightBundle.message("dialog.edit.template.error.title"),
          Messages.getErrorIcon()
      );
      return;

    }

    boolean anyChecked = false;
    for (JCheckBox contextCheckBox : myCbContextMap.values()) {
      if (contextCheckBox.isSelected()) {
        anyChecked = true;
        break;
      }
    }
    if (!anyChecked) {
      Messages.showMessageDialog(getContentPane(),
                                 "Please enable at least one context checkbox. Otherwise the live template will never ber active.",
                                 CodeInsightBundle.message("dialog.edit.template.error.title"),
                                 Messages.getErrorIcon()
                                 );
      return;
    }

    super.doOKAction();
  }
  */
}


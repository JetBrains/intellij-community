/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.EverywhereContextType;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class LiveTemplateSettingsEditor extends JPanel {
  private final TemplateImpl myTemplate;

  private final JTextField myKeyField;
  private final JTextField myDescription;
  private final Editor myTemplateEditor;

  private JComboBox myExpandByCombo;
  private final String myDefaultShortcutItem;
  private JCheckBox myCbReformat;

  private JButton myEditVariablesButton;

  private static final String SPACE = CodeInsightBundle.message("template.shortcut.space");
  private static final String TAB = CodeInsightBundle.message("template.shortcut.tab");
  private static final String ENTER = CodeInsightBundle.message("template.shortcut.enter");
  private final Map<TemplateOptionalProcessor, Boolean> myOptions;
  private final Map<TemplateContextType, Boolean> myContext;
  private JBPopup myContextPopup;
  private Dimension myLastSize;

  public LiveTemplateSettingsEditor(TemplateImpl template,
                                    final String defaultShortcut,
                                    Map<TemplateOptionalProcessor, Boolean> options,
                                    Map<TemplateContextType, Boolean> context, final Runnable nodeChanged, boolean allowNoContext) {
    super(new BorderLayout());
    myOptions = options;
    myContext = context;

    myTemplate = template;
    myDefaultShortcutItem = CodeInsightBundle.message("dialog.edit.template.shortcut.default", defaultShortcut);

    myKeyField=new JTextField();
    myDescription=new JTextField();
    myTemplateEditor = TemplateEditorUtil.createEditor(false, myTemplate.getString(), context);
    myTemplate.setId(null);

    createComponents(allowNoContext);
    
    reset();

    com.intellij.ui.DocumentAdapter listener = new com.intellij.ui.DocumentAdapter() {
      @Override
      protected void textChanged(javax.swing.event.DocumentEvent e) {
        myTemplate.setKey(myKeyField.getText().trim());
        myTemplate.setDescription(myDescription.getText().trim());
        nodeChanged.run();
      }
    };
    myKeyField.getDocument().addDocumentListener(listener);
    myDescription.getDocument().addDocumentListener(listener);

    new UiNotifyConnector(this, new Activatable.Adapter() {
      @Override
      public void hideNotify() {
        disposeContextPopup();
      }
    });

  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  public void dispose() {
    final Project project = myTemplateEditor.getProject();
    if (project != null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(myTemplateEditor.getDocument());
      if (psiFile != null) {
        DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true);
      }
    }
    EditorFactory.getInstance().releaseEditor(myTemplateEditor);
  }

  private void createComponents(boolean allowNoContexts) {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBag gb = new GridBag().setDefaultInsets(4, 4, 4, 4).setDefaultWeightY(1).setDefaultFill(GridBagConstraints.BOTH);
    
    JPanel editorPanel = new JPanel(new BorderLayout(4, 4));
    editorPanel.setPreferredSize(new Dimension(250, 100));
    editorPanel.setMinimumSize(editorPanel.getPreferredSize());
    editorPanel.add(myTemplateEditor.getComponent(), BorderLayout.CENTER);
    JLabel templateTextLabel = new JLabel(CodeInsightBundle.message("dialog.edit.template.template.text.title"));
    templateTextLabel.setLabelFor(myTemplateEditor.getContentComponent());
    editorPanel.add(templateTextLabel, BorderLayout.NORTH);
    panel.add(editorPanel, gb.nextLine().next().weighty(1).weightx(1).coverColumn(2));

    myEditVariablesButton = new JButton(CodeInsightBundle.message("dialog.edit.template.button.edit.variables"));
    myEditVariablesButton.setDefaultCapable(false);
    myEditVariablesButton.setMaximumSize(myEditVariablesButton.getPreferredSize());
    panel.add(myEditVariablesButton, gb.next().weighty(0));

    panel.add(createTemplateOptionsPanel(), gb.nextLine().next().next().coverColumn(2).weighty(1));

    panel.add(createShortContextPanel(allowNoContexts), gb.nextLine().next().weighty(0).fillCellNone().anchor(GridBagConstraints.WEST));

    myTemplateEditor.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          validateEditVariablesButton();

          myTemplate.setString(myTemplateEditor.getDocument().getText());
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

    add(createNorthPanel(), BorderLayout.NORTH);
    add(panel, BorderLayout.CENTER);
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

    JLabel descriptionPrompt = new JLabel(CodeInsightBundle.message("dialog.edit.template.label.description"));
    descriptionPrompt.setLabelFor(myDescription);
    panel.add(descriptionPrompt, gb.next());

    panel.add(myDescription, gb.next().weightx(3));
    return panel;
  }

  private JPanel createTemplateOptionsPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder(CodeInsightBundle.message("dialog.edit.template.options.title"),
                                                        true));
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridy = 0;
    JLabel expandWithLabel = new JLabel(CodeInsightBundle.message("dialog.edit.template.label.expand.with"));
    panel.add(expandWithLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.insets = new Insets(0, 4, 0, 0);
    myExpandByCombo = new JComboBox(new Object[]{myDefaultShortcutItem, SPACE, TAB, ENTER});
    myExpandByCombo.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
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
        
      }
    });
    expandWithLabel.setLabelFor(myExpandByCombo);
    
    panel.add(myExpandByCombo, gbConstraints);
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 2;
    panel.add(new JPanel(), gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.gridwidth = 3;
    myCbReformat = new JCheckBox(CodeInsightBundle.message("dialog.edit.template.checkbox.reformat.according.to.style"));
    panel.add(myCbReformat, gbConstraints);

    for (final TemplateOptionalProcessor processor: myOptions.keySet()) {
      if (!processor.isVisible(myTemplate)) continue;
      gbConstraints.gridy++;
      final JCheckBox cb = new JCheckBox(processor.getOptionName());
      panel.add(cb, gbConstraints);
      cb.setSelected(myOptions.get(processor).booleanValue());
      cb.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          myOptions.put(processor, cb.isSelected());
        }
      });
    }

    gbConstraints.weighty = 1;
    gbConstraints.gridy++;
    panel.add(new JPanel(), gbConstraints);          

    return panel;
  }

  private List<TemplateContextType> getApplicableContexts() {
    ArrayList<TemplateContextType> result = new ArrayList<TemplateContextType>();
    for (TemplateContextType type : myContext.keySet()) {
      if (myContext.get(type).booleanValue()) {
        result.add(type);
      }
    }
    return result;
  }

  private JPanel createShortContextPanel(final boolean allowNoContexts) {
    JPanel panel = new JPanel(new BorderLayout());

    final JLabel ctxLabel = new JLabel();
    final JLabel change = new JLabel();
    change.setForeground(Color.BLUE);
    change.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.add(ctxLabel, BorderLayout.CENTER);
    panel.add(change, BorderLayout.EAST);

    final Runnable updateLabel = new Runnable() {
      public void run() {
        StringBuilder sb = new StringBuilder();
        String oldPrefix = "";
        for (TemplateContextType type : getApplicableContexts()) {
          final TemplateContextType base = type.getBaseContextType();
          String ownName = UIUtil.removeMnemonic(type.getPresentableName());
          String prefix = "";
          if (base != null && !(base instanceof EverywhereContextType)) {
            prefix = UIUtil.removeMnemonic(base.getPresentableName()) + ": ";
            ownName = StringUtil.decapitalize(ownName);
          }
          if (sb.length() > 0) {
            sb.append(oldPrefix.equals(prefix) ? ", " : "; ");
          }
          if (!oldPrefix.equals(prefix)) {
            sb.append(prefix);
            oldPrefix = prefix;
          }
          sb.append(ownName);
        }
        final boolean noContexts = sb.length() == 0;
        ctxLabel.setText((noContexts ? "No applicable contexts" + (allowNoContexts ? "" : " yet") : "Applicable in " + sb.toString()) + ".  ");
        ctxLabel.setForeground(noContexts ? allowNoContexts ? Color.GRAY : Color.RED : UIUtil.getLabelForeground());
        change.setText(noContexts ? "Define" : "Change");
      }
    };

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (disposeContextPopup()) return false;

        final JPanel content = createPopupContextPanel(updateLabel);
        Dimension prefSize = content.getPreferredSize();
        if (myLastSize != null && (myLastSize.width > prefSize.width || myLastSize.height > prefSize.height)) {
          content.setPreferredSize(new Dimension(Math.max(prefSize.width, myLastSize.width), Math.max(prefSize.height, myLastSize.height)));
        }
        myContextPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null).setResizable(true).createPopup();
        myContextPopup.show(new RelativePoint(change, new Point(change.getWidth() , -content.getPreferredSize().height - 10)));
        myContextPopup.addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            myLastSize = content.getSize();
          }
        });
        return true;
      }
    }.installOn(change);

    updateLabel.run();

    return panel;
  }

  private boolean disposeContextPopup() {
    if (myContextPopup != null && myContextPopup.isVisible()) {
      myContextPopup.cancel();
      myContextPopup = null;
      return true;
    }
    return false;
  }

  private JPanel createPopupContextPanel(final Runnable onChange) {
    JPanel panel = new JPanel(new BorderLayout());

    MultiMap<TemplateContextType, TemplateContextType> hierarchy = new MultiMap<TemplateContextType, TemplateContextType>() {
      @Override
      protected Map<TemplateContextType, Collection<TemplateContextType>> createMap() {
        return new LinkedHashMap<TemplateContextType, Collection<TemplateContextType>>();
      }
    };
    for (TemplateContextType type : myContext.keySet()) {
      hierarchy.putValue(type.getBaseContextType(), type);
    }

    final CheckedTreeNode root = new CheckedTreeNode(Pair.create(null, "Hi"));
    final CheckboxTree checkboxTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        final Object o = ((DefaultMutableTreeNode)value).getUserObject();
        if (o instanceof Pair) {
          getTextRenderer().append((String)((Pair)o).second);
        }
      }
    }, root) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        final TemplateContextType type = (TemplateContextType)((Pair)node.getUserObject()).first;
        if (type != null) {
          myContext.put(type, node.isChecked());
        }
        myExpandByCombo.setEnabled(isExpandableFromEditor());
        updateHighlighter();
        onChange.run();

      }
    };

    for (TemplateContextType type : hierarchy.get(null)) {
      addContextNode(hierarchy, root, type);
    }

    ((DefaultTreeModel)checkboxTree.getModel()).nodeStructureChanged(root);

    TreeUtil.traverse(root, new TreeUtil.Traverse() {
      @Override
      public boolean accept(Object _node) {
        final CheckedTreeNode node = (CheckedTreeNode)_node;
        if (node.isChecked()) {
          checkboxTree.expandPath(new TreePath(node.getPath()).getParentPath());
        }
        return true;
      }
    });

    panel.add(ScrollPaneFactory.createScrollPane(checkboxTree));
    final Dimension size = checkboxTree.getPreferredSize();
    panel.setPreferredSize(new Dimension(size.width + 30, Math.min(size.height + 10, 500)));

    return panel;
  }

  private void addContextNode(MultiMap<TemplateContextType, TemplateContextType> hierarchy,
                              CheckedTreeNode parent,
                              TemplateContextType type) {
    final Collection<TemplateContextType> children = hierarchy.get(type);
    final String name = UIUtil.removeMnemonic(type.getPresentableName());
    final CheckedTreeNode node = new CheckedTreeNode(Pair.create(children.isEmpty() ? type : null, name));
    parent.add(node);

    if (children.isEmpty()) {
      node.setChecked(myContext.get(type));
    }
    else {
      for (TemplateContextType child : children) {
        addContextNode(hierarchy, node, child);
      }
      final CheckedTreeNode other = new CheckedTreeNode(Pair.create(type, "Other"));
      other.setChecked(myContext.get(type));
      node.add(other);
    }
  }

  private boolean isExpandableFromEditor() {
    boolean hasNonExpandable = false;
    for (TemplateContextType type : getApplicableContexts()) {
      if (type.isExpandableFromEditor()) {
        return true;
      }
      hasNonExpandable = true;
    }
    
    return !hasNonExpandable;
  }

  private void updateHighlighter() {
    List<TemplateContextType> applicableContexts = getApplicableContexts();
    if (!applicableContexts.isEmpty()) {
      TemplateContext contextByType = new TemplateContext();
      contextByType.setEnabled(applicableContexts.get(0), true);
      TemplateEditorUtil.setHighlighter(myTemplateEditor, contextByType);
      return;
    }
    ((EditorEx) myTemplateEditor).repaint(0, myTemplateEditor.getDocument().getTextLength());
  }

  private void validateEditVariablesButton() {
    myEditVariablesButton.setEnabled(!parseVariables(myTemplateEditor.getDocument().getCharsSequence()).isEmpty());
  }

  private void reset() {
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

    myCbReformat.setSelected(myTemplate.isToReformat());
    myCbReformat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTemplate.setToReformat(myCbReformat.isSelected());
      }
    });

    myExpandByCombo.setEnabled(isExpandableFromEditor());

    updateHighlighter();
    validateEditVariablesButton();
  }

  private void editVariables() {
    ArrayList<Variable> newVariables = updateVariablesByTemplateText();

    EditVariableDialog editVariableDialog = new EditVariableDialog(myTemplateEditor, myEditVariablesButton, newVariables, getApplicableContexts());
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
    

    ArrayList<Variable> parsedVariables = parseVariables(myTemplateEditor.getDocument().getCharsSequence());

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

  public JTextField getKeyField() {
    return myKeyField;
  }

  public void focusKey() {
    myKeyField.selectAll();
    //todo[peter,kirillk] without these invokeLaters this requestFocus conflicts with com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.MyDialog.MyWindowListener.windowOpened()
    IdeFocusManager.findInstanceByComponent(myKeyField).requestFocus(myKeyField, true);
    final ModalityState modalityState = ModalityState.stateForComponent(myKeyField);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                IdeFocusManager.findInstanceByComponent(myKeyField).requestFocus(myKeyField, true);
              }
            }, modalityState);
          }
        }, modalityState);
      }
    }, modalityState);
  }

  private static ArrayList<Variable> parseVariables(CharSequence text) {
    ArrayList<Variable> variables = new ArrayList<Variable>();
    TemplateImplUtil.parseVariables(text, variables, TemplateImpl.INTERNAL_VARS_SET);
    return variables;
  }

}


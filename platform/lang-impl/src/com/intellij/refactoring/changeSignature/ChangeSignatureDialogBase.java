/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.DelegationPanel;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public abstract class ChangeSignatureDialogBase<P extends ParameterInfo, M extends PsiElement, D extends MethodDescriptor<P>>
  extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase");
  protected final D myMethod;
  private final boolean myAllowDelegation;
  private EditorTextField myNameField;
  protected EditorTextField myReturnTypeField;
  protected JTable myParametersTable;
  protected final ParameterTableModelBase<P> myParametersTableModel;
  private JTextArea mySignatureArea;
  private final Alarm myUpdateSignatureAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private VisibilityPanelBase myVisibilityPanel;
  protected PsiCodeFragment myReturnTypeCodeFragment;
  private DelegationPanel myDelegationPanel;
  private JButton myPropagateParamChangesButton;
  protected Set<M> myMethodsToPropagateParameters = null;

  private Tree myParameterPropagationTreeToReuse;
  protected JPanel myPropagatePanel;

  protected final PsiElement myDefaultValueContext;

  protected abstract LanguageFileType getFileType();

  protected abstract ParameterTableModelBase<P> createParametersInfoModel(MethodDescriptor<P> method);

  protected abstract BaseRefactoringProcessor createRefactoringProcessor();

  protected abstract PsiCodeFragment createReturnTypeCodeFragment();

  protected abstract CallerChooserBase<M> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<M>> callback);

  protected abstract TableCellEditor createTypeCellEditor();

  protected abstract TableCellEditor createNameCellEditor();

  protected abstract TableCellEditor createDefaultValueCellEditor();

  protected abstract String validateAndCommitData();

  protected abstract boolean isResolvableType(P info, MethodDescriptor<P> method);

  public ChangeSignatureDialogBase(Project project, final D method, boolean allowDelegation, PsiElement defaultValueContext) {
    super(project, true);
    myMethod = method;
    myDefaultValueContext = defaultValueContext;
    myParametersTableModel = createParametersInfoModel(method);
    myAllowDelegation = allowDelegation;

    setParameterInfos(method.getParameters());

    setTitle(ChangeSignatureHandler.REFACTORING_NAME);
    init();
    doUpdateSignature();
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        myUpdateSignatureAlarm.cancelAllRequests();
      }
    });
  }

  public void setParameterInfos(List<P> parameterInfos) {
    myParametersTableModel.setParameterInfos(parameterInfos);
    updateSignature();
  }

  protected String getMethodName() {
    if (myNameField != null) {
      return myNameField.getText().trim();
    }
    else {
      return myMethod.getName();
    }
  }

  protected String getVisibility() {
    if (myVisibilityPanel != null) {
      return myVisibilityPanel.getVisibility();
    }
    else {
      return myMethod.getVisibility();
    }
  }

  public P[] getParameters() {
    return myParametersTableModel.getParameters();
  }

  public boolean isGenerateDelegate() {
    return myAllowDelegation && myDelegationPanel.isGenerateDelegate();
  }

  public JComponent getPreferredFocusedComponent() {
    return myParametersTableModel.getRowCount() > 0 ? myParametersTable : myNameField;
  }


  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));

    JPanel top = new JPanel(new BorderLayout());
    if (myAllowDelegation) {
      myDelegationPanel = createDelegationPanel();
      top.add(myDelegationPanel, BorderLayout.WEST);
    }

    myPropagateParamChangesButton = new JButton(RefactoringBundle.message("changeSignature.propagate.parameters.title"));
    myPropagateParamChangesButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Ref<CallerChooserBase<M>> chooser = new Ref<CallerChooserBase<M>>();
        Consumer<Set<M>> callback = new Consumer<Set<M>>() {
          @Override
          public void consume(Set<M> callers) {
            myMethodsToPropagateParameters = callers;
            myParameterPropagationTreeToReuse = chooser.get().getTree();
          }
        };
        chooser.set(
          createCallerChooser(RefactoringBundle.message("changeSignature.parameter.caller.chooser"), myParameterPropagationTreeToReuse,
                              callback));
        chooser.get().show();
      }
    });
    myPropagatePanel = new JPanel();
    myPropagatePanel.add(myPropagateParamChangesButton);

    top.add(myPropagatePanel, BorderLayout.EAST);

    panel.add(top);
    if (!myMethod.isConstructor()) {
      JLabel namePrompt = new JLabel();
      myNameField = new EditorTextField(myMethod.getName());
      namePrompt.setText(RefactoringBundle.message("name.prompt"));
      namePrompt.setLabelFor(myNameField);
      panel.add(namePrompt);
      panel.add(myNameField);

      JLabel typePrompt = new JLabel();
      panel.add(typePrompt);
      final String returnTypeText = myMethod.getReturnTypeText();
      if (returnTypeText == null) {
        LOG.error(myMethod.getClass().getName());
        return panel;
      }
      myReturnTypeCodeFragment = createReturnTypeCodeFragment();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
      myReturnTypeField = new EditorTextField(document, myProject, getFileType());
      typePrompt.setText(RefactoringBundle.message("changeSignature.return.type.prompt"));
      typePrompt.setLabelFor(myReturnTypeField);
      panel.add(myReturnTypeField);

      final DocumentListener documentListener = new DocumentListener() {
        public void beforeDocumentChange(DocumentEvent event) {
        }

        public void documentChanged(DocumentEvent event) {
          updateSignature();
        }
      };
      myNameField.addDocumentListener(documentListener);
      myReturnTypeField.addDocumentListener(documentListener);
    }

    return panel;
  }

  private DelegationPanel createDelegationPanel() {
    return new DelegationPanel() {
      protected void stateModified() {
        myParametersTableModel.fireTableDataChanged();
        configureParameterTableEditors();
      }
    };
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel subPanel = new JPanel(new BorderLayout());
    subPanel.add(createParametersPanel(), BorderLayout.CENTER);

    if (myMethod.canChangeVisibility()) {
      JPanel visibilityPanel = createVisibilityPanel(subPanel);
      subPanel.add(visibilityPanel, BorderLayout.EAST);
    }

    panel.add(subPanel, BorderLayout.CENTER);

    JPanel subPanel1 = new JPanel(new GridBagLayout());
    JPanel additionalPanel = createAdditionalPanel();
    int gridX = 0;
    if (additionalPanel != null) {
      subPanel1.add(additionalPanel,
                    new GridBagConstraints(gridX++, 0, 1, 1, 0.5, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                           new Insets(4, 4, 4, 0), 0, 0));
    }

    subPanel1.add(createSignaturePanel(),
                  new GridBagConstraints(gridX, 0, 1, 1, 0.5, 0.0, GridBagConstraints.EAST, GridBagConstraints.BOTH, new Insets(4, 0, 4, 4),
                                         0, 0));
    panel.add(subPanel1, BorderLayout.SOUTH);

    return panel;
  }

  protected JPanel createVisibilityPanel(JPanel subPanel) {
    myVisibilityPanel = createVisibilityControl();
    myVisibilityPanel.setVisibility(myMethod.getVisibility());
    myVisibilityPanel.addListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateSignature();
      }
    });
    return myVisibilityPanel;
  }

  protected abstract VisibilityPanelBase createVisibilityControl();

  @Nullable
  protected JPanel createAdditionalPanel() {
    return null;
  }

  protected String getDimensionServiceKey() {
    return "refactoring.ChangeSignatureDialog";
  }

  private JPanel createParametersPanel() {
    myParametersTable = new JBTable(myParametersTableModel) {
      @Override
      public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);
        repaint(); // to update disabled cells background
      }
    };
    myParametersTable.setCellSelectionEnabled(true);
    //myParametersTable.setFocusCycleRoot(true);
    final int minWidth = new JCheckBox().getPreferredSize().width;
    if (myParametersTable.getColumnCount() > 3) {
      final TableColumn anyVarColumn = myParametersTable.getColumnModel().getColumn(3);
      final int headerWidth =
        myParametersTable.getFontMetrics(myParametersTable.getFont()).stringWidth(RefactoringBundle.message("column.name.any.var")) + 8;
      anyVarColumn.setMaxWidth(Math.max(minWidth, headerWidth));
    }
    configureParameterTableEditors();
    return createTablePanelImpl(myParametersTable, myParametersTableModel, RefactoringBundle.message("parameters.border.title"), true);
  }

  protected JPanel createTablePanelImpl(JTable table, RowEditableTableModel tableModel, String borderTitle, boolean addMnemonics) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(scrollPane, BorderLayout.CENTER);

    tablePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.add(tablePanel, BorderLayout.CENTER);

    table.setPreferredScrollableViewportSize(new Dimension(450, table.getRowHeight() * 8));
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

    JPanel buttonsPanel = EditableRowTable.createButtonsTable(table, tableModel, addMnemonics);

    panel.add(buttonsPanel, BorderLayout.EAST);

    tableModel.addTableModelListener(
      new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          updateSignature();
        }
      }
    );

    return panel;
  }

  private void configureParameterTableEditors() {
    myParametersTable.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, getFileType()));
    myParametersTable.getColumnModel().getColumn(1).setCellRenderer(new MyCellRenderer());
    if (myParametersTable.getColumnCount() > 2) {
      myParametersTable.getColumnModel().getColumn(2).setCellRenderer(new CodeFragmentTableCellRenderer(myProject, getFileType()) {

        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!myParametersTableModel.isCellEditable(row, myParametersTable.convertColumnIndexToModel(column))) {
            component.setBackground(myParametersTable.getBackground().darker());
          }
          return component;
        }
      });
    }
    if (myParametersTable.getColumnCount() > 3) {
      myParametersTable.getColumnModel().getColumn(3).setCellRenderer(new BooleanTableCellRenderer() {
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!myParametersTableModel.isCellEditable(row, myParametersTable.convertColumnIndexToModel(column))) {
            setBackground(myParametersTable.getBackground().darker());
          }
          return this;
        }
      });
    }
    myParametersTable.getColumnModel().getColumn(0).setCellEditor(createTypeCellEditor());
    myParametersTable.getColumnModel().getColumn(1).setCellEditor(createNameCellEditor());
    if (myParametersTable.getColumnCount() > 2) {
      myParametersTable.getColumnModel().getColumn(2).setCellEditor(createDefaultValueCellEditor());
    }
  }

  private JComponent createSignaturePanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createCompoundBorder(
      IdeBorderFactory.createTitledBorder(RefactoringBundle.message("signature.preview.border.title")),
      IdeBorderFactory.createEmptyBorder(new Insets(4, 4, 4, 4))));

    String s = calculateSignature();
    s = StringUtil.convertLineSeparators(s);
    int height = new StringTokenizer(s, "\n\r").countTokens() + 2;
    if (height > 10) height = 10;
    mySignatureArea = new JTextArea(height, 50);
    mySignatureArea.setEditable(false);
    mySignatureArea.setBackground(getContentPane().getBackground());
    //mySignatureArea.setFont(myTableFont);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(mySignatureArea);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder(new Insets(0, 0, 0, 0)));
    panel.add(scrollPane, BorderLayout.CENTER);

    updateSignature();
    return panel;
  }

  protected void updateSignature() {
    if (mySignatureArea == null) return;

    final Runnable updateRunnable = new Runnable() {
      public void run() {
        myUpdateSignatureAlarm.cancelAllRequests();
        myUpdateSignatureAlarm.addRequest(new Runnable() {
          public void run() {
            doUpdateSignature();
            updatePropagateButtons();
          }
        }, 100, ModalityState.stateForComponent(mySignatureArea));
      }
    };
    SwingUtilities.invokeLater(updateRunnable);
  }

  private void doUpdateSignature() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    String signature = calculateSignature();
    mySignatureArea.setText(signature);
  }

  protected void updatePropagateButtons() {
    myPropagateParamChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateParameters());
  }

  private boolean mayPropagateParameters() {
    final P[] infos = myParametersTableModel.getParameters();
    if (infos.length <= myMethod.getParametersCount()) return false;
    for (int i = 0; i < myMethod.getParametersCount(); i++) {
      if (infos[i].getOldIndex() != i) return false;
    }
    return true;
  }

  protected abstract String calculateSignature();

  protected void doAction() {
    TableUtil.stopEditing(myParametersTable);
    String message = validateAndCommitData();
    if (message != null) {
      CommonRefactoringUtil
        .showErrorMessage(RefactoringBundle.message("error.incorrect.data"), message, "refactoring.changeSignature", myProject);
      return;
    }
    if (!checkMethodConflicts()) {
      return;
    }

    if (myMethodsToPropagateParameters != null && !mayPropagateParameters()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.parameters.wont.propagate"),
                                 ChangeSignatureHandler.REFACTORING_NAME);
      myMethodsToPropagateParameters = null;
    }

    invokeRefactoring(createRefactoringProcessor());
  }

  private boolean checkMethodConflicts() {
    P[] parameters = getParameters();

    for (P info : parameters) {
      if (!isResolvableType(info, myMethod)) {
        final int ret = Messages.showOkCancelDialog(myProject,
                                                    RefactoringBundle.message("changeSignature.cannot.resolve.type", info.getTypeText()),
                                                    ChangeSignatureHandler.REFACTORING_NAME, Messages.getErrorIcon());
        if (ret != 0) return false;
      }

    }
    return true;
  }

  @Override
  protected String getHelpId() {
    return "refactoring.changeSignature";
  }

  private class MyCellRenderer extends ColoredTableCellRenderer {

    public void customizeCellRenderer(JTable table, Object value,
                                      boolean isSelected, boolean hasFocus, int row, int column) {
      if (value == null) return;
      if (!myParametersTableModel.isCellEditable(row, myParametersTable.convertColumnIndexToModel(column))) {
        setBackground(getBackground().darker());
      }
      append((String)value, new SimpleTextAttributes(Font.PLAIN, null));
    }
  }
}

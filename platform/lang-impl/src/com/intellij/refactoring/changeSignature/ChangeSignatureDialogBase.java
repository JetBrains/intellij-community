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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ChangeSignatureDialogBase<P extends ParameterInfo, M extends PsiElement, D extends MethodDescriptor<P>>
  extends RefactoringDialog {

  protected static final String EXIT_SILENTLY = "";

  protected final D myMethod;
  private final boolean myAllowDelegation;
  protected EditorTextField myNameField;
  protected EditorTextField myReturnTypeField;
  protected TableView<ParameterTableModelItemBase<P>> myParametersTable;
  protected final ParameterTableModelBase<P> myParametersTableModel;
  private EditorTextField mySignatureArea;
  private final Alarm myUpdateSignatureAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  protected VisibilityPanelBase myVisibilityPanel;
  protected PsiCodeFragment myReturnTypeCodeFragment;
  private DelegationPanel myDelegationPanel;
  protected AnActionButton myPropagateParamChangesButton;
  protected Set<M> myMethodsToPropagateParameters = null;

  private Tree myParameterPropagationTreeToReuse;

  protected final PsiElement myDefaultValueContext;

  protected abstract LanguageFileType getFileType();

  protected abstract ParameterTableModelBase<P> createParametersInfoModel(MethodDescriptor<P> method);

  protected abstract BaseRefactoringProcessor createRefactoringProcessor();

  protected abstract PsiCodeFragment createReturnTypeCodeFragment();

  protected abstract CallerChooserBase<M> createCallerChooser(String title, Tree treeToReuse, Consumer<Set<M>> callback);

  protected abstract String validateAndCommitData();

  protected abstract String calculateSignature();

  protected abstract VisibilityPanelBase createVisibilityControl();

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

  public List<P> getParameters() {
    List<P> result = new ArrayList<P>(myParametersTableModel.getRowCount());
    for (ParameterTableModelItemBase<P> item : myParametersTableModel.getItems()) {
      result.add(item.parameter);
    }
    return result;
  }

  public boolean isGenerateDelegate() {
    return myAllowDelegation && myDelegationPanel.isGenerateDelegate();
  }

  public JComponent getPreferredFocusedComponent() {
    if (myParametersTableModel.getRowCount() > 0) {
      final JTable table = myParametersTable.getComponent();
      if (table.getColumnModel().getSelectedColumnCount() == 0) {
        table.getSelectionModel().setSelectionInterval(0,0);
        table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);
      }
      return table;
    } else {
      return myNameField == null ? super.getPreferredFocusedComponent() : myNameField;
    }
  }


  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    final JPanel methodPanel = new JPanel(new BorderLayout());
    final JPanel typePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
    final JPanel namePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 2, true, false));

    final DocumentListener documentListener = new DocumentAdapter() {
      public void documentChanged(DocumentEvent event) {
        updateSignature();
      }
    };

    final JLabel nameLabel = new JLabel(RefactoringBundle.message("name.prompt"));
    myNameField = new EditorTextField(myMethod.getName());
    nameLabel.setLabelFor(myNameField);
    namePanel.add(nameLabel);
    namePanel.add(myNameField);
    myNameField.setEnabled(myMethod.canChangeName());
    if (myMethod.canChangeName()) {
      myNameField.addDocumentListener(documentListener);
    }

    createVisibilityPanel();

    if (myMethod.canChangeReturnType() != MethodDescriptor.ReadWriteOption.None) {
      final JLabel typeLabel = new JLabel(RefactoringBundle.message("changeSignature.return.type.prompt"));
      typePanel.add(typeLabel);
      myReturnTypeCodeFragment = createReturnTypeCodeFragment();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
      myReturnTypeField = createReturnTypeTextField(document);
      typeLabel.setLabelFor(myReturnTypeField);
      typePanel.add(myReturnTypeField);

      if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
        typePanel.setPreferredSize(new Dimension(200, -1));
        myReturnTypeField.addDocumentListener(documentListener);
      }
      else {
        myReturnTypeField.setEnabled(false);
      }
    }

    final JPanel p = new JPanel(new BorderLayout());
    if (myMethod.canChangeVisibility() && myVisibilityPanel instanceof ComboBoxVisibilityPanel) {
      p.add(myVisibilityPanel, BorderLayout.WEST);
    }
    p.add(typePanel, BorderLayout.EAST);
    methodPanel.add(p, BorderLayout.WEST);
    methodPanel.add(namePanel, BorderLayout.CENTER);
    panel.add(methodPanel);

    return panel;
  }

  protected EditorTextField createReturnTypeTextField(Document document) {
    return new EditorTextField(document, myProject, getFileType());
  }

  private DelegationPanel createDelegationPanel() {
    return new DelegationPanel() {
      protected void stateModified() {
        myParametersTableModel.fireTableDataChanged();
        myParametersTable.repaint();
      }
    };
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    //Should be called from here to initialize fields !!!
    final JComponent optionsPanel = createOptionsPanel();

    final JPanel subPanel = new JPanel(new BorderLayout());
    final List<Pair<String, JPanel>> panels = createAdditionalPanels();
    if (myMethod.canChangeParameters()) {
      final JPanel parametersPanel = createParametersPanel();
      if (!panels.isEmpty()) {
        parametersPanel.setBorder(IdeBorderFactory.createEmptyBorder(0));
      }
      subPanel.add(parametersPanel, BorderLayout.CENTER);
    }

    if (myMethod.canChangeVisibility() && !(myVisibilityPanel instanceof ComboBoxVisibilityPanel)) {
      subPanel.add(myVisibilityPanel, myMethod.canChangeParameters() ? BorderLayout.EAST : BorderLayout.CENTER);
    }

    panel.add(subPanel, BorderLayout.CENTER);
    final JPanel main;
    if (panels.isEmpty()) {
      main = panel;
    } else {
      final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(getDisposable());
      tabbedPane.addTab(RefactoringBundle.message("parameters.border.title"), panel);
      for (Pair<String, JPanel> extraPanel : panels) {
        tabbedPane.addTab(extraPanel.first, extraPanel.second);
      }
      main = new JPanel(new BorderLayout());
      main.add(tabbedPane.getComponent(), BorderLayout.CENTER);
    }
    final JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(optionsPanel, BorderLayout.NORTH);
    bottom.add(createSignaturePanel(), BorderLayout.SOUTH);
    main.add(bottom, BorderLayout.SOUTH);
    main.setBorder(IdeBorderFactory.createEmptyBorder(5,0,0,0));
    return main;
  }

  protected JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    if (myAllowDelegation) {
      myDelegationPanel = createDelegationPanel();
      panel.add(myDelegationPanel, BorderLayout.WEST);
    }

    myPropagateParamChangesButton = new AnActionButton(RefactoringBundle.message("changeSignature.propagate.parameters.title"), null, PlatformIcons.NEW_PARAMETER) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final Ref<CallerChooserBase<M>> chooser = new Ref<CallerChooserBase<M>>();
        Consumer<Set<M>> callback = new Consumer<Set<M>>() {
          @Override
          public void consume(Set<M> callers) {
            myMethodsToPropagateParameters = callers;
            myParameterPropagationTreeToReuse = chooser.get().getTree();
          }
        };
        try {
          chooser.set(
            createCallerChooser(RefactoringBundle.message("changeSignature.parameter.caller.chooser"), myParameterPropagationTreeToReuse,
                                callback));
        }
        catch (ProcessCanceledException ex) {
          // user cancelled initial callers search, don't show dialog
          return;
        }
        chooser.get().show();
      }
    };

    final JPanel result = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    result.add(panel);
    return result;
  }

  protected JPanel createVisibilityPanel() {
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


  @NotNull
  protected List<Pair<String, JPanel>> createAdditionalPanels() {
    return Collections.emptyList();
  }

  protected String getDimensionServiceKey() {
    return "refactoring.ChangeSignatureDialog";
  }

  protected JPanel createParametersPanel() {
    myParametersTable = new TableView<ParameterTableModelItemBase<P>>(myParametersTableModel) {
      @Override
      public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);
        repaint(); // to update disabled cells background
      }

      @Nullable
      @Override
      public TableCellEditor getCellEditor(int row, int column) {
        final TableCellEditor editor = super.getCellEditor(row, column);
        final DocumentAdapter listener = new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            final TableCellEditor ed = myParametersTable.getCellEditor();
            if (ed != null) {
              Object editorValue = ed.getCellEditorValue();
              myParametersTableModel.setValueAtWithoutUpdate(editorValue, myParametersTable.getSelectedRow(), myParametersTable.getSelectedColumn());
              updateSignature();
            }
          }
        };

        if (editor instanceof StringTableCellEditor) {
          final StringTableCellEditor ed = (StringTableCellEditor)editor;
          ed.addDocumentListener(listener);
        } else if (editor instanceof CodeFragmentTableCellEditorBase) {
          ((CodeFragmentTableCellEditorBase)editor).addDocumentListener(listener);
        }
        return editor;
      }
    };
    UIUtil.setTableDecorationEnabled(myParametersTable);
    myParametersTable.setCellSelectionEnabled(true);
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("parameters.border.title")));

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myParametersTable);

    JPanel tablePanel = new JPanel(new BorderLayout());
    tablePanel.add(scrollPane, BorderLayout.CENTER);

    tablePanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    panel.add(tablePanel, BorderLayout.CENTER);

    myParametersTable.setPreferredScrollableViewportSize(new Dimension(450, myParametersTable.getRowHeight() * 8));
    myParametersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myParametersTable.getSelectionModel().setSelectionInterval(0, 0);
    myParametersTable.setSurrendersFocusOnKeystroke(true);
    myPropagateParamChangesButton.setShortcut(KeyboardShortcut.fromString("alt G"));
    final JPanel buttonsPanel = EditableRowTable.createButtonsTable(myParametersTable, myParametersTableModel,
                                                              false, true, false, myPropagateParamChangesButton);
    myPropagateParamChangesButton.setEnabled(false);
    myPropagateParamChangesButton.setVisible(false);
    panel.add(buttonsPanel, BorderLayout.EAST);

    myParametersTableModel.addTableModelListener(
      new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          updateSignature();
        }
      }
    );

    customizeParametersTable(myParametersTable);

    return panel;
  }

  protected void customizeParametersTable(TableView<ParameterTableModelItemBase<P>> table) {
  }

  private JComponent createSignaturePanel() {
    mySignatureArea = createSignaturePreviewComponent();
    JPanel panel = new JPanel(new BorderLayout());
    //panel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringBundle.message("signature.preview.border.title")));
    panel.add(SeparatorFactory.createSeparator(RefactoringBundle.message("signature.preview.border.title"), null), BorderLayout.NORTH);
    panel.add(mySignatureArea, BorderLayout.CENTER);
    mySignatureArea.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
    mySignatureArea.setPreferredSize(new Dimension(-1, 130));
    mySignatureArea.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.CARET_ROW_COLOR));
    mySignatureArea.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        IdeFocusManager.findInstance().requestFocus(getContentPane().getFocusCycleRootAncestor(), true);
      }
    });
    updateSignature();
    return panel;
  }

  protected EditorTextField createSignaturePreviewComponent() {
    return new EditorTextField(EditorFactory.getInstance().createDocument(calculateSignature()),
                               getProject(),
                               getFileType(),
                               true,
                               false);
  }

  protected void updateSignature() {
    if (mySignatureArea == null || myPropagateParamChangesButton == null) return;

    final Runnable updateRunnable = new Runnable() {
      public void run() {
        myUpdateSignatureAlarm.cancelAllRequests();
        myUpdateSignatureAlarm.addRequest(new Runnable() {
          public void run() {
            doUpdateSignature();
            updatePropagateButtons();
          }
        }, 100);
      }
    };
    SwingUtilities.invokeLater(updateRunnable);
  }

  private void doUpdateSignature() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    String signature = calculateSignature();
    mySignatureArea.setText(signature);
    final Editor editor = mySignatureArea.getEditor();
    if (editor != null) {
      ((EditorEx)editor).setHorizontalScrollbarVisible(true);
      ((EditorEx)editor).setVerticalScrollbarVisible(true);
      editor.getScrollingModel().scrollVertically(0);
      editor.getScrollingModel().scrollHorizontally(0);
    }
  }

  protected void updatePropagateButtons() {
    if (myPropagateParamChangesButton != null) {
      myPropagateParamChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateParameters());
    }
  }

  private boolean mayPropagateParameters() {
    final List<P> infos = getParameters();
    if (infos.size() <= myMethod.getParametersCount()) return false;
    for (int i = 0; i < myMethod.getParametersCount(); i++) {
      if (infos.get(i).getOldIndex() != i) return false;
    }
    return true;
  }

  protected void doAction() {
    if (myParametersTable != null) {
      TableUtil.stopEditing(myParametersTable);
    }
    String message = validateAndCommitData();
    if (message != null) {
      if (message != EXIT_SILENTLY) {
        CommonRefactoringUtil.showErrorMessage(getTitle(), message, getHelpId(), myProject);
      }
      return;
    }
    if (myMethodsToPropagateParameters != null && !mayPropagateParameters()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.parameters.wont.propagate"),
                                 ChangeSignatureHandler.REFACTORING_NAME);
      myMethodsToPropagateParameters = null;
    }

    invokeRefactoring(createRefactoringProcessor());
  }

  @Override
  protected String getHelpId() {
    return "refactoring.changeSignature";
  }
}

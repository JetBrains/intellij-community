// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.JBListTable;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
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
public abstract class ChangeSignatureDialogBase<ParamInfo extends ParameterInfo,
                                                Method extends PsiElement,
                                                Visibility,
                                                Descriptor extends MethodDescriptor<ParamInfo, Visibility>,
                                                ParameterTableModelItem extends ParameterTableModelItemBase<ParamInfo>,
                                                ParameterTableModel extends ParameterTableModelBase<ParamInfo, ParameterTableModelItem>>
  extends RefactoringDialog {

  private static final Logger LOG = Logger.getInstance(ChangeSignatureDialogBase.class);

  protected static final String EXIT_SILENTLY = "";

  protected final Descriptor myMethod;
  private final boolean myAllowDelegation;
  protected JPanel myNamePanel;
  protected EditorTextField myNameField;
  protected EditorTextField myReturnTypeField;
  protected JBListTable myParametersList;
  protected TableView<ParameterTableModelItem> myParametersTable;
  protected final ParameterTableModel myParametersTableModel;
  protected final UpdateSignatureListener mySignatureUpdater = new UpdateSignatureListener();
  private MethodSignatureComponent mySignatureArea;
  private final Alarm myUpdateSignatureAlarm;

  protected VisibilityPanelBase<Visibility> myVisibilityPanel;

  protected @Nullable PsiCodeFragment myReturnTypeCodeFragment;
  private DelegationPanel myDelegationPanel;
  protected AnActionButton myPropagateParamChangesButton;
  protected Set<Method> myMethodsToPropagateParameters = null;

  private Tree myParameterPropagationTreeToReuse;

  protected final PsiElement myDefaultValueContext;

  protected abstract LanguageFileType getFileType();

  protected abstract @NotNull ParameterTableModel createParametersInfoModel(@NotNull Descriptor method);

  protected abstract BaseRefactoringProcessor createRefactoringProcessor();

  protected abstract PsiCodeFragment createReturnTypeCodeFragment();

  protected abstract @Nullable CallerChooserBase<Method> createCallerChooser(@Nls String title, Tree treeToReuse, Consumer<? super Set<Method>> callback);

  protected abstract @Nullable @NlsContexts.DialogMessage String validateAndCommitData();

  protected abstract String calculateSignature();

  protected abstract VisibilityPanelBase<Visibility> createVisibilityControl();

  public ChangeSignatureDialogBase(@NotNull Project project, @NotNull Descriptor method, boolean allowDelegation, PsiElement defaultValueContext) {
    super(project, true);
    myMethod = method;
    myDefaultValueContext = defaultValueContext;
    myParametersTableModel = createParametersInfoModel(method);
    myAllowDelegation = allowDelegation;

    setParameterInfos(method.getParameters());

    setTitle(RefactoringBundle.message("changeSignature.refactoring.name"));
    init();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    myUpdateSignatureAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myDisposable);
  }

  public void setParameterInfos(@NotNull List<? extends ParamInfo> parameterInfos) {
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

  protected @Nullable Visibility getVisibility() {
    if (myVisibilityPanel != null) {
      return myVisibilityPanel.getVisibility();
    }
    else {
      return myMethod.getVisibility();
    }
  }

  public List<ParamInfo> getParameters() {
    List<ParamInfo> result = new ArrayList<>(myParametersTableModel.getRowCount());
    for (ParameterTableModelItemBase<ParamInfo> item : myParametersTableModel.getItems()) {
      result.add(item.parameter);
    }
    return result;
  }

  public boolean isGenerateDelegate() {
    return myAllowDelegation && myDelegationPanel.isGenerateDelegate();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final JTable table = getTableComponent();

    if (table != null && table.getRowCount() > 0) {
      if (table.getColumnModel().getSelectedColumnCount() == 0) {
        final int selectedIdx = getSelectedIdx();
        table.getSelectionModel().setSelectionInterval(selectedIdx, selectedIdx);
        table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);
      }
      return table;
    }
    if (UIUtil.isFocusable(myNameField)) {
      return myNameField;
    }
    if (UIUtil.isFocusable(myReturnTypeField)) {
      return myReturnTypeField;
    }
    return super.getPreferredFocusedComponent();
  }

  protected int getSelectedIdx() {
    return 0;
  }

  protected JBTable getTableComponent() {
    return myParametersList == null ? myParametersTable : myParametersList.getTable();
  }

  public boolean placeReturnTypeBeforeName() {
    return true;
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 0, 1,
                                                    GridBagConstraints.WEST,
                                                    GridBagConstraints.HORIZONTAL,
                                                    JBInsets.emptyInsets(),
                                                    0, 0);

    myNamePanel = new JPanel(new BorderLayout(0, 2));
    myNameField = new EditorTextField(myMethod.getName());
    final JLabel nameLabel = new JLabel(RefactoringBundle.message("changeSignature.name.prompt"));
    nameLabel.setLabelFor(myNameField);
    myNameField.setEnabled(myMethod.canChangeName());
    if (myMethod.canChangeName()) {
      myNameField.addDocumentListener(mySignatureUpdater);
      Disposer.register(myDisposable, () -> myNameField.removeDocumentListener(mySignatureUpdater));
      myNameField.setPreferredWidth(200);
    }
    myNamePanel.add(nameLabel, BorderLayout.NORTH);
    myNamePanel.add(myNameField, BorderLayout.SOUTH);

    JPanel visibilityPanel = createVisibilityPanel();

    if (myMethod.canChangeVisibility() && myVisibilityPanel instanceof ComboBoxVisibilityPanel) {
      ((ComboBoxVisibilityPanel<?>)myVisibilityPanel).registerUpDownActionsFor(myNameField);
      visibilityPanel.setBorder(JBUI.Borders.emptyRight(8));
      panel.add(visibilityPanel, gbc);
      gbc.gridx++;
    }

    gbc.weightx = 1;

    if (myMethod.canChangeReturnType() != MethodDescriptor.ReadWriteOption.None) {
      JPanel typePanel = new JPanel(new BorderLayout(0, 2));
      typePanel.setBorder(JBUI.Borders.emptyRight(8));
      final JLabel typeLabel = new JLabel(RefactoringBundle.message("changeSignature.return.type.prompt"));
      myReturnTypeCodeFragment = createReturnTypeCodeFragment();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment);
      myReturnTypeField = createReturnTypeTextField(document);
      ((ComboBoxVisibilityPanel<?>)myVisibilityPanel).registerUpDownActionsFor(myReturnTypeField);
      typeLabel.setLabelFor(myReturnTypeField);

      if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
        myReturnTypeField.setPreferredWidth(200);
        myReturnTypeField.addDocumentListener(mySignatureUpdater);
        Disposer.register(myDisposable, () -> myReturnTypeField.removeDocumentListener(mySignatureUpdater));
      }
      else {
        myReturnTypeField.setEnabled(false);
      }

      typePanel.add(typeLabel, BorderLayout.NORTH);
      typePanel.add(myReturnTypeField, BorderLayout.SOUTH);
      if (placeReturnTypeBeforeName()) {
        panel.add(typePanel, gbc);
        gbc.gridx++;
        panel.add(myNamePanel, gbc);
      }
      else {
        panel.add(myNamePanel, gbc);
        gbc.gridx++;
        panel.add(typePanel, gbc);
      }
    }
    else {
      panel.add(myNamePanel, gbc);
    }

    return panel;
  }

  protected EditorTextField createReturnTypeTextField(Document document) {
    return new EditorTextField(document, myProject, getFileType());
  }

  private DelegationPanel createDelegationPanel() {
    return new DelegationPanel() {
      @Override
      protected void stateModified() {
        myParametersTableModel.fireTableDataChanged();
        myParametersTable.repaint();
      }
    };
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    //Should be called from here to initialize fields !!!
    final JComponent optionsPanel = createOptionsPanel();

    final JPanel subPanel = new JPanel(new BorderLayout());
    final List<Pair<@NlsContexts.TabTitle String, JPanel>> panels = createAdditionalPanels();
    if (myMethod.canChangeParameters()) {
      final JPanel parametersPanel = createParametersPanel(!panels.isEmpty());
      if (!panels.isEmpty()) {
        parametersPanel.setBorder(JBUI.Borders.empty());
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
    }
    else {
      final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(getDisposable());
      panel.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));
      tabbedPane.addTab(RefactoringBundle.message("parameters.border.title"), panel);
      for (Pair<@NlsContexts.TabTitle String, JPanel> extraPanel : panels) {
        extraPanel.second.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));
        tabbedPane.addTab(extraPanel.first, extraPanel.second);
      }
      main = new JPanel(new BorderLayout());
      final JComponent tabs = tabbedPane.getComponent();
      main.add(tabs, BorderLayout.CENTER);
      //remove traversal policies
      for (JComponent c : UIUtil.findComponentsOfType(tabs, JComponent.class)) {
        c.setFocusCycleRoot(false);
        c.setFocusTraversalPolicy(null);
      }
    }
    final JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(optionsPanel, BorderLayout.NORTH);
    bottom.add(createSignaturePanel(), BorderLayout.SOUTH);
    main.add(bottom, BorderLayout.SOUTH);
    main.setBorder(JBUI.Borders.emptyTop(5));
    return main;
  }

  protected JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    if (myAllowDelegation) {
      myDelegationPanel = createDelegationPanel();
      panel.add(myDelegationPanel, BorderLayout.WEST);
    }

    myPropagateParamChangesButton =
      new AnActionButton(RefactoringBundle.message("changeSignature.propagate.parameters.title"), null, AllIcons.Hierarchy.Supertypes) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          final Ref<CallerChooserBase<Method>> chooser = new Ref<>();
          Consumer<Set<Method>> callback = callers -> {
            myMethodsToPropagateParameters = callers;
            myParameterPropagationTreeToReuse = chooser.get().getTree();
          };
          try {
            String message = RefactoringBundle.message("changeSignature.parameter.caller.chooser");
            chooser.set(createCallerChooser(message, myParameterPropagationTreeToReuse, callback));
          }
          catch (ProcessCanceledException ex) {
            // user cancelled initial callers search, don't show dialog
            return;
          }
          chooser.get().show();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      };

    final JPanel result = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    result.add(panel);
    return result;
  }

  protected JPanel createVisibilityPanel() {
    myVisibilityPanel = createVisibilityControl();
    myVisibilityPanel.setVisibility(myMethod.getVisibility());
    myVisibilityPanel.addListener(mySignatureUpdater);
    return myVisibilityPanel;
  }


  protected @NotNull List<Pair<@NlsContexts.TabTitle String, JPanel>> createAdditionalPanels() {
    return Collections.emptyList();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "refactoring.ChangeSignatureDialog";
  }

  protected boolean isListTableViewSupported() {
    return false;
  }


  protected JPanel createParametersPanel(boolean hasTabsInDialog) {
    myParametersTable = new TableView<>(myParametersTableModel) {

      @Override
      public void removeEditor() {
        clearEditorListeners();
        super.removeEditor();
      }

      @Override
      public void editingStopped(ChangeEvent e) {
        super.editingStopped(e);
        repaint(); // to update disabled cells background
      }

      private void clearEditorListeners() {
        final TableCellEditor editor = getCellEditor();
        if (editor instanceof StringTableCellEditor ed) {
          ed.clearListeners();
        }
        else if (editor instanceof CodeFragmentTableCellEditorBase) {
          ((CodeFragmentTableCellEditorBase)editor).clearListeners();
        }
      }

      @Override
      public Component prepareEditor(final TableCellEditor editor, final int row, final int column) {
        final DocumentListener listener = new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            final TableCellEditor ed = myParametersTable.getCellEditor();
            if (ed != null) {
              Object editorValue = ed.getCellEditorValue();
              myParametersTableModel.setValueAtWithoutUpdate(editorValue, row, column);
              updateSignature();
            }
          }
        };

        if (editor instanceof StringTableCellEditor ed) {
          ed.addDocumentListener(listener);
        }
        else if (editor instanceof CodeFragmentTableCellEditorBase) {
          ((CodeFragmentTableCellEditorBase)editor).addDocumentListener(listener);
        }
        return super.prepareEditor(editor, row, column);
      }
    };

    myParametersTable.setShowGrid(false);
    myParametersTable.setCellSelectionEnabled(true);
    myParametersTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myParametersTable.getSelectionModel().setSelectionInterval(0, 0);
    myParametersTable.setSurrendersFocusOnKeystroke(true);
    myPropagateParamChangesButton.setShortcut(CustomShortcutSet.fromString("alt G"));

    if (isListTableViewSupported()) {
      myParametersList = createParametersListTable();
      final JPanel buttonsPanel = ToolbarDecorator.createDecorator(myParametersList.getTable())
        .addExtraAction(myPropagateParamChangesButton)
        .createPanel();
      TableModel tableModel = myParametersList.getTable().getModel();
      tableModel.addTableModelListener(mySignatureUpdater);
      Disposer.register(myDisposable, () -> tableModel.removeTableModelListener(mySignatureUpdater));
      return buttonsPanel;
    }
    else {
      final JPanel buttonsPanel =
        ToolbarDecorator.createDecorator(getTableComponent())
          .addExtraAction(myPropagateParamChangesButton)
          .createPanel();

      myPropagateParamChangesButton.setEnabled(false);
      myPropagateParamChangesButton.setVisible(false);

      myParametersTableModel.addTableModelListener(mySignatureUpdater);
      Disposer.register(myDisposable, () -> myParametersTableModel.removeTableModelListener(mySignatureUpdater));

      customizeParametersTable(myParametersTable);
      return buttonsPanel;
    }
  }

  protected ParametersListTable createParametersListTable() {
    return new ParametersListTable() {
      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return new JBTableRowRenderer() {
          @Override
          public JComponent getRowRendererComponent(JTable table, int row, boolean selected, boolean focused) {
            JComponent presentation = getRowPresentation(getRowItem(row), selected, focused);
            LOG.assertTrue(presentation != null);
            return presentation;
          }
        };
      }

      @Override
      protected @NotNull JBTableRowEditor getRowEditor(ParameterTableModelItemBase<ParamInfo> item) {
        JBTableRowEditor editor = ChangeSignatureDialogBase.this.getTableEditor(getTable(), item);
        LOG.assertTrue(editor != null);
        return editor;
      }

      @Override
      protected boolean isRowEmpty(int row) {
        return false;
      }
    };
  }

  /**
   * @deprecated override {@link #createParametersListTable} instead.
   */
  @Deprecated
  protected @Nullable JBTableRowEditor getTableEditor(JTable table, ParameterTableModelItemBase<ParamInfo> item) {
    return null;
  }

  /**
   * @deprecated override {@link #createParametersListTable} instead.
   */
  @Deprecated(forRemoval = true)
  protected @Nullable JComponent getRowPresentation(ParameterTableModelItemBase<ParamInfo> item, boolean selected, boolean focused) {
    return null;
  }

  protected void customizeParametersTable(TableView<ParameterTableModelItem> table) {
  }

  private JComponent createSignaturePanel() {
    mySignatureArea = createSignaturePreviewComponent();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(RefactoringBundle.message("signature.preview.border.title"), mySignatureArea), BorderLayout.NORTH);
    panel.add(mySignatureArea, BorderLayout.CENTER);
    mySignatureArea.setPreferredSize(new Dimension(-1, 130));
    mySignatureArea.setMinimumSize(new Dimension(-1, 130));
    mySignatureArea.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        Container root = findTraversalRoot(getContentPane());

        if (root != null) {
          Component c = root.getFocusTraversalPolicy().getComponentAfter(root, mySignatureArea);
          if (c != null) {
            IdeFocusManager.findInstance().requestFocus(c, true);
          }
        }
      }
    });
    updateSignature();
    return panel;
  }

  private static Container findTraversalRoot(Container container) {
    Container current = KeyboardFocusManager.getCurrentKeyboardFocusManager().getCurrentFocusCycleRoot();
    Container root;

    if (current == container) {
      root = container;
    }
    else {
      root = container.getFocusCycleRootAncestor();
      if (root == null) {
        root = container;
      }
    }

    if (root != current) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().setGlobalCurrentFocusCycleRoot(root);
    }
    return root;
  }

  protected MethodSignatureComponent createSignaturePreviewComponent() {
    return new MethodSignatureComponent(calculateSignature(), getProject(), getFileType());
  }

  protected void updateSignature() {
    if (mySignatureArea == null || myPropagateParamChangesButton == null) return;

    final Runnable updateRunnable = () -> {
      if (myUpdateSignatureAlarm.isDisposed()) return;
      myUpdateSignatureAlarm.cancelAllRequests();
      myUpdateSignatureAlarm.addRequest(() -> {
        if (myProject.isDisposed()) return;
        PsiDocumentManager.getInstance(myProject).performLaterWhenAllCommitted(() -> updateSignatureAlarmFired());
      }, 100, ModalityState.stateForComponent(mySignatureArea));
    };
    SwingUtilities.invokeLater(updateRunnable);
  }

  protected void updateSignatureAlarmFired() {
    doUpdateSignature();
    updatePropagateButtons();
  }

  private void doUpdateSignature() {
    LOG.assertTrue(!PsiDocumentManager.getInstance(myProject).hasUncommitedDocuments());
    ReadAction.nonBlocking(() -> calculateSignature())
      .finishOnUiThread(ModalityState.current(), signature -> mySignatureArea.setSignature(signature))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  protected void updatePropagateButtons() {
    if (myPropagateParamChangesButton != null) {
      myPropagateParamChangesButton.setEnabled(!isGenerateDelegate() && mayPropagateParameters());
    }
  }

  protected boolean mayPropagateParameters() {
    final List<ParamInfo> infos = getParameters();
    if (infos.size() <= myMethod.getParametersCount()) return false;
    for (int i = 0; i < myMethod.getParametersCount(); i++) {
      if (infos.get(i).getOldIndex() != i) return false;
    }
    return true;
  }

  @Override
  protected void doAction() {
    if (myParametersTable != null) {
      TableUtil.stopEditing(myParametersTable);
    }
    String message = validateAndCommitData();
    if (message != null) {
      if (!Strings.areSameInstance(message, EXIT_SILENTLY)) {
        CommonRefactoringUtil.showErrorMessage(getTitle(), message, getHelpId(), myProject);
      }
      return;
    }
    if (myMethodsToPropagateParameters != null && !mayPropagateParameters()) {
      Messages.showWarningDialog(myProject, RefactoringBundle.message("changeSignature.parameters.wont.propagate"),
                                 RefactoringBundle.message("changeSignature.refactoring.name"));
      myMethodsToPropagateParameters = null;
    }

    invokeRefactoring(createRefactoringProcessor());
  }

  @Override
  protected String getHelpId() {
    return "refactoring.changeSignature";
  }

  protected final class UpdateSignatureListener implements ChangeListener, DocumentListener, TableModelListener {
    private void update() {
      updateSignature();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      update();
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      update();
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      update();
    }
  }

  protected abstract class ParametersListTable extends JBListTable {
    public ParametersListTable() {
      super(myParametersTable, ChangeSignatureDialogBase.this.getDisposable());
    }

    @Override
    protected final JBTableRowEditor getRowEditor(final int row) {
      JBTableRowEditor editor = getRowEditor(getRowItem(row));
      editor.addDocumentListener(new JBTableRowEditor.RowDocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e, int column) {
          if (String.class.equals(myParametersTableModel.getColumnClass(column))) {
            myParametersTableModel.setValueAtWithoutUpdate(e.getDocument().getText(), row, column);
          }
          updateSignature();
        }
      });
      return editor;
    }

    protected abstract @NotNull JBTableRowEditor getRowEditor(ParameterTableModelItemBase<ParamInfo> item);

    @Override
    protected abstract boolean isRowEmpty(int row);

    protected ParameterTableModelItem getRowItem(int row) {
      return myParametersTable.getItems().get(row);
    }
  }
}

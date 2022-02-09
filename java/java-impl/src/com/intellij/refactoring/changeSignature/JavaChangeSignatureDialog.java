// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.inCallers.JavaCallerChooser;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.refactoring.ui.JavaCodeFragmentTableCellEditor;
import com.intellij.refactoring.ui.JavaComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.VisibilityPanelBase;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TableColumnAnimator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.EditorTextFieldJBTableRowRenderer;
import com.intellij.util.ui.table.JBTableRow;
import com.intellij.util.ui.table.JBTableRowEditor;
import com.intellij.util.ui.table.JBTableRowRenderer;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class JavaChangeSignatureDialog extends ChangeSignatureDialogBase<ParameterInfoImpl, PsiMethod, String, JavaMethodDescriptor, ParameterTableModelItemBase<ParameterInfoImpl>, JavaParameterTableModel> {
  private ExceptionsTableModel myExceptionsModel;
  protected Set<PsiMethod> myMethodsToPropagateExceptions;
  private AnActionButton myPropExceptionsButton;
  private Tree myExceptionPropagationTree;

  public JavaChangeSignatureDialog(@NotNull Project project, @NotNull PsiMethod method, boolean allowDelegation, PsiElement context) {
    this(project, new JavaMethodDescriptor(method), allowDelegation, context);
  }

  protected JavaChangeSignatureDialog(@NotNull Project project, @NotNull JavaMethodDescriptor descriptor, boolean allowDelegation, PsiElement context) {
    super(project, descriptor, allowDelegation, context);
  }

  @NotNull
  public static JavaChangeSignatureDialog createAndPreselectNew(@NotNull Project project,
                                                                @NotNull PsiMethod method,
                                                                @NotNull List<? extends ParameterInfoImpl> parameterInfos,
                                                                final boolean allowDelegation,
                                                                final PsiReferenceExpression refExpr) {
    return createAndPreselectNew(project, method, parameterInfos, allowDelegation, refExpr, null);
  }

  @NotNull
  public static JavaChangeSignatureDialog createAndPreselectNew(@NotNull Project project,
                                                                @NotNull PsiMethod method,
                                                                @NotNull List<? extends ParameterInfoImpl> parameterInfos,
                                                                final boolean allowDelegation,
                                                                final PsiReferenceExpression refExpr,
                                                                @Nullable Consumer<? super List<ParameterInfoImpl>> callback) {
    return new JavaChangeSignatureDialog(project, method, allowDelegation, refExpr) {
      @Override
      protected int getSelectedIdx() {
        for (int i = 0; i < parameterInfos.size(); i++) {
          ParameterInfoImpl info = parameterInfos.get(i);
          if (info.oldParameterIndex < 0) {
            return i;
          }
        }
        return super.getSelectedIdx();
      }

      @Override
      protected BaseRefactoringProcessor createRefactoringProcessor() {
        final List<ParameterInfoImpl> parameters = getParameters();
        var provider = JavaSpecialRefactoringProvider.getInstance();
        return provider.getChangeSignatureProcessorWithCallback(
          myProject,
          myMethod.getMethod(),
          isGenerateDelegate(),
          getVisibility(),
          getMethodName(),
          getReturnType(),
          parameters.toArray(new ParameterInfoImpl[0]),
          getExceptions(),
          myMethodsToPropagateParameters,
          myMethodsToPropagateExceptions, () -> {
            if (callback != null) {
              callback.consume(getParameters());
            }
          });
      }
    };
  }

  @Nullable
  @Override
  @PsiModifier.ModifierConstant
  protected String getVisibility() {
    //noinspection MagicConstant
    return super.getVisibility();
  }

  @Override
  protected VisibilityPanelBase<String> createVisibilityControl() {
    return new JavaComboBoxVisibilityPanel(myMethod.getAllowedModifiers());
  }

  @Override
  protected JComponent createCenterPanel() {
    final JComponent centerPanel = super.createCenterPanel();
    myPropagateParamChangesButton.setVisible(true);
    return centerPanel;
  }

  @Override
  protected void updatePropagateButtons() {
    super.updatePropagateButtons();
    myPropExceptionsButton.setEnabled(!isGenerateDelegate() && mayPropagateExceptions());
  }

  protected boolean mayPropagateExceptions() {
    final ThrownExceptionInfo[] exceptions = myExceptionsModel.getThrownExceptions();
    final PsiClassType[] types = getMethod().getThrowsList().getReferencedTypes();

    if (exceptions.length <= types.length) {
      return false;
    }

    for (int i = 0; i < types.length; i++) {
      if (exceptions[i].getOldIndex() != i) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected @NotNull List<Pair<@NlsContexts.TabTitle String, JPanel>> createAdditionalPanels() {
    // this method is invoked before constructor body
    myExceptionsModel = new ExceptionsTableModel(getMethod().getThrowsList());
    myExceptionsModel.setTypeInfos(getMethod());

    final JBTable table = new JBTable(myExceptionsModel);
    table.setShowGrid(false);
    table.setRowHeight(20);
    table.getColumnModel().getColumn(0).setCellRenderer(new CodeFragmentTableCellRenderer(myProject));
    final JavaCodeFragmentTableCellEditor cellEditor = new JavaCodeFragmentTableCellEditor(myProject);
    cellEditor.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        final int row = table.getSelectedRow();
        final int col = table.getSelectedColumn();
        myExceptionsModel.setValueAt(cellEditor.getCellEditorValue(), row, col);
        updateMethodSignature();
      }
    });
    table.getColumnModel().getColumn(0).setCellEditor(cellEditor);
    table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().setSelectionInterval(0, 0);
    table.setSurrendersFocusOnKeystroke(true);

    myPropExceptionsButton = new AnActionButton(JavaRefactoringBundle.message("changeSignature.propagate.exceptions.title"), null, AllIcons.Hierarchy.Supertypes) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final Ref<JavaCallerChooser> chooser = new Ref<>();
        Consumer<Set<PsiMethod>> callback = psiMethods -> {
          myMethodsToPropagateExceptions = psiMethods;
          myExceptionPropagationTree = chooser.get().getTree();
        };
        chooser.set(new JavaCallerChooser(getMethod(),
                                          myProject,
                                          JavaRefactoringBundle.message("changeSignature.exception.caller.chooser"),
                                          myExceptionPropagationTree,
                                          callback));
        chooser.get().show();
      }
    };
    myPropExceptionsButton.setShortcut(CustomShortcutSet.fromString("alt X"));

    final JPanel panel = ToolbarDecorator.createDecorator(table).addExtraAction(myPropExceptionsButton).createPanel();
    panel.setBorder(JBUI.Borders.empty());

    myExceptionsModel.addTableModelListener(getSignatureUpdater());

    final ArrayList<Pair<String, JPanel>> result = new ArrayList<>();
    final String message = JavaRefactoringBundle.message("changeSignature.exceptions.panel.border.title");
    result.add(Pair.create(message, panel));
    return result;
  }

  private void updateMethodSignature() {
    updateSignature();
  }

  @Override
  protected LanguageFileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @NotNull
  @Override
  protected JavaParameterTableModel createParametersInfoModel(@NotNull JavaMethodDescriptor descriptor) {
    final PsiParameterList parameterList = descriptor.getMethod().getParameterList();
    return new JavaParameterTableModel(parameterList, myDefaultValueContext, this);
  }

  @Override
  protected boolean isListTableViewSupported() {
    return true;
  }

  @Override
  protected ParametersListTable createParametersListTable() {
    return new ParametersListTable() {
      @Override
      public ParameterTableModelItemBase<ParameterInfoImpl> getRowItem(int row) {
        return super.getRowItem(row);
      }

      private final EditorTextFieldJBTableRowRenderer myRowRenderer =
        new EditorTextFieldJBTableRowRenderer(getProject(), JavaChangeSignatureDialog.this.getFileType(), myDisposable) {
        @Override
        protected String getText(JTable table, int row) {
          ParameterTableModelItemBase<ParameterInfoImpl> item = getRowItem(row);
          final String typeText = item.typeCodeFragment.getText();
          final String separator = StringUtil.repeatSymbol(' ', getTypesMaxLength() - typeText.length() + 1);
          String text = typeText + separator + item.parameter.getName();
          final String defaultValue = item.defaultValueCodeFragment.getText();
          String tail = "";
          if (StringUtil.isNotEmpty(defaultValue)) {
            tail += " " + RefactoringBundle.message("changeSignature.default.value.label") + " " + defaultValue;
          }
          if (item.parameter.isUseAnySingleVariable()) {
            if (StringUtil.isNotEmpty(defaultValue)) {
              tail += ";";
            }
            tail += " " + JavaRefactoringBundle.message("changeSignature.use.any.var");
          }
          if (!StringUtil.isEmpty(tail)) {
            text += " //" + tail;
          }
          return " " + text;
        }
      };

      @Override
      protected JBTableRowRenderer getRowRenderer(int row) {
        return myRowRenderer;
      }

      @NotNull
      @Override
      protected JBTableRowEditor getRowEditor(final ParameterTableModelItemBase<ParameterInfoImpl> item) {
        return new JBTableRowEditor() {
          private EditorTextField myTypeEditor;
          private EditorTextField myNameEditor;
          private EditorTextField myDefaultValueEditor;
          private JCheckBox myAnyVar;

          @Override
          public void prepareEditor(JTable table, int row) {
            setLayout(new BorderLayout());
            final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(item.typeCodeFragment);
            myTypeEditor = new EditorTextField(document, getProject(), getFileType());
            myTypeEditor.addDocumentListener(getSignatureUpdater());
            myTypeEditor.setPreferredWidth(getTable().getWidth() / 2);
            myTypeEditor.addDocumentListener(new RowEditorChangeListener(0));
            add(createLabeledPanel(RefactoringBundle.message("column.name.type"), myTypeEditor), BorderLayout.WEST);

            myNameEditor = new EditorTextField(item.parameter.getName(), getProject(), getFileType());
            myNameEditor.addDocumentListener(getSignatureUpdater());
            myNameEditor.addDocumentListener(new RowEditorChangeListener(1));
            add(createLabeledPanel(JavaBundle.message("dialog.create.field.from.parameter.field.name.label"), myNameEditor), BorderLayout.CENTER);
            new TextFieldCompletionProvider() {

              @Override
              protected void addCompletionVariants(@NotNull String text,
                                                   int offset,
                                                   @NotNull String prefix,
                                                   @NotNull CompletionResultSet result) {
                final PsiCodeFragment fragment = item.typeCodeFragment;
                if (fragment instanceof PsiTypeCodeFragment) {
                  final PsiType type;
                  try {
                    type = ((PsiTypeCodeFragment)fragment).getType();
                  }
                  catch (Exception e) {
                    return;
                  }
                  final SuggestedNameInfo info = JavaCodeStyleManager.getInstance(myProject)
                    .suggestVariableName(VariableKind.PARAMETER, null, null, type);

                  for (String completionVariant : info.names) {
                    final LookupElementBuilder element = LookupElementBuilder.create(completionVariant);
                    result.addElement(element.withLookupString(StringUtil.toLowerCase(completionVariant)));
                  }
                }
              }
            }.apply(myNameEditor, item.parameter.getName());

            if (!item.isEllipsisType() && item.parameter.isNew()) {
              final JPanel additionalPanel = new JPanel(new BorderLayout());
              final Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(item.defaultValueCodeFragment);
              myDefaultValueEditor = new EditorTextField(doc, getProject(), getFileType());
              ((PsiExpressionCodeFragment)item.defaultValueCodeFragment).setExpectedType(getRowType(item));
              myDefaultValueEditor.setPreferredWidth(getTable().getWidth() / 2);
              myDefaultValueEditor.addDocumentListener(new RowEditorChangeListener(2));
              String message = RefactoringBundle.message("changeSignature.default.value.label");
              additionalPanel.add(createLabeledPanel(message, myDefaultValueEditor), BorderLayout.WEST);

              if (!isGenerateDelegate()) {
                myAnyVar = new JCheckBox(JavaRefactoringBundle.message("change.signature.use.any.checkbox"),
                                         item.parameter.isUseAnySingleVariable());
                UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myAnyVar);
                DialogUtil.registerMnemonic(myAnyVar, '&');
                myAnyVar.addActionListener(new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                    item.parameter.setUseAnySingleVariable(myAnyVar.isSelected());
                  }
                });
                additionalPanel.add(createLabeledPanel(" "/* for correct vertical alignment */, myAnyVar), BorderLayout.CENTER);
              }
              add(additionalPanel, BorderLayout.SOUTH);
            }
          }

          @Override
          public JBTableRow getValue() {
            return new JBTableRow() {
              @Override
              public Object getValueAt(int column) {
                switch (column) {
                  case 0: return item.typeCodeFragment;
                  case 1: return myNameEditor.getText().trim();
                  case 2: return item.defaultValueCodeFragment;
                  case 3: return myAnyVar != null && myAnyVar.isSelected();
                }
                return null;
              }
            };
          }

          @Override
          public JComponent getPreferredFocusedComponent() {
            final MouseEvent me = getMouseEvent();
            if (me == null) {
              return myTypeEditor.getFocusTarget();
            }
            final double x = me.getPoint().getX();
            return x <= getTypesColumnWidth()
                   ? myTypeEditor.getFocusTarget()
                   : myDefaultValueEditor == null || x <= getNamesColumnWidth()
                     ? myNameEditor.getFocusTarget()
                     : myDefaultValueEditor.getFocusTarget();
          }

          @Override
          public JComponent[] getFocusableComponents() {
            final List<JComponent> focusable = new ArrayList<>();
            focusable.add(myTypeEditor.getFocusTarget());
            focusable.add(myNameEditor.getFocusTarget());
            if (myDefaultValueEditor != null) {
              focusable.add(myDefaultValueEditor.getFocusTarget());
            }
            if (myAnyVar != null) {
              focusable.add(myAnyVar);
            }
            return focusable.toArray(new JComponent[0]);
          }
        };
      }

      @Override
      protected boolean isRowEmpty(int row) {
        ParameterInfoImpl parameter = getRowItem(row).parameter;
        return StringUtil.isEmpty(parameter.getName()) && StringUtil.isEmpty(parameter.getTypeText());
      }
    };
  }

  private UpdateSignatureListener getSignatureUpdater() {
    return mySignatureUpdater;
  }

  private int getTypesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      final String text = item.typeCodeFragment == null ? null : item.typeCodeFragment.getText();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }

  private int getNamesMaxLength() {
    int len = 0;
    for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
      final String text = item.parameter.getName();
      len = Math.max(len, text == null ? 0 : text.length());
    }
    return len;
  }

  private int getColumnWidth(int index) {
    int letters = getTypesMaxLength() + (index == 0 ? 1 : getNamesMaxLength() + 2);
    Font font = EditorFontType.getGlobalPlainFont();
    font = new Font(font.getFontName(), font.getStyle(), 12);
    return  letters * Toolkit.getDefaultToolkit().getFontMetrics(font).stringWidth("W");
  }

  private int getTypesColumnWidth() {
    return  getColumnWidth(0);
  }

  private int getNamesColumnWidth() {
    return getColumnWidth(1);
  }

  @Nullable
  private static PsiType getRowType(ParameterTableModelItemBase<ParameterInfoImpl> item) {
    try {
      return ((PsiTypeCodeFragment)item.typeCodeFragment).getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e) {
      return null;
    }
  }

  @Override
  protected void customizeParametersTable(TableView<ParameterTableModelItemBase<ParameterInfoImpl>> table) {
    final JTable t = table.getComponent();
    final TableColumn defaultValue = t.getColumnModel().getColumn(2);
    final TableColumn varArg = t.getColumnModel().getColumn(3);
    t.removeColumn(defaultValue);
    t.removeColumn(varArg);
    t.getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.INSERT) {
          t.getModel().removeTableModelListener(this);
          final TableColumnAnimator animator = new TableColumnAnimator(t);
          animator.setStep(48);
          animator.addColumn(defaultValue, (t.getWidth() - 48) / 3);
          animator.addColumn(varArg, 48);
          animator.startAndDoWhenDone(() -> t.editCellAt(t.getRowCount() - 1, 0));
          animator.start();
        }
      }
    });
  }

  @Override
  protected void invokeRefactoring(final BaseRefactoringProcessor processor) {
    if (myMethodsToPropagateExceptions != null && !mayPropagateExceptions()) {
      Messages.showWarningDialog(myProject, JavaRefactoringBundle.message("changeSignature.exceptions.wont.propagate"), 
                                 RefactoringBundle.message("changeSignature.refactoring.name"));
      myMethodsToPropagateExceptions = null;
    }
    super.invokeRefactoring(processor);
  }

  @Override
  protected BaseRefactoringProcessor createRefactoringProcessor() {
    final List<ParameterInfoImpl> parameters = getParameters();

    return ActionUtil.underModalProgress(
      myProject,
      JavaRefactoringBundle.message("changeSignature.processing.changes.title"),
      () -> {
        var provider = JavaSpecialRefactoringProvider.getInstance();
        return provider.getChangeSignatureProcessorWithCallback(
          myProject, getMethod(), isGenerateDelegate(), getVisibility(), getMethodName(), getReturnType(),
          parameters.toArray(new ParameterInfoImpl[0]), getExceptions(), myMethodsToPropagateParameters, myMethodsToPropagateExceptions,
          null);
      }
    );
  }

  private PsiMethod getMethod() {
    return myMethod.getMethod();
  }

  @Nullable
  protected CanonicalTypes.Type getReturnType() {
    if (myReturnTypeField != null) {
      try {
        final PsiType type = ((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType();
        return CanonicalTypes.createTypeWrapper(type);
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e) {
        return null;
      }
    }

    return null;
  }

  protected ThrownExceptionInfo[] getExceptions() {
    return myExceptionsModel.getThrownExceptions();
  }

  @Override
  protected PsiCodeFragment createReturnTypeCodeFragment() {
    final String returnTypeText = StringUtil.notNullize(myMethod.getReturnTypeText());
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    return factory.createTypeCodeFragment(returnTypeText, getMethod(), true, JavaCodeFragmentFactory.ALLOW_VOID);
  }

  @Override
  protected CallerChooserBase<PsiMethod> createCallerChooser(@Nls String title, Tree treeToReuse, Consumer<Set<PsiMethod>> callback) {
    return new JavaCallerChooser(getMethod(), myProject, title, treeToReuse, callback);
  }

  @Override
  protected String validateAndCommitData() {
    Ref<JComponent> componentWithFocus = Ref.create();
    String message = ActionUtil.underModalProgress(myProject, JavaRefactoringBundle.message("changeSignature.validating.title"), () -> {
      PsiManager manager = PsiManager.getInstance(myProject);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

      String name = getMethodName();
      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(name)) {
        return RefactoringMessageUtil.getIncorrectIdentifierMessage(name);
      }

      if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
        try {
          ((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType();
        }
        catch (PsiTypeCodeFragment.TypeSyntaxException e) {
          componentWithFocus.set(myReturnTypeField);
          return JavaRefactoringBundle.message("changeSignature.wrong.return.type", myReturnTypeCodeFragment.getText());
        }
        catch (PsiTypeCodeFragment.NoTypeException e) {
          componentWithFocus.set(myReturnTypeField);
          return JavaRefactoringBundle.message("changeSignature.no.return.type");
        }
      }

      List<ParameterTableModelItemBase<ParameterInfoImpl>> parameterInfos = myParametersTableModel.getItems();
      final int newParametersNumber = parameterInfos.size();

      for (int i = 0; i < newParametersNumber; i++) {
        final ParameterTableModelItemBase<ParameterInfoImpl> item = parameterInfos.get(i);

        if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(item.parameter.getName())) {
          return RefactoringMessageUtil.getIncorrectIdentifierMessage(item.parameter.getName());
        }

        final PsiType type;
        try {
          type = ((PsiTypeCodeFragment)parameterInfos.get(i).typeCodeFragment).getType();
        }
        catch (PsiTypeCodeFragment.TypeSyntaxException e) {
          return JavaRefactoringBundle.message("changeSignature.wrong.type.for.parameter",
                                               item.typeCodeFragment.getText(),
                                               item.parameter.getName());
        }
        catch (PsiTypeCodeFragment.NoTypeException e) {
          return JavaRefactoringBundle.message("changeSignature.no.type.for.parameter", "return", item.parameter.getName());
        }

        item.parameter.setType(type);

        if (type instanceof PsiEllipsisType && i != newParametersNumber - 1) {
          return JavaRefactoringBundle.message("changeSignature.vararg.not.last");
        }

        if (item.parameter.oldParameterIndex < 0) {
          String def = JavaCodeStyleManager.getInstance(myProject)
            .qualifyClassReferences(item.defaultValueCodeFragment.copy()).getText().trim();
          item.parameter.defaultValue = def;
          if (!(type instanceof PsiEllipsisType)) {
            try {
              if (!StringUtil.isEmpty(def)) {
                factory.createExpressionFromText(def, null);
              }
            }
            catch (IncorrectOperationException e) {
              return e.getMessage();
            }
          }
        }
      }

      ThrownExceptionInfo[] exceptionInfos = myExceptionsModel.getThrownExceptions();
      PsiTypeCodeFragment[] typeCodeFragments = myExceptionsModel.getTypeCodeFragments();
      for (int i = 0; i < exceptionInfos.length; i++) {
        ThrownExceptionInfo exceptionInfo = exceptionInfos[i];
        PsiTypeCodeFragment typeCodeFragment = typeCodeFragments[i];
        try {
          PsiType type = typeCodeFragment.getType();
          if (!(type instanceof PsiClassType)) {
            return JavaRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
          }

          PsiClassType throwable = JavaPsiFacade.getElementFactory(myProject)
            .createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, type.getResolveScope());
          if (!throwable.isAssignableFrom(type)) {
            return JavaRefactoringBundle.message("changeSignature.not.throwable.type", typeCodeFragment.getText());
          }
          exceptionInfo.setType((PsiClassType)type);
        }
        catch (PsiTypeCodeFragment.TypeSyntaxException e) {
          return JavaRefactoringBundle.message("changeSignature.wrong.type.for.exception", typeCodeFragment.getText());
        }
        catch (PsiTypeCodeFragment.NoTypeException e) {
          return JavaRefactoringBundle.message("changeSignature.no.type.for.exception");
        }
      }
      return null;
    });
    if (!componentWithFocus.isNull()) {
      IdeFocusManager.getGlobalInstance()
        .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(componentWithFocus.get(), true));
    }
    else if (message == null) { // warnings
      try {
        if (myMethod.canChangeReturnType() == MethodDescriptor.ReadWriteOption.ReadWrite) {
          if (PsiTypesUtil.hasUnresolvedComponents(((PsiTypeCodeFragment)myReturnTypeCodeFragment).getType())) {
            if (Messages.showOkCancelDialog(myProject, JavaRefactoringBundle
                                              .message("changeSignature.cannot.resolve.return.type", myReturnTypeCodeFragment.getText()),
                                            RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) !=
                Messages.OK) {
              return EXIT_SILENTLY;
            }
          }
        }
        for (ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
          if (PsiTypesUtil.hasUnresolvedComponents(((PsiTypeCodeFragment)item.typeCodeFragment).getType())) {
            if (Messages.showOkCancelDialog(myProject, RefactoringBundle
                                              .message("changeSignature.cannot.resolve.parameter.type", item.typeCodeFragment.getText(), item.parameter.getName()),
                                            RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getWarningIcon()) !=
                Messages.OK) {
              return EXIT_SILENTLY;
            }
          }
        }
      }
      catch (PsiTypeCodeFragment.IncorrectTypeException ignored) {
      }
    }
    return message;
  }

  @Override
  protected ValidationInfo doValidate() {
    if (!getTableComponent().isEditing()) {
      for (final ParameterTableModelItemBase<ParameterInfoImpl> item : myParametersTableModel.getItems()) {
        if (item.parameter.oldParameterIndex < 0) {
          if (StringUtil.isEmpty(item.defaultValueCodeFragment.getText()))
            return new ValidationInfo(JavaRefactoringBundle.message("change.signature.default.value.missing.warning.message"));
        }
      }
    }
    return super.doValidate();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  protected String calculateSignature() {
    return doCalculateSignature(getMethod());
  }

  static String getModifiersText(PsiModifierList list, String newVisibility) {
    final String oldVisibility = VisibilityUtil.getVisibilityModifier(list);
    List<String> modifierKeywords = StreamEx.of(PsiTreeUtil.findChildrenOfType(list, PsiKeyword.class))
                                            .map(PsiElement::getText)
                                            .toList();
    if (!oldVisibility.equals(newVisibility)) {
      if (oldVisibility.equals(PsiModifier.PACKAGE_LOCAL)) {
        modifierKeywords.add(0, PsiModifier.PACKAGE_LOCAL);
      }
      if (newVisibility.equals(PsiModifier.PACKAGE_LOCAL)) {
        modifierKeywords.remove(oldVisibility);
      } else {
        modifierKeywords.replaceAll(m -> m.equals(oldVisibility) ? newVisibility : m);
      }
    }
    return String.join(" ", modifierKeywords);
  }

  private String getAnnotationText(PsiMethod method, PsiModifierList modifierList) {
    PsiAnnotation annotation = modifierList.findAnnotation(JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    if (annotation != null) {
      String[] oldNames = ContainerUtil.map2Array(method.getParameterList().getParameters(), String.class, PsiParameter::getName);
      JavaParameterInfo[] parameters =
        ContainerUtil.map2Array(myParametersTableModel.getItems(), JavaParameterInfo.class, item -> item.parameter);
      try {
        PsiAnnotation converted = ContractConverter.convertContract(method, oldNames, parameters);
        if (converted != null && converted != annotation) {
          String text = converted.getText();
          return text.replaceFirst("^@"+JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, "@Contract");
        }
      }
      catch (ContractConverter.ContractConversionException ignored) {
      }
      return annotation.getText();
    }
    return "";
  }

  protected String doCalculateSignature(PsiMethod method) {
    final StringBuilder buffer = new StringBuilder();
    final PsiModifierList modifierList = method.getModifierList();
    final String annotationText = getAnnotationText(method, modifierList);
    buffer.append(annotationText);
    if (!annotationText.isEmpty()) {
      buffer.append("\n");
    }
    final String modifiers = getModifiersText(modifierList, ObjectUtils.notNull(getVisibility(), PsiModifier.PACKAGE_LOCAL));
    buffer.append(modifiers);
    if (!modifiers.isEmpty()) {
      buffer.append(" ");
    }

    if (!method.isConstructor()) {
      final CanonicalTypes.Type type = getReturnType();
      if (type != null) {
        buffer.append(type.getTypeText());
      }
      buffer.append(" ");
    }
    buffer.append(getMethodName());
    buffer.append("(");

    final int lineBreakIdx = buffer.lastIndexOf("\n");
    String indent = StringUtil.repeatSymbol(' ', lineBreakIdx >= 0 ? buffer.length() - lineBreakIdx - 1 : buffer.length());
    List<ParameterTableModelItemBase<ParameterInfoImpl>> items = myParametersTableModel.getItems();
    int curIndent = indent.length();
    for (int i = 0; i < items.size(); i++) {
      final ParameterTableModelItemBase<ParameterInfoImpl> item = items.get(i);
      if (i > 0) {
        buffer.append(",");
        buffer.append("\n");
        buffer.append(indent);
      }
      final String text = item.typeCodeFragment.getText();
      buffer.append(text).append(" ");
      final String name = item.parameter.getName();
      buffer.append(name);
      curIndent = indent.length() + text.length() + 1 + name.length();
    }
    //if (!items.isEmpty()) {
    //  buffer.append("\n");
    //}
    buffer.append(")");
    PsiTypeCodeFragment[] thrownExceptionsFragments = myExceptionsModel.getTypeCodeFragments();
    if (thrownExceptionsFragments.length > 0) {
      //buffer.append("\n");
      buffer.append(" throws ");
      curIndent += 9; // ") throws ".length()
      indent = StringUtil.repeatSymbol(' ', curIndent);
      for (int i = 0; i < thrownExceptionsFragments.length; i++) {
        String text = thrownExceptionsFragments[i].getText();
        if (i != 0) buffer.append(indent);
        buffer.append(text);
        if (i < thrownExceptionsFragments.length - 1) {
          buffer.append(",");
        }
        buffer.append("\n");
      }
    }

    return buffer.toString();
  }
}

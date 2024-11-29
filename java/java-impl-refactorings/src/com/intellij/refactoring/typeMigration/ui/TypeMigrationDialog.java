// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.CommonBundle;
import com.intellij.find.FindUsagesSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.*;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 */
public abstract class TypeMigrationDialog extends RefactoringDialog {
  private static final Logger LOG = Logger.getInstance(TypeMigrationDialog.class);

  protected final PsiElement[] myRoots;
  private TypeMigrationRules myRules;
  private final ScopeChooserCombo myScopeChooserCombo;

  public TypeMigrationDialog(@NotNull Project project, PsiElement @NotNull [] roots, @Nullable TypeMigrationRules rules) {
    super(project, false);
    myRoots = roots;
    myRules = rules;

    myScopeChooserCombo = new ScopeChooserCombo(project, false, true, FindUsagesSettings.getInstance().getDefaultScopeName());
    Disposer.register(myDisposable, myScopeChooserCombo);
    myScopeChooserCombo.getChildComponent().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        validateButtons();
      }
    });
    setTitle(JavaRefactoringBundle.message("type.migration.action.name"));
  }

  @Override
  protected void doAction() {
    if (myScopeChooserCombo.getSelectedScope() == null) {
      Messages.showErrorDialog(JavaRefactoringBundle.message("type.migration.no.scope.warning.message"), CommonBundle.getErrorTitle());
      return;
    }
    FindUsagesSettings.getInstance().setDefaultScopeName(myScopeChooserCombo.getSelectedScopeName());
    if (myRules == null) {
      myRules = new TypeMigrationRules(getProject());
      myRules.setBoundScope(myScopeChooserCombo.getSelectedScope());
    }
    invokeRefactoring(new TypeMigrationProcessor(myProject, myRoots, getMigrationTypeFunction(), myRules, true));
  }

  @NotNull
  protected abstract Function<? super PsiElement, ? extends PsiType> getMigrationTypeFunction();

  protected void appendMigrationTypeEditor(JPanel panel, GridBagConstraints cs) {
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                         GridBagConstraints.HORIZONTAL, JBUI.insets(5, 5, 0, 0), 0, 0);
    appendMigrationTypeEditor(panel, gc);
    LabeledComponent<ScopeChooserCombo> scopeChooserComponent = new LabeledComponent<>();
    scopeChooserComponent.setComponent(myScopeChooserCombo);
    scopeChooserComponent.setText(JavaRefactoringBundle.message("type.migration.choose.scope.title"));
    panel.add(scopeChooserComponent, gc);
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myScopeChooserCombo;
  }

  @Override
  protected String getHelpId() {
    return "reference.typeMigrationDialog";
  }

  public static class MultipleElements extends TypeMigrationDialog {
    private final Function<? super PsiElement, ? extends PsiType> myMigrationTypeFunction;

    public MultipleElements(@NotNull Project project,
                            PsiElement @NotNull [] roots,
                            @NotNull Function<? super PsiElement, ? extends PsiType> migrationTypeFunction,
                            @NotNull TypeMigrationRules rules) {
      super(project, roots, rules);
      myMigrationTypeFunction = migrationTypeFunction;
      init();
    }

    @Override
    protected @NotNull Function<? super PsiElement, ? extends PsiType> getMigrationTypeFunction() {
      return myMigrationTypeFunction;
    }
  }

  public static class SingleElement extends TypeMigrationDialog {
    private final PsiTypeCodeFragment myTypeCodeFragment;
    private final EditorComboBox myToTypeEditor;

    public SingleElement(@NotNull Project project, PsiElement @NotNull [] roots) {
      super(project, roots, null);
      LOG.assertTrue(roots.length > 0);
      final PsiType rootType = getRootType();
      final String text = rootType != null ? rootType.getCanonicalText(true) : "";
      int flags = 0;
      PsiElement root = roots[0];
      if (PsiUtil.isAvailable(JavaFeature.VARARGS, root)) {
        flags |= JavaCodeFragmentFactory.ALLOW_ELLIPSIS;
      }
      if (PsiUtil.isAvailable(JavaFeature.MULTI_CATCH, root)) {
        flags |= JavaCodeFragmentFactory.ALLOW_DISJUNCTION;
      }
      flags |= JavaCodeFragmentFactory.ALLOW_VOID;
      myTypeCodeFragment = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment(text, root, true, flags);

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final Document document = documentManager.getDocument(myTypeCodeFragment);
      assert document != null;
      myToTypeEditor = new EditorComboBox(document, project, JavaFileType.INSTANCE);
      final String[] types = getValidTypes(project, root);
      myToTypeEditor.setHistory(types != null ? types : new String[]{document.getText()});
      document.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          documentManager.commitDocument(document);
          validateButtons();
        }
      });
      init();
      validateButtons();
    }

    @Override
    protected void canRun() throws ConfigurationException {
      super.canRun();
      if (isIllegalVarargMigration()) {
        throw new ConfigurationException(JavaBundle.message("type.migration.dialog.message.vararg.type.not.applicable"));
      }
      if (isIllegalDisjunctionTypeMigration()) {
        throw new ConfigurationException(JavaBundle.message("type.migration.dialog.message.disjunction.type.not.applicable"));
      }
      if (isIllegalTypeMigration(getMigrationType())) {
        throw new ConfigurationException(
          JavaBundle.message("type.migration.dialog.message.invalid.type", StringUtil.escapeXmlEntities(myTypeCodeFragment.getText())));
      }
      if (isIllegalVoidMigration()) {
        throw new ConfigurationException(JavaBundle.message("type.migration.dialog.message.void.not.applicable"));
      }
      if (getMigrationType().equals(getRootType())) {
        throw new ConfigurationException(null);
      }
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myToTypeEditor;
    }

    @Override
    protected void appendMigrationTypeEditor(JPanel panel, GridBagConstraints gc) {
      final PsiType type = getRootType();
      final String typeText = type != null ? type.getPresentableText() : "<unknown>";
      panel.add(new JLabel(getTypeMigrationLabelText(myRoots[0], typeText)), gc);
      panel.add(myToTypeEditor, gc);
    }

    private String @Nullable [] getValidTypes(Project project, PsiElement root) {
      if (root instanceof PsiField || root instanceof PsiMethod) {
        final PsiModifierList modifierList = ((PsiModifierListOwner)root).getModifierList();
        if (VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(modifierList), PsiModifier.PRIVATE) < 0) return null;
      }

      final List<PsiExpression> expressions = new ArrayList<>();
      for (PsiReference reference : ReferencesSearch.search(root, GlobalSearchScope.fileScope(root.getContainingFile()))) {
        final PsiElement element = reference.getElement();
        final PsiExpression expr = PsiTreeUtil.getParentOfType(element, PsiExpression.class, false);
        if (expr != null) {
          expressions.add(expr);
        }
      }
      try {
        final PsiExpression[] occurrences = expressions.toArray(PsiExpression.EMPTY_ARRAY);
        PsiType type = myTypeCodeFragment.getType();
        PsiType[] psiTypes = new TypeSelectorManagerImpl(project, type, occurrences).getTypesForAll();
        if (root instanceof PsiMethod) {
          psiTypes = ArrayUtil.append(psiTypes, PsiTypes.voidType());
        }
        if (type instanceof PsiDisjunctionType) {
          psiTypes = ArrayUtil.prepend(type, psiTypes);
        }

        if (psiTypes.length > 0) {
          final String[] history = new String[psiTypes.length];
          for (int i = 0; i < psiTypes.length; i++) {
            PsiType psiType = psiTypes[i];
            history[i] = psiType.getCanonicalText(true);
          }
          return history;
        }
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e) {
        LOG.info(e);
        return null;
      }
      return null;
    }

    @Override
    protected void doAction() {
      final PsiType rootType = getRootType();
      final PsiType migrationType = getMigrationType();
      if (migrationType == null || CommonJavaRefactoringUtil.deepTypeEqual(rootType, migrationType)) {
        close(DialogWrapper.OK_EXIT_CODE);
        return;
      }
      super.doAction();
    }

    @Override
    protected @NotNull Function<? super PsiElement, ? extends PsiType> getMigrationTypeFunction() {
      return Functions.constant(getMigrationType());
    }

    @Nullable
    public PsiType getMigrationType() {
      try {
        return myTypeCodeFragment.getType();
      }
      catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e) {
        LOG.debug(e);
        return null;
      }
    }

    @Nullable
    private PsiType getRootType() {
      return TypeMigrationLabeler.getElementType(myRoots[0]);
    }

    private static @NlsContexts.Label String getTypeMigrationLabelText(PsiElement element, String type) {
      if (element instanceof PsiMethod method) {
        String methodText = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                       PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                       PsiFormatUtilBase.SHOW_TYPE);
        return JavaRefactoringBundle.message("type.migration.return.type.of.method.label", type, methodText);
      }
      else if (element instanceof PsiField field) {
        String variableText = PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        return JavaRefactoringBundle.message("type.migration.type.of.field.label", type, variableText);
      }
      else if (element instanceof PsiLocalVariable variable) {
        String variableText = PsiFormatUtil.formatVariable(variable, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        return JavaRefactoringBundle.message("type.migration.type.of.variable.label", type, variableText);
      }
      else if (element instanceof PsiParameter parameter) {
        PsiElement scope = parameter.getDeclarationScope();
        boolean realParameter = scope instanceof PsiMethod || scope instanceof PsiLambdaExpression;
        String variableText = PsiFormatUtil.formatVariable(parameter, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        return JavaRefactoringBundle.message(realParameter
                                             ? "type.migration.type.of.parameter.label"
                                             : "type.migration.type.of.variable.label",
                                             type, variableText);
      }
      else if (element instanceof PsiReferenceParameterList) {
        return JavaRefactoringBundle.message("type.migration.class.type.argument.label", type);
      }
      else if (element instanceof PsiRecordComponent component) {
        String variableText = PsiFormatUtil.formatVariable(component, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        return JavaRefactoringBundle.message("type.migration.type.of.record.component.label", type, variableText);
      }
      throw new AssertionError("unknown element: " + element);
    }

    private boolean isIllegalVarargMigration() {
      if (!(getMigrationType() instanceof PsiEllipsisType)) return false;
      for (PsiElement root : myRoots) {
        if (!(root instanceof PsiParameter parameter)) return true;
        PsiElement scope = parameter.getDeclarationScope();
        if (!(scope instanceof PsiMethod method)) return true;
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter lastParameter = parameterList.getParameter(parameterList.getParametersCount() - 1);
        if (!parameter.equals(lastParameter)) return true;
      }
      return false;
    }

    private boolean isIllegalDisjunctionTypeMigration() {
      if (!(getMigrationType() instanceof PsiDisjunctionType)) return false;
      for (PsiElement root : myRoots) {
        if (!(root instanceof PsiParameter parameter)) return true;
        PsiElement scope = parameter.getDeclarationScope();
        if (!(scope instanceof PsiCatchSection)) return true;
      }
      return false;
    }

    private boolean isIllegalVoidMigration() {
      if (!PsiTypes.voidType().equals(getMigrationType())) return false;
      for (PsiElement root : myRoots) {
        if (!(root instanceof PsiMethod)) return true;
      }
      return false;
    }

    private boolean isIllegalTypeMigration(PsiType type) {
      if (type == null) return true;
      if (!type.isValid()) return true;
      if (type instanceof PsiClassType psiClassType){
        if (psiClassType.resolve() == null) return true;
        final PsiType[] types = psiClassType.getParameters();
        for (PsiType paramType : types) {
          if (paramType instanceof PsiPrimitiveType ||
              (paramType instanceof PsiWildcardType && ((PsiWildcardType)paramType).getBound() instanceof PsiPrimitiveType)) {
            return true;
          }
          if (isIllegalTypeMigration(paramType)) return true;
        }
      }
      if (type instanceof PsiArrayType) {
        PsiType componentType = type.getDeepComponentType();
        if (PsiTypes.voidType().equals(componentType)) return true;
        return isIllegalTypeMigration(componentType);
      }
      if (type instanceof PsiDisjunctionType disjunctionType) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(myRoots[0].getProject());
        PsiClassType throwable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, myRoots[0].getResolveScope());
        List<PsiType> disjunctions = disjunctionType.getDisjunctions();
        for (PsiType disjunction : disjunctions) {
          if (isIllegalTypeMigration(disjunction)) return true;
          if (!TypeConversionUtil.isAssignable(throwable, type)) return true;
        }
      }
      return false;
    }
  }
}
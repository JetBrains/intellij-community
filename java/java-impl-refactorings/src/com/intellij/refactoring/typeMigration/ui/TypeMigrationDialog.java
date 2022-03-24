// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.ui;

import com.intellij.CommonBundle;
import com.intellij.find.FindSettings;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.EditorComboBox;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.Functions;
import com.intellij.util.VisibilityUtil;
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

  public TypeMigrationDialog(@NotNull Project project,
                             PsiElement @NotNull [] roots,
                             @Nullable TypeMigrationRules rules) {
    super(project, false);
    myRoots = roots;
    myRules = rules;

    myScopeChooserCombo = new ScopeChooserCombo(project, false, true, FindSettings.getInstance().getDefaultScopeName());
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
    FindSettings.getInstance().setDefaultScopeName(myScopeChooserCombo.getSelectedScopeName());
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

    public SingleElement(@NotNull Project project,
                         PsiElement @NotNull [] roots) {
      super(project, roots, null);
      LOG.assertTrue(roots.length > 0);
      final PsiType rootType = getRootType();
      final String text = rootType != null ? rootType.getCanonicalText(true) : "";
      int flags = 0;
      PsiElement root = roots[0];
      if (root instanceof PsiParameter) {
        final PsiElement scope = ((PsiParameter)root).getDeclarationScope();
        if (scope instanceof PsiMethod) {
          flags |= JavaCodeFragmentFactory.ALLOW_ELLIPSIS;
        }
        else if (scope instanceof PsiCatchSection && PsiUtil.getLanguageLevel(root).isAtLeast(LanguageLevel.JDK_1_7)) {
          flags |= JavaCodeFragmentFactory.ALLOW_DISJUNCTION;
        }
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
        public void documentChanged(@NotNull final DocumentEvent e) {
          documentManager.commitDocument(document);
          validateButtons();
        }
      });
      init();
    }

    @Override
    protected void canRun() throws ConfigurationException {
      super.canRun();
      if (!checkType(getMigrationType()))
        throw new ConfigurationException(
          JavaBundle.message("type.migration.dialog.message.invalid.type", StringUtil.escapeXmlEntities(myTypeCodeFragment.getText())));
      if (isVoidVariableMigration()) throw new ConfigurationException(
        JavaBundle.message("type.migration.dialog.message.void.not.applicable"));
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myToTypeEditor;
    }

    @Override
    protected void appendMigrationTypeEditor(JPanel panel, GridBagConstraints gc) {
      final PsiType type = getRootType();
      final String typeText = type != null ? type.getPresentableText() : "<unknown>";
      panel.add(new JLabel(JavaRefactoringBundle.message("type.migration.label", getElementPresentation(myRoots[0]), typeText)), gc);
      panel.add(myToTypeEditor, gc);
    }

    private String @Nullable [] getValidTypes(final Project project, final PsiElement root) {
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
        final PsiType[] psiTypes = new TypeSelectorManagerImpl(project, myTypeCodeFragment.getType(), occurrences).getTypesForAll();
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

    private static String getElementPresentation(PsiElement element) {
      if (element instanceof PsiMethod) {
        return "return type of method " + ((PsiMethod)element).getName();
      }

      if (element instanceof PsiField) {
        return "type of field " + ((PsiField)element).getName();
      }

      if (element instanceof PsiLocalVariable) {
        return "type of variable " + ((PsiLocalVariable)element).getName();
      }

      if (element instanceof PsiReferenceParameterList) {
        return "class type arguments ";
      }

      if (element instanceof PsiParameter) {
        final PsiParameter param = (PsiParameter)element;
        String result = "type of parameter " + param.getName();
        if (param.getParent() instanceof PsiParameterList) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(param, PsiMethod.class);
          assert method != null;
          result  += " of method " + method.getName();
        }
        return result;
      }

      return element.toString();
    }

    private boolean isVoidVariableMigration() {
      if (!PsiType.VOID.equals(getMigrationType())) return false;
      for (PsiElement root : myRoots) {
        if (root instanceof PsiVariable) return true;
      }
      return false;
    }

    private static boolean checkType(final PsiType type) {
      if (type == null) return false;
      if (!type.isValid()) return false;
      if (type instanceof PsiClassType){
        final PsiClassType psiClassType = (PsiClassType)type;
        if (psiClassType.resolve() == null) return false;
        final PsiType[] types = psiClassType.getParameters();
        for (PsiType paramType : types) {
          if (paramType instanceof PsiPrimitiveType ||
              (paramType instanceof PsiWildcardType && ((PsiWildcardType)paramType).getBound() instanceof PsiPrimitiveType)) {
            return false;
          }
          if (!checkType(paramType)) return false;
        }
      }
      if (type instanceof PsiArrayType) {
        return checkType(type.getDeepComponentType());
      }
      return true;
    }
  }
}
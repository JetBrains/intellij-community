/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;

public abstract class LocalToFieldHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.LocalToFieldHandler");

  private static final String REFACTORING_NAME = RefactoringBundle.message("convert.local.to.field.title");
  private final Project myProject;
  private final boolean myIsConstant;

  public LocalToFieldHandler(Project project, boolean isConstant) {
    myProject = project;
    myIsConstant = isConstant;
  }

  protected abstract BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences, boolean isStatic);

  public boolean convertLocalToField(final PsiLocalVariable local, final Editor editor) {
    boolean tempIsStatic = myIsConstant;
    PsiElement parent = local.getParent();
    final List<PsiClass> classes = new ArrayList<>();
    while (parent != null && parent.getContainingFile() != null) {
      if (parent instanceof PsiClass && !(myIsConstant && parent instanceof PsiAnonymousClass)) {
        classes.add((PsiClass)parent);
      }
      if (parent instanceof PsiFile && FileTypeUtils.isInServerPageFile(parent)) {
        String message = RefactoringBundle.message("error.not.supported.for.jsp", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.LOCAL_TO_FIELD);
        return false;
      }
      if (parent instanceof PsiModifierListOwner &&((PsiModifierListOwner)parent).hasModifierProperty(PsiModifier.STATIC)) {
        tempIsStatic = true;
      }
      parent = parent.getParent();
    }

    if (classes.isEmpty()) return false;
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    final boolean shouldSuggestDialog = activeIntroducer instanceof InplaceIntroduceConstantPopup &&
                                         activeIntroducer.startsOnTheSameElement(null, local);
    if (classes.size() == 1 || ApplicationManager.getApplication().isUnitTestMode() || shouldSuggestDialog) {
      if (convertLocalToField(local, classes.get(getChosenClassIndex(classes)), editor, tempIsStatic)) return false;
    } else {
      final boolean isStatic = tempIsStatic;
      final PsiClass firstClass = classes.get(0);
      final PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes, firstClass);
      NavigationUtil.getPsiElementPopup(classes.toArray(new PsiClass[classes.size()]), PsiClassListCellRenderer.INSTANCE, "Choose class to introduce " + (myIsConstant ? "constant" : "field"), new PsiElementProcessor<PsiClass>() {
        @Override
        public boolean execute(@NotNull PsiClass aClass) {
          AnonymousTargetClassPreselectionUtil.rememberSelection(aClass, aClass);
          convertLocalToField(local, aClass, editor, isStatic);
          return false;
        }
      }, preselection).showInBestPositionFor(editor);
    }

    return true;
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }

  private boolean convertLocalToField(PsiLocalVariable local, PsiClass aClass, Editor editor, boolean isStatic) {
    final PsiExpression[] occurences = CodeInsightUtil.findReferenceExpressions(RefactoringUtil.getVariableScope(local), local);
    if (editor != null) {
      RefactoringUtil.highlightAllOccurrences(myProject, occurences, editor);
    }

    final BaseExpressionToFieldHandler.Settings settings = showRefactoringDialog(aClass, local, occurences, isStatic);
    if (settings == null) return true;
    //LocalToFieldDialog dialog = new LocalToFieldDialog(project, aClass, local, isStatic);
    final PsiClass destinationClass = settings.getDestinationClass();
    boolean rebindNeeded = false;
    if (destinationClass != null) {
      aClass = destinationClass;
      rebindNeeded = true;
    }

    final PsiClass aaClass = aClass;
    final boolean rebindNeeded1 = rebindNeeded;
    final Runnable runnable =
      new IntroduceFieldRunnable(rebindNeeded1, local, aaClass, settings, isStatic, occurences);
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(runnable), REFACTORING_NAME, null);
    return false;
  }

  private static PsiField createField(PsiLocalVariable local, PsiType forcedType, String fieldName, boolean includeInitializer) {
    @NonNls StringBuilder pattern = new StringBuilder();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (local.getInitializer() == null) {
      includeInitializer = false;
    }
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    final Project project = local.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);

      field.getTypeElement().replace(factory.createTypeElement(forcedType));
      if (includeInitializer) {
        PsiExpression initializer =
          RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), forcedType);
        field.getInitializer().replace(initializer);
      }

      for (PsiAnnotation annotation : local.getModifierList().getAnnotations()) {
        field.getModifierList().add(annotation.copy());
      }
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiExpressionStatement createAssignment(PsiLocalVariable local, String fieldname, PsiElementFactory factory) {
    try {
      String pattern = fieldname + "=0;";
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(local.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression initializer = RefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), local.getType());
      expr.getRExpression().replace(initializer);

      return statement;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiStatement addInitializationToSetUp(final PsiLocalVariable local, final PsiField field, final PsiElementFactory factory)
                                                                                                                             throws IncorrectOperationException {
    PsiMethod inClass = TestFrameworks.getInstance().findOrCreateSetUpMethod(field.getContainingClass());
    assert inClass != null;
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    final PsiCodeBlock body = inClass.getBody();
    assert body != null;
    if (PsiTreeUtil.isAncestor(body, local, false)) {
      assignment = (PsiStatement)body.addBefore(assignment, PsiTreeUtil.getParentOfType(local, PsiStatement.class));
    } else {
      assignment = (PsiStatement)body.add(assignment);
    }
    appendComments(local, assignment);
    local.delete();
    return assignment;
  }

  private static PsiStatement addInitializationToConstructors(PsiLocalVariable local, PsiField field, PsiMethod enclosingConstructor,
                                                      PsiElementFactory factory) throws IncorrectOperationException {
    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    boolean added = false;
    for (PsiMethod constructor : constructors) {
      if (constructor == enclosingConstructor) continue;
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            @NonNls String text = ((PsiMethodCallExpression)expression).getMethodExpression().getText();
            if ("this".equals(text)) {
              continue;
            }
            if ("super".equals(text) && enclosingConstructor == null && PsiTreeUtil.isAncestor(constructor, local, false)) {
              final PsiStatement statement = (PsiStatement)body.addAfter(assignment, first);
              appendComments(local, statement);
              local.delete();
              return statement;
            }
          }
        }
        if (enclosingConstructor == null && PsiTreeUtil.isAncestor(constructor, local, false)) {
          final PsiStatement statement = (PsiStatement)body.addBefore(assignment, first);
          appendComments(local, statement);
          local.delete();
          return statement;
        }
      }

      assignment = (PsiStatement)body.add(assignment);
      added = true;
    }
    if (!added && enclosingConstructor == null) {
      if (aClass instanceof PsiAnonymousClass) {
        final PsiClassInitializer classInitializer = (PsiClassInitializer)aClass.addAfter(factory.createClassInitializer(), field);
        assignment = (PsiStatement)classInitializer.getBody().add(assignment);
      } else {
        PsiMethod constructor = (PsiMethod)aClass.add(factory.createConstructor());
        assignment = (PsiStatement)constructor.getBody().add(assignment);
      }
    }

    if (enclosingConstructor == null) {
      appendComments(assignment, local);
      local.delete();
    }
    return assignment;
  }

  static class IntroduceFieldRunnable implements Runnable {
    private final String myVariableName;
    private final String myFieldName;
    private final boolean myRebindNeeded;
    private final PsiLocalVariable myLocal;
    private final Project myProject;
    private final PsiClass myDestinationClass;
    private final BaseExpressionToFieldHandler.Settings mySettings;
    private final BaseExpressionToFieldHandler.InitializationPlace myInitializerPlace;
    private final PsiExpression[] myOccurences;
    private PsiField myField;

    public IntroduceFieldRunnable(boolean rebindNeeded,
                                  PsiLocalVariable local,
                                  PsiClass aClass,
                                  BaseExpressionToFieldHandler.Settings settings,
                                  boolean isStatic,
                                  PsiExpression[] occurrences) {
      myVariableName = local.getName();
      myFieldName = settings.getFieldName();
      myRebindNeeded = rebindNeeded;
      myLocal = local;
      myProject = local.getProject();
      myDestinationClass = aClass;
      mySettings = settings;
      myInitializerPlace = settings.getInitializerPlace();
      myOccurences = occurrences;
    }

    public void run() {
      try {
        ChangeContextUtil.encodeContextInfo(myDestinationClass, true);
        final boolean rebindNeeded2 = !myVariableName.equals(myFieldName) || myRebindNeeded;
        final PsiReference[] refs;
        if (rebindNeeded2) {
          refs = ReferencesSearch.search(myLocal, GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
        }
        else {
          refs = null;
        }

        final PsiMethod enclosingConstructor = BaseExpressionToFieldHandler.getEnclosingConstructor(myDestinationClass, myLocal);
        myField = mySettings.isIntroduceEnumConstant() ? EnumConstantsUtil.createEnumConstant(myDestinationClass, myLocal, myFieldName)
                                                       : createField(myLocal, mySettings.getForcedType(), myFieldName, myInitializerPlace == IN_FIELD_DECLARATION);
        myField = (PsiField)myDestinationClass.add(myField);
        BaseExpressionToFieldHandler.setModifiers(myField, mySettings);
        if (!mySettings.isIntroduceEnumConstant()) {
          VisibilityUtil.fixVisibility(myOccurences, myField, mySettings.getFieldVisibility());
        }

        myLocal.normalizeDeclaration();
        PsiElement declarationStatement = myLocal.getParent();
        final BaseExpressionToFieldHandler.InitializationPlace finalInitializerPlace;
        if (myLocal.getInitializer() == null) {
          finalInitializerPlace = IN_FIELD_DECLARATION;
        }
        else {
          finalInitializerPlace = myInitializerPlace;
        }
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();

        switch (finalInitializerPlace) {
          case IN_FIELD_DECLARATION:
            appendComments(declarationStatement, myField);
            declarationStatement.delete();
            break;

          case IN_CURRENT_METHOD:
            PsiExpressionStatement statement = createAssignment(myLocal, myFieldName, factory);
            appendComments(declarationStatement, declarationStatement);
            if (declarationStatement instanceof PsiDeclarationStatement) {
              declarationStatement.replace(statement);
            } else {
              myLocal.replace(statement.getExpression());
            }
            break;

          case IN_CONSTRUCTOR:
            addInitializationToConstructors(myLocal, myField, enclosingConstructor, factory);
            break;
          case IN_SETUP_METHOD:
            addInitializationToSetUp(myLocal, myField, factory);
        }

        if (enclosingConstructor != null && myInitializerPlace == IN_CONSTRUCTOR) {
          PsiStatement statement = createAssignment(myLocal, myFieldName, factory);
          declarationStatement.replace(statement);
        }

        if (rebindNeeded2) {
          for (final PsiReference reference : refs) {
            if (reference != null) {
              //expr = RefactoringUtil.outermostParenthesizedExpression(expr);
              RefactoringUtil.replaceOccurenceWithFieldRef((PsiExpression)reference, myField, myDestinationClass);
              //replaceOccurenceWithFieldRef((PsiExpression)reference, field, aaClass);
            }
          }
          //RefactoringUtil.renameVariableReferences(local, pPrefix + fieldName, GlobalSearchScope.projectScope(myProject));
          ChangeContextUtil.decodeContextInfo(myDestinationClass, myDestinationClass, null);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    public PsiField getField() {
      return myField;
    }
  }

  private static void appendComments(PsiElement declarationStatement, PsiElement element) {
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(declarationStatement, PsiComment.class);
    final PsiElement parent = element.getParent();
    for (PsiComment comment : comments) {
      parent.addBefore(comment, element);
    }
  }
}

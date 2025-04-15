// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
import static com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;

public abstract class LocalToFieldHandler {
  private static final Logger LOG = Logger.getInstance(LocalToFieldHandler.class);

  private final Project myProject;
  private final boolean myIsConstant;

  public LocalToFieldHandler(Project project, boolean isConstant) {
    myProject = project;
    myIsConstant = isConstant;
  }

  protected abstract BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences, boolean isStatic);

  public boolean convertLocalToField(final PsiLocalVariable local, final Editor editor) {
    boolean tempIsStatic = myIsConstant;
    boolean compileTimeConstant = isCompileTimeConstant(local.getInitializer(), local.getType());
    final List<PsiClass> classes = new ArrayList<>();
    PsiElement parent = local.getParent();
    while (parent != null && parent.getContainingFile() != null) {
      if (parent instanceof PsiClass && (compileTimeConstant || !myIsConstant || isStaticFieldAllowed((PsiClass) parent))) {
        classes.add((PsiClass)parent);
      }
      if (parent instanceof PsiFile && FileTypeUtils.isInServerPageFile(parent)) {
        String message = JavaRefactoringBundle.message("error.not.supported.for.jsp", getRefactoringName());
        CommonRefactoringUtil.showErrorHint(myProject, editor, message, getRefactoringName(), HelpID.LOCAL_TO_FIELD);
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
      if (!convertLocalToField(local, classes.get(getChosenClassIndex(classes)), editor, tempIsStatic)) return false;
    } else {
      final boolean isStatic = tempIsStatic;
      final PsiClass firstClass = classes.get(0);
      final PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes, firstClass);
      String title = myIsConstant ? JavaRefactoringBundle.message("popup.title.choose.class.to.introduce.constant")
                                  : JavaRefactoringBundle.message("popup.title.choose.class.to.introduce.field");
      new PsiTargetNavigator<>(classes.toArray(PsiClass.EMPTY_ARRAY)).selection(preselection).createPopup(myProject,
                                                title, new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiClass aClass) {
            AnonymousTargetClassPreselectionUtil.rememberSelection(aClass, aClass);
            convertLocalToField(local, aClass, editor, isStatic);
            return false;
          }
        }).showInBestPositionFor(editor);
    }

    return true;
  }

  public static boolean isCompileTimeConstant(@Nullable PsiExpression initializer, @Nullable PsiType type) {
    return type != null && (type instanceof PsiPrimitiveType || type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) &&
           PsiUtil.isConstantExpression(initializer);
  }

  /**
   * @deprecated Use {@link #isStaticFieldAllowed(PsiClass)} instead.
   */
  @Deprecated(forRemoval = true)
  public static boolean mayContainConstants(@NotNull PsiClass aClass) {
    return isStaticFieldAllowed(aClass);
  }

  /**
   * Checks if adding a static field is allowed in the specified class.
   * Before Java 16 this was only allowed for inner, local and anonymous classes,
   * if the static field was a compile-time constant.
   * @param aClass  the class to check
   * @return true, if adding a non-compile-time constants static field to the specified class is allowed. False otherwise.
   */
  public static boolean isStaticFieldAllowed(@NotNull PsiClass aClass) {
    if (PsiUtil.isAvailable(JavaFeature.INNER_STATICS, aClass)) {
      return true;
    }
    return aClass.hasModifierProperty(PsiModifier.STATIC) || aClass.getParent() instanceof PsiJavaFile;
  }

  protected int getChosenClassIndex(List<PsiClass> classes) {
    return classes.size() - 1;
  }

  private boolean convertLocalToField(PsiLocalVariable local, PsiClass aClass, Editor editor, boolean isStatic) {
    if (!IntroduceFieldHandler.canIntroduceField(aClass, local.getType(), editor)) {
      return false;
    }
    final PsiExpression[] occurrences = CodeInsightUtil.findReferenceExpressions(CommonJavaRefactoringUtil.getVariableScope(local), local);
    if (editor != null) {
      RefactoringUtil.highlightAllOccurrences(myProject, occurrences, editor);
    }

    final BaseExpressionToFieldHandler.Settings settings = showRefactoringDialog(aClass, local, occurrences, isStatic);
    if (settings == null) return false;
    final PsiClass destinationClass = settings.getDestinationClass();
    boolean rebindNeeded = false;
    if (destinationClass != null) {
      aClass = destinationClass;
      rebindNeeded = true;
    }

    final PsiClass aaClass = aClass;
    final boolean rebindNeeded1 = rebindNeeded;
    final Runnable runnable =
      new IntroduceFieldRunnable(rebindNeeded1, local, aaClass, settings, occurrences);
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(runnable),
                                                  getRefactoringName(), null);
    return true;
  }

  private static PsiField createField(PsiLocalVariable local, @NotNull PsiType forcedType, String fieldName, boolean includeInitializer) {
    forcedType = PsiTypesUtil.removeExternalAnnotations(forcedType);
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
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);

      field.getTypeElement().replace(factory.createTypeElement(forcedType));
      if (includeInitializer) {
        PsiExpression initializer =
          CommonJavaRefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), forcedType);
        field.getInitializer().replace(initializer);
      }

      PsiModifierList sourceModifierList = local.getModifierList();
      LOG.assertTrue(sourceModifierList != null);
      PsiModifierList fieldModifierList = field.getModifierList();
      LOG.assertTrue(fieldModifierList != null);
      GenerateMembersUtil.copyAnnotations(sourceModifierList, fieldModifierList);
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
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, local);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(local.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression initializer = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(local.getInitializer(), local.getType());
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
    PsiClass containingClass = field.getContainingClass();
    PsiMethod inClass = TestFrameworks.getInstance().findOrCreateSetUpMethod(containingClass);
    assert inClass != null;
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    final PsiCodeBlock body = inClass.getBody();
    assert body != null;
    ChangeContextUtil.encodeContextInfo(assignment, false);
    if (PsiTreeUtil.isAncestor(body, local, false)) {
      assignment = (PsiStatement)body.addBefore(assignment, PsiTreeUtil.getParentOfType(local, PsiStatement.class));
    } else {
      assignment = (PsiStatement)body.add(assignment);
    }
    ChangeContextUtil.decodeContextInfo(assignment, containingClass, factory.createExpressionFromText("this", null));
    appendComments(local, assignment);
    local.delete();
    return assignment;
  }

  private static PsiStatement addInitializationToConstructors(PsiLocalVariable local, PsiField field, PsiMethod enclosingConstructor,
                                                      PsiElementFactory factory) throws IncorrectOperationException {
    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    PsiStatement assignment = createAssignment(local, field.getName(), factory);
    PsiExpression thisAccessExpr = factory.createExpressionFromText("this", null);
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
              ChangeContextUtil.encodeContextInfo(assignment, false);
              final PsiStatement statement = (PsiStatement)body.addAfter(assignment, first);
              ChangeContextUtil.decodeContextInfo(statement, aClass, thisAccessExpr);
              appendComments(local, statement);
              local.delete();
              return statement;
            }
          }
        }
        if (enclosingConstructor == null && PsiTreeUtil.isAncestor(constructor, local, false)) {
          ChangeContextUtil.encodeContextInfo(assignment, false);
          final PsiStatement statement = (PsiStatement)body.addBefore(assignment, first);
          ChangeContextUtil.decodeContextInfo(statement, aClass, thisAccessExpr);
          appendComments(local, statement);
          local.delete();
          return statement;
        }
      }

      ChangeContextUtil.encodeContextInfo(assignment, false);
      assignment = (PsiStatement)body.add(assignment);
      ChangeContextUtil.decodeContextInfo(assignment, aClass, thisAccessExpr);
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

    IntroduceFieldRunnable(boolean rebindNeeded,
                           PsiLocalVariable local,
                           PsiClass aClass,
                           BaseExpressionToFieldHandler.Settings settings,
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

    @Override
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
        BaseExpressionToFieldHandler.setModifiers(myField, mySettings);
        if (!mySettings.isIntroduceEnumConstant()) {
          VisibilityUtil.fixVisibility(myOccurences, myField, mySettings.getFieldVisibility());
        }
        PsiElement finalAnchorElement = myLocal;
        while (finalAnchorElement != null && finalAnchorElement.getParent() != myDestinationClass) {
          finalAnchorElement = finalAnchorElement.getParent();
        }
        PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? (PsiMember)finalAnchorElement : null;
        //required to check anchors as rearranger allows any configuration 
        myField = BaseExpressionToFieldHandler.ConvertToFieldRunnable
          .appendField(myLocal.getInitializer(), myInitializerPlace, myDestinationClass, myDestinationClass, myField, anchorMember);

        myLocal.normalizeDeclaration();
        PsiElement declarationStatement = myLocal.getParent();
        final BaseExpressionToFieldHandler.InitializationPlace finalInitializerPlace;
        if (myLocal.getInitializer() == null) {
          finalInitializerPlace = IN_FIELD_DECLARATION;
        }
        else {
          finalInitializerPlace = myInitializerPlace;
        }
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

        switch (finalInitializerPlace) {
          case IN_FIELD_DECLARATION -> {
            appendComments(declarationStatement, myField, myLocal.getInitializer());
            declarationStatement.delete();
          }
          case IN_CURRENT_METHOD -> {
            PsiExpressionStatement statement = createAssignment(myLocal, myFieldName, factory);
            appendComments(declarationStatement, declarationStatement, myLocal.getInitializer());
            if (declarationStatement instanceof PsiDeclarationStatement) {
              declarationStatement.replace(statement);
            }
            else {
              myLocal.replace(statement.getExpression());
            }
          }
          case IN_CONSTRUCTOR -> addInitializationToConstructors(myLocal, myField, enclosingConstructor, factory);
          case IN_SETUP_METHOD -> addInitializationToSetUp(myLocal, myField, factory);
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
    appendComments(declarationStatement, element, null);
  }

  private static void appendComments(PsiElement declarationStatement, PsiElement element, PsiElement unchanged) {
    final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(declarationStatement, PsiComment.class);
    final PsiElement parent = element.getParent();
    for (PsiComment comment : comments) {
      if (PsiTreeUtil.isAncestor(unchanged, comment, false)) {
        continue;
      }
      parent.addBefore(comment, element);
    }
  }

  private static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("convert.local.to.field.title");
  }
}

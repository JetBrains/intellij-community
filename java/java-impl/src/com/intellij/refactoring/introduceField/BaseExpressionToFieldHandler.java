/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class BaseExpressionToFieldHandler extends IntroduceHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler");

  public enum InitializationPlace {
    IN_CURRENT_METHOD,
    IN_FIELD_DECLARATION,
    IN_CONSTRUCTOR,
    IN_SETUP_METHOD
  }

  private final boolean myIsConstant;
  private PsiClass myParentClass;

  protected BaseExpressionToFieldHandler(boolean isConstant) {
    myIsConstant = isConstant;
  }

  protected boolean invokeImpl(final Project project, @NotNull final PsiExpression selectedExpr, final Editor editor) {
    final PsiElement element = getPhysicalElement(selectedExpr);

    final PsiFile file = element.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + selectedExpr);
    }

    final PsiType tempType = getTypeByExpression(selectedExpr);
    if (tempType == null || LambdaUtil.notInferredType(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), getHelpID());
      return false;
    }

    if (PsiType.VOID.equals(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), getHelpID());
      return false;
    }

    myParentClass = getParentClass(selectedExpr);
    final List<PsiClass> classes = new ArrayList<>();
    PsiClass aClass = myParentClass;
    while (aClass != null) {
      classes.add(aClass);
      final PsiField psiField = ConvertToFieldRunnable.checkForwardRefs(selectedExpr, aClass);
      if (psiField != null && psiField.getParent() == aClass) break;
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    }
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    final boolean shouldSuggestDialog = activeIntroducer instanceof InplaceIntroduceConstantPopup &&
                                        activeIntroducer.startsOnTheSameElement(selectedExpr, null);
    if (classes.size() == 1 || editor == null || ApplicationManager.getApplication().isUnitTestMode() || shouldSuggestDialog) {
      return !convertExpressionToField(selectedExpr, editor, file, project, tempType);
    }
    else if (!classes.isEmpty()){
      PsiClass selection = AnonymousTargetClassPreselectionUtil.getPreselection(classes, myParentClass);
      NavigationUtil.getPsiElementPopup(classes.toArray(new PsiClass[classes.size()]), new PsiClassListCellRenderer(),
                                        "Choose class to introduce " + (myIsConstant ? "constant" : "field"),
                                        new PsiElementProcessor<PsiClass>() {
                                          @Override
                                          public boolean execute(@NotNull PsiClass aClass) {
                                            AnonymousTargetClassPreselectionUtil.rememberSelection(aClass, myParentClass);
                                            myParentClass = aClass;
                                            convertExpressionToField(selectedExpr, editor, file, project, tempType);
                                            return false;
                                          }
                                        }, selection).showInBestPositionFor(editor);
    }
    return true;
  }

  private boolean convertExpressionToField(PsiExpression selectedExpr,
                                           @Nullable Editor editor,
                                           PsiFile file,
                                           final Project project,
                                           PsiType tempType) {
    if (myParentClass == null) {
      if (FileTypeUtils.isInServerPageFile(file)) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("error.not.supported.for.jsp", getRefactoringName()),
                                            getRefactoringName(), getHelpID());
        return true;
      }
      else if ("package-info.java".equals(file.getName())) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("error.not.supported.for.package.info", getRefactoringName()),
                                            getRefactoringName(), getHelpID());
        return true;
      }
      else {
        LOG.error(file);
        return true;
      }
    }

    if (!validClass(myParentClass, editor)) {
      return true;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return true;

    final OccurrenceManager occurrenceManager = createOccurrenceManager(selectedExpr, myParentClass);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    List<RangeHighlighter> highlighters = null;
    if (editor != null) {
      highlighters = RefactoringUtil.highlightAllOccurrences(project, occurrences, editor);
    }

    PsiElement tempAnchorElement = RefactoringUtil.getParentExpressionAnchorElement(selectedExpr);
    if (!Comparing.strEqual(IntroduceConstantHandler.REFACTORING_NAME, getRefactoringName()) &&
        IntroduceVariableBase.checkAnchorBeforeThisOrSuper(project, editor, tempAnchorElement, getRefactoringName(), getHelpID()))
      return true;

    final Settings settings =
      showRefactoringDialog(project, editor, myParentClass, selectedExpr, tempType,
                            occurrences, tempAnchorElement, anchorStatementIfAll);

    if (settings == null) return true;

    if (settings.getForcedType() != null) {
      tempType = settings.getForcedType();
    }
    final PsiType type = tempType;

    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      for (RangeHighlighter highlighter : highlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
    }


    final Runnable runnable =
      new ConvertToFieldRunnable(settings.getSelectedExpr(), settings, type, settings.getOccurrences(), occurrenceManager,
                                 anchorStatementIfAll, tempAnchorElement, editor,
                                 myParentClass);

    new WriteCommandAction(project, getRefactoringName()){
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        runnable.run();
      }
    }.execute();
    return false;
  }

  public static void setModifiers(PsiField field, Settings settings) {
    if (!settings.isIntroduceEnumConstant()) {
      if (settings.isDeclareStatic()) {
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      }
      if (settings.isDeclareFinal()) {
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      }
      if (settings.isAnnotateAsNonNls()) {
        PsiAnnotation annotation = JavaPsiFacade.getInstance(field.getProject()).getElementFactory()
          .createAnnotationFromText("@" + AnnotationUtil.NON_NLS, field);
        final PsiModifierList modifierList = field.getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.addAfter(annotation, null);
      }
    }
    JavaCodeStyleManager.getInstance(field.getProject()).shortenClassReferences(field);
  }

  public static PsiElement getPhysicalElement(final PsiExpression selectedExpr) {
    PsiElement element = selectedExpr.getUserData(ElementToWorkOn.PARENT);
    if (element == null) element = selectedExpr;
    return element;
  }

  private static TextAttributes highlightAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
                EditorColors.SEARCH_RESULT_ATTRIBUTES
              );
  }

  protected abstract OccurrenceManager createOccurrenceManager(PsiExpression selectedExpr, PsiClass parentClass);

  protected final PsiClass getParentClass() {
    return myParentClass;
  }

  protected abstract boolean validClass(PsiClass parentClass, Editor editor);

  private static PsiElement getNormalizedAnchor(PsiElement anchorElement) {
    PsiElement child = anchorElement;
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (RefactoringUtil.isExpressionAnchorElement(prev)) break;
      if (PsiUtil.isJavaToken(prev, JavaTokenType.LBRACE)) break;
      child = prev;
    }

    child = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
    PsiElement anchor;
    if (child != null) {
      anchor = child;
    }
    else {
      anchor = anchorElement;
    }
    return anchor;
  }

  protected abstract String getHelpID();

  protected abstract Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr,
                                                    PsiType type, PsiExpression[] occurrences, PsiElement anchorElement,
                                                    PsiElement anchorElementIfAll);


  private static PsiType getTypeByExpression(PsiExpression expr) {
    return RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
  }

  public PsiClass getParentClass(@NotNull PsiExpression initializerExpression) {
    PsiElement element = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (element == null) element = initializerExpression.getParent();
    PsiElement parent = element;
    while (parent != null) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass && myIsConstant)) {
        return (PsiClass)parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  public static PsiMethod getEnclosingConstructor(PsiClass parentClass, PsiElement element) {
    if (element == null) return null;
    final PsiMethod[] constructors = parentClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (PsiTreeUtil.isAncestor(constructor, element, false)) {
        if (PsiTreeUtil.getParentOfType(element, PsiClass.class) != parentClass) return null;
        return constructor;
      }
    }
    return null;
  }

  private static void addInitializationToSetUp(final PsiExpression initializer,
                                               final PsiField field,
                                               final PsiExpression[] occurrences,
                                               final boolean replaceAll,
                                               final PsiClass parentClass) throws IncorrectOperationException {
    final PsiMethod setupMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(parentClass);

    assert setupMethod != null;

    PsiElement anchor = null;
    if (PsiTreeUtil.isAncestor(setupMethod, initializer, true)) {
      anchor = replaceAll
               ? RefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, setupMethod)
               : PsiTreeUtil.getParentOfType(initializer, PsiStatement.class);
    }

    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)JavaPsiFacade.getInstance(parentClass.getProject()).getElementFactory()
        .createStatementFromText(field.getName() + "= expr;", null);
    PsiAssignmentExpression expr = (PsiAssignmentExpression)expressionStatement.getExpression();
    final PsiExpression rExpression = expr.getRExpression();
    LOG.assertTrue(rExpression != null);
    rExpression.replace(initializer);

    final PsiCodeBlock body = setupMethod.getBody();
    assert body != null;
    body.addBefore(expressionStatement, anchor);
  }

  private static void addInitializationToConstructors(PsiExpression initializerExpression,
                                                      PsiField field,
                                                      PsiMethod enclosingConstructor, final PsiClass parentClass) {
    try {
      PsiClass aClass = field.getContainingClass();
      PsiMethod[] constructors = aClass.getConstructors();

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
            }
          }
        }
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild(), parentClass);
        assignment = (PsiStatement) body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringChangeUtil.createThisExpression(field.getManager(), null));
        added = true;
      }
      if (!added && enclosingConstructor == null) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
        PsiMethod constructor = (PsiMethod)aClass.add(factory.createConstructor());
        final PsiCodeBlock body = constructor.getBody();
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild(), parentClass);
        assignment = (PsiStatement) body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringChangeUtil.createThisExpression(field.getManager(), null));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiField createField(String fieldName,
                                      PsiType type,
                                      PsiExpression initializerExpr,
                                      boolean includeInitializer, final PsiClass parentClass) {
    @NonNls StringBuilder pattern = new StringBuilder();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    PsiManager psiManager = parentClass.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);
      final PsiTypeElement typeElement = factory.createTypeElement(type);
      field.getTypeElement().replace(typeElement);
      field = (PsiField)CodeStyleManager.getInstance(psiManager.getProject()).reformat(field);
      if (includeInitializer) {
        field.getInitializer().replace(initializerExpr);
      }
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiStatement createAssignment(PsiField field,
                                               PsiExpression initializerExpr,
                                               PsiElement context,
                                               final PsiClass parentClass) {
    try {
      @NonNls String pattern = "x=0;";
      PsiManager psiManager = parentClass.getManager();
      PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression rExpression = expr.getRExpression();
      LOG.assertTrue(rExpression != null);
      rExpression.replace(initializerExpr);
      final PsiReferenceExpression fieldReference = RenameJavaVariableProcessor.createMemberReference(field, context);
      expr.getLExpression().replace(fieldReference);

      return statement;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  protected abstract boolean accept(ElementToWorkOn elementToWorkOn);

  protected ElementToWorkOn.ElementsProcessor<ElementToWorkOn> getElementProcessor(final Project project, final Editor editor) {
    return new ElementToWorkOn.ElementsProcessor<ElementToWorkOn>() {
      @Override
      public boolean accept(ElementToWorkOn el) {
        return BaseExpressionToFieldHandler.this.accept(el);
      }

      @Override
      public void pass(final ElementToWorkOn elementToWorkOn) {
        if (elementToWorkOn == null) {
          return;
        }

        if (elementToWorkOn.getExpression() == null && elementToWorkOn.getLocalVariable() == null) {
          ElementToWorkOn.showNothingSelectedErrorMessage(editor, getRefactoringName(), getHelpID(), project);
          return;
        }

        final boolean hasRunTemplate = LookupManager.getActiveLookup(editor) == null;
        if (elementToWorkOn.getExpression() == null) {
          final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
          final boolean result = invokeImpl(project, localVariable, editor) && hasRunTemplate;
          if (result) {
            editor.getSelectionModel().removeSelection();
          }
        }
        else {
          if (invokeImpl(project, elementToWorkOn.getExpression(), editor) && hasRunTemplate) {
            editor.getSelectionModel().removeSelection();
          }
        }
      }
    };
  }

  protected abstract String getRefactoringName();

  public static class Settings {
    private final String myFieldName;
    private final PsiType myForcedType;

    private final boolean myReplaceAll;
    private final boolean myDeclareStatic;
    private final boolean myDeclareFinal;
    private final InitializationPlace myInitializerPlace;
    private final String myVisibility;
    private final boolean myDeleteLocalVariable;
    private final TargetDestination myTargetClass;
    private final boolean myAnnotateAsNonNls;
    private final boolean myIntroduceEnumConstant;
    private final PsiExpression mySelectedExpr;
    private PsiExpression[] myOccurrences;

    public PsiLocalVariable getLocalVariable() {
      return myLocalVariable;
    }

    public boolean isDeleteLocalVariable() {
      return myDeleteLocalVariable;
    }

    private final PsiLocalVariable myLocalVariable;

    public String getFieldName() {
      return myFieldName;
    }

    public boolean isDeclareStatic() {
      return myDeclareStatic;
    }

    public boolean isDeclareFinal() {
      return myDeclareFinal;
    }

    public InitializationPlace getInitializerPlace() {
      return myInitializerPlace;
    }

    public String getFieldVisibility() {
      return myVisibility;
    }

    @Nullable
    public PsiClass getDestinationClass() {
      return myTargetClass != null ? myTargetClass.getTargetClass() : null;
    }

    public PsiType getForcedType() {
      return myForcedType;
    }

    public boolean isReplaceAll() {
      return myReplaceAll;
    }

    public boolean isAnnotateAsNonNls() {
      return myAnnotateAsNonNls;
    }

    public boolean isIntroduceEnumConstant() {
      return myIntroduceEnumConstant;
    }

    public Settings(String fieldName,
                    PsiExpression selectedExpr,
                    PsiExpression[] occurrences,
                    boolean replaceAll,
                    boolean declareStatic, boolean declareFinal,
                    InitializationPlace initializerPlace, String visibility, PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable,
                    TargetDestination targetDestination,
                    final boolean annotateAsNonNls,
                    final boolean introduceEnumConstant) {

      myFieldName = fieldName;
      myOccurrences = occurrences;
      mySelectedExpr = selectedExpr;
      myReplaceAll = replaceAll;
      myDeclareStatic = declareStatic;
      myDeclareFinal = declareFinal;
      myInitializerPlace = initializerPlace;
      myVisibility = visibility;
      myLocalVariable = localVariableToRemove;
      myDeleteLocalVariable = deleteLocalVariable;
      myForcedType = forcedType;
      myTargetClass = targetDestination;
      myAnnotateAsNonNls = annotateAsNonNls;
      myIntroduceEnumConstant = introduceEnumConstant;
    }

    public Settings(String fieldName,
                    PsiExpression selectedExpression,
                    PsiExpression[] occurrences,
                    boolean replaceAll,
                    boolean declareStatic, boolean declareFinal,
                    InitializationPlace initializerPlace, String visibility, PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable,
                    PsiClass targetClass,
                    final boolean annotateAsNonNls,
                    final boolean introduceEnumConstant) {

      this(fieldName, selectedExpression, occurrences, replaceAll, declareStatic, declareFinal, initializerPlace, visibility, localVariableToRemove, forcedType, deleteLocalVariable, new TargetDestination(targetClass), annotateAsNonNls, introduceEnumConstant);
    }

    public PsiExpression getSelectedExpr() {
      return mySelectedExpr;
    }

    public PsiExpression[] getOccurrences() {
      return myOccurrences;
    }

    public void setOccurrences(PsiExpression[] occurrences) {
      myOccurrences = occurrences;
    }
  }

  public static class TargetDestination {
    private final String myQualifiedName;
    private final Project myProject;

    private PsiClass myParentClass;
    private PsiClass myTargetClass;

    public TargetDestination(String qualifiedName, PsiClass parentClass) {
      myQualifiedName = qualifiedName;
      myParentClass = parentClass;
      myProject = parentClass.getProject();
    }

    public TargetDestination(@NotNull PsiClass targetClass) {
      myTargetClass = targetClass;
      myQualifiedName = targetClass.getQualifiedName();
      myProject = targetClass.getProject();
    }

    @Nullable
    public PsiClass getTargetClass() {
      if (myTargetClass != null) return myTargetClass;
      final String packageName = StringUtil.getPackageName(myQualifiedName);
      final String shortName = StringUtil.getShortName(myQualifiedName);
      if (Comparing.strEqual(myParentClass.getQualifiedName(), packageName)) {
        myTargetClass = (PsiClass)myParentClass.add(JavaPsiFacade.getElementFactory(myProject).createClass(shortName));
        return myTargetClass;
      }
      PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
      final PsiDirectory psiDirectory;
      if (psiPackage != null) {
        final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.allScope(myProject));
        psiDirectory = directories.length > 1 ? DirectoryChooserUtil.chooseDirectory(directories, null, myProject, new HashMap<>()) : directories[0];
      } else {
        psiDirectory = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myParentClass.getContainingFile().getContainingDirectory(), false);
      }
      myTargetClass = psiDirectory != null ? JavaDirectoryService.getInstance().createClass(psiDirectory, shortName) : null;
      return myTargetClass;
    }
  }

  public static class ConvertToFieldRunnable implements Runnable {
    private PsiExpression mySelectedExpr;
    private final Settings mySettings;
    private final PsiElement myAnchorElement;
    private final Project myProject;
    private final String myFieldName;
    private final PsiType myType;
    private final PsiExpression[] myOccurrences;
    private final boolean myReplaceAll;
    private final OccurrenceManager myOccurrenceManager;
    private final PsiElement myAnchorStatementIfAll;
    private final PsiElement myAnchorElementIfOne;
    private final Boolean myOutOfCodeBlockExtraction;
    private final PsiElement myElement;
    private boolean myDeleteSelf;
    private final Editor myEditor;
    private final PsiClass myParentClass;

    private PsiField myField;

    public ConvertToFieldRunnable(PsiExpression selectedExpr,
                                  Settings settings,
                                  PsiType type,
                                  PsiExpression[] occurrences,
                                  OccurrenceManager occurrenceManager,
                                  PsiElement anchorStatementIfAll,
                                  PsiElement anchorElementIfOne,
                                  Editor editor,
                                  PsiClass parentClass) {
      mySelectedExpr = selectedExpr;
      mySettings = settings;
      myAnchorElement = settings.isReplaceAll() ? anchorStatementIfAll : anchorElementIfOne;
      myProject = selectedExpr.getProject();
      myFieldName = settings.getFieldName();
      myType = type;
      myOccurrences = occurrences;
      myReplaceAll = settings.isReplaceAll();
      myOccurrenceManager = occurrenceManager;
      myAnchorStatementIfAll = anchorStatementIfAll;
      myAnchorElementIfOne = anchorElementIfOne;
      myOutOfCodeBlockExtraction = selectedExpr.getUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK);
      myDeleteSelf = myOutOfCodeBlockExtraction != null;
      myElement = getPhysicalElement(selectedExpr);
      if (myElement.getParent() instanceof PsiExpressionStatement && getNormalizedAnchor(myAnchorElement).equals(myAnchorElement) && selectedExpr.isPhysical()) {
        PsiStatement statement = (PsiStatement)myElement.getParent();
        if (statement.getParent() instanceof PsiCodeBlock) {
          myDeleteSelf = true;
        }
      }

      myEditor = editor;
      myParentClass = parentClass;
    }

    public void run() {
      try {
        InitializationPlace initializerPlace = mySettings.getInitializerPlace();
        final PsiLocalVariable localVariable = mySettings.getLocalVariable();
        final boolean deleteLocalVariable = mySettings.isDeleteLocalVariable();
        @Nullable PsiExpression initializer = null;
        if (localVariable != null) {
          initializer = localVariable.getInitializer();
        }
        else if (!(mySelectedExpr instanceof PsiReferenceExpression && ((PsiReferenceExpression)mySelectedExpr).resolve() == null)){
          initializer = mySelectedExpr;
        }

        final SmartTypePointer type = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(myType);
        initializer = IntroduceVariableBase.simplifyVariableInitializer(initializer, myType);

        final PsiMethod enclosingConstructor = getEnclosingConstructor(myParentClass, myAnchorElement);
        PsiClass destClass = mySettings.getDestinationClass() == null ? myParentClass : mySettings.getDestinationClass();

        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, destClass.getContainingFile())) return;

        ChangeContextUtil.encodeContextInfo(destClass, true);

        myField = mySettings.isIntroduceEnumConstant() ? EnumConstantsUtil.createEnumConstant(destClass, myFieldName, initializer) :
                  createField(myFieldName, type.getType(), initializer,
                              initializerPlace == InitializationPlace.IN_FIELD_DECLARATION && initializer != null,
                              myParentClass);

        setModifiers(myField, mySettings);
        PsiElement finalAnchorElement = null;
        if (destClass == myParentClass) {
          for (finalAnchorElement = myAnchorElement;
               finalAnchorElement != null && finalAnchorElement.getParent() != destClass;
               finalAnchorElement = finalAnchorElement.getParent()) {
          }
        }
        PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? (PsiMember)finalAnchorElement : null;

        if (anchorMember instanceof PsiEnumConstant && destClass == anchorMember.getContainingClass() &&
            initializer != null && PsiTreeUtil.isAncestor(((PsiEnumConstant)anchorMember).getArgumentList(), initializer, false)) {
          final String initialName = "Constants";
          String constantsClassName = initialName;

          PsiClass innerClass = destClass.findInnerClassByName(constantsClassName, true);
          if (innerClass == null || !isConstantsClass(innerClass)) {
            int i = 1;
            while (destClass.findInnerClassByName(constantsClassName, true) != null) {
              constantsClassName = initialName + i++;
            }

            PsiClass psiClass = JavaPsiFacade.getElementFactory(myProject).createClass(constantsClassName);
            PsiUtil.setModifierProperty(psiClass, PsiModifier.PRIVATE, true);
            PsiUtil.setModifierProperty(psiClass, PsiModifier.STATIC, true);
            destClass = (PsiClass)destClass.add(psiClass);
          } else {
            destClass = innerClass;
          }
          anchorMember = null;
        }
        myField = appendField(initializer, initializerPlace, destClass, myParentClass, myField, anchorMember);
        if (!mySettings.isIntroduceEnumConstant()) {
          VisibilityUtil.fixVisibility(myOccurrences, myField, mySettings.getFieldVisibility());
        }
        PsiStatement assignStatement = null;
        PsiElement anchorElementHere = null;
        if (initializerPlace == InitializationPlace.IN_CURRENT_METHOD && initializer != null ||
            initializerPlace == InitializationPlace.IN_CONSTRUCTOR && enclosingConstructor != null && initializer != null) {
          if (myReplaceAll) {
            if (enclosingConstructor != null) {
              final PsiElement anchorInConstructor = RefactoringUtil.getAnchorElementForMultipleExpressions(mySettings.myOccurrences,
                                                                                                            enclosingConstructor);
              anchorElementHere = anchorInConstructor != null ? anchorInConstructor : myAnchorStatementIfAll;
            }
            else {
              anchorElementHere = myAnchorStatementIfAll;
            }
          }
          else {
            anchorElementHere = myAnchorElementIfOne;
          }
          assignStatement = createAssignment(myField, initializer, anchorElementHere, myParentClass);
          if (anchorElementHere != null && !RefactoringUtil.isLoopOrIf(anchorElementHere.getParent())) {
            anchorElementHere.getParent().addBefore(assignStatement, getNormalizedAnchor(anchorElementHere));
          }
        }
        if (initializerPlace == InitializationPlace.IN_CONSTRUCTOR && initializer != null) {
          addInitializationToConstructors(initializer, myField, enclosingConstructor, myParentClass);
        }
        if (initializerPlace == InitializationPlace.IN_SETUP_METHOD && initializer != null) {
          addInitializationToSetUp(initializer, myField, myOccurrences, myReplaceAll, myParentClass);
        }
        if (mySelectedExpr.getParent() instanceof PsiParenthesizedExpression) {
          mySelectedExpr = (PsiExpression)mySelectedExpr.getParent();
        }
        if (myOutOfCodeBlockExtraction != null) {
          final int endOffset = mySelectedExpr.getUserData(ElementToWorkOn.TEXT_RANGE).getEndOffset();
          PsiElement endElement = myElement.getContainingFile().findElementAt(endOffset);
          while (true) {
            final PsiElement parent = endElement.getParent();
            if (parent instanceof PsiClass) break;
            endElement = parent;
          }
          PsiElement last = PsiTreeUtil.skipWhitespacesBackward(endElement);
          if (last.getTextRange().getStartOffset() < myElement.getTextRange().getStartOffset()) {
            last = myElement;
          }
          myElement.getParent().deleteChildRange(myElement, last);
        }
        else if (myDeleteSelf) {
          myElement.getParent().delete();
        }

        if (myReplaceAll) {
          List<PsiElement> array = new ArrayList<>();
          for (PsiExpression occurrence : myOccurrences) {
            if (occurrence instanceof PsiExpression) {
              occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
            }
            if (myDeleteSelf && occurrence.equals(mySelectedExpr)) continue;
            final PsiElement replaced = RefactoringUtil.replaceOccurenceWithFieldRef(occurrence, myField, destClass);
            if (replaced != null) {
              array.add(replaced);
            }
          }

          if (myEditor != null) {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              PsiElement[] exprsToHighlight = PsiUtilCore.toPsiElementArray(array);
              HighlightManager highlightManager = HighlightManager.getInstance(myProject);
              highlightManager.addOccurrenceHighlights(myEditor, exprsToHighlight, highlightAttributes(), true, null);
              WindowManager
                .getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
            }
          }
        }
        else {
          if (!myDeleteSelf) {
            mySelectedExpr = RefactoringUtil.outermostParenthesizedExpression(mySelectedExpr);
            RefactoringUtil.replaceOccurenceWithFieldRef(mySelectedExpr, myField, destClass);
          }
        }

        if (anchorElementHere != null && RefactoringUtil.isLoopOrIf(anchorElementHere.getParent())) {
          RefactoringUtil.putStatementInLoopBody(assignStatement, anchorElementHere.getParent(), anchorElementHere);
        }


        if (localVariable != null) {
          if (deleteLocalVariable) {
            localVariable.normalizeDeclaration();
            localVariable.getParent().delete();
          }
        }

        ChangeContextUtil.decodeContextInfo(destClass, destClass, null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    static boolean isConstantsClass(PsiClass innerClass) {
      if (innerClass.getMethods().length != 0) return false;
      for (PsiField field : innerClass.getFields()) {
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return false;
      }
      return true;
    }

    static PsiField appendField(final PsiExpression initializer,
                                InitializationPlace initializerPlace, final PsiClass destClass,
                                final PsiClass parentClass,
                                final PsiField psiField,
                                final PsiMember anchorMember) {
      return appendField(destClass, psiField, anchorMember, initializerPlace == InitializationPlace.IN_FIELD_DECLARATION
                                                            ? checkForwardRefs(initializer, parentClass) : null);
    }

    public static PsiField appendField(final PsiClass destClass,
                                       final PsiField psiField,
                                       final PsiElement anchorMember,
                                       final PsiField forwardReference) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(anchorMember, PsiClass.class);

      if (anchorMember instanceof PsiField &&
          anchorMember.getParent() == parentClass &&
          destClass == parentClass &&
          ((PsiField)anchorMember).hasModifierProperty(PsiModifier.STATIC) == psiField.hasModifierProperty(PsiModifier.STATIC)) {
        return (PsiField)destClass.addBefore(psiField, anchorMember);
      }
      else if (anchorMember instanceof PsiClassInitializer &&
               anchorMember.getParent() == parentClass &&
               destClass == parentClass) {
        PsiField field = (PsiField)destClass.addBefore(psiField, anchorMember);
        destClass.addBefore(CodeEditUtil.createLineFeed(field.getManager()), anchorMember);
        return field;
      }
      else {
        if (forwardReference != null &&forwardReference.getParent() == destClass) {
          return (PsiField)destClass.addAfter(psiField, forwardReference);
        }
        return (PsiField)destClass.add(psiField);
      }
    }

    @Nullable
    private static PsiField checkForwardRefs(@Nullable final PsiExpression initializer, final PsiClass parentClass) {
      if (initializer == null) return null;
      final PsiField[] refConstantFields = new PsiField[1];
      initializer.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolve = expression.resolve();
          if (resolve instanceof PsiField &&
              PsiTreeUtil.isAncestor(parentClass, resolve, false) && ((PsiField)resolve).hasInitializer() &&
              !PsiTreeUtil.isAncestor(initializer, resolve, false)) {
            if (refConstantFields[0] == null || refConstantFields[0].getTextOffset() < resolve.getTextOffset()) {
              refConstantFields[0] = (PsiField)resolve;
            }
          }
        }
      });
      return refConstantFields[0];
    }

    public PsiField getField() {
      return myField;
    }
  }
}

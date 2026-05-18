// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.SmartTypePointerManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.rename.RenameJavaMemberProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.InitializationPlace;
import static com.intellij.refactoring.introduceField.JavaIntroduceFieldService.ToFieldContext;

public abstract class BaseExpressionToFieldHandler extends IntroduceHandlerBase {
  private static final Logger LOG = Logger.getInstance(BaseExpressionToFieldHandler.class);

  private final boolean myIsConstant;
  private @Nullable PsiClass myParentClass;
  final @NotNull FieldHelper myHelper;

  BaseExpressionToFieldHandler(@NotNull FieldHelper helper) {
    myIsConstant = helper.isConstant();
    myHelper = helper;
  }

  @Override
  protected boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
    ToFieldContext context = FieldExtractor.getContext(myHelper, selectedExpr, false);
    if (context instanceof ToFieldContext.Error(@NlsContexts.DialogMessage String message)) {
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), getHelpID());
      return false;
    }
    if (!(context instanceof ToFieldContext.ExpressionContext expressionContext)) {
      return false;
    }
    PsiFile file = expressionContext.psiFile();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return true;
    PsiType tempType = expressionContext.tempType();
    final List<PsiClass> classes = expressionContext.proposedClasses();
    myParentClass = classes.getFirst();
    final AbstractInplaceIntroducer activeIntroducer = AbstractInplaceIntroducer.getActiveIntroducer(editor);
    final boolean shouldSuggestDialog = activeIntroducer instanceof InplaceIntroduceConstantPopup &&
                                        activeIntroducer.startsOnTheSameElement(selectedExpr, null);
    if (classes.size() == 1 ||
        editor == null ||
        IntentionPreviewUtils.isPreviewElement(myParentClass) ||
        ApplicationManager.getApplication().isUnitTestMode() ||
        shouldSuggestDialog) {
      return !convertExpressionToField(selectedExpr, editor, project, tempType, myParentClass);
    }
    else if (!classes.isEmpty()) {
      final PsiClass selection = AnonymousTargetClassPreselectionUtil.getPreselection(classes, myParentClass);
      final String title = myIsConstant
                           ? JavaRefactoringBundle.message("popup.title.choose.class.to.introduce.constant")
                           : JavaRefactoringBundle.message("popup.title.choose.class.to.introduce.field");
      new PsiTargetNavigator<>(classes.toArray(PsiClass.EMPTY_ARRAY)).selection(selection)
        .createPopup(project, title, new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiClass aClass) {
            AnonymousTargetClassPreselectionUtil.rememberSelection(aClass, myParentClass);
            myParentClass = aClass;
            convertExpressionToField(selectedExpr, editor, project, tempType, myParentClass);
            return false;
          }
        }).showInBestPositionFor(editor);
    }
    return true;
  }

  private boolean convertExpressionToField(PsiExpression selectedExpr,
                                           @Nullable Editor editor,
                                           Project project,
                                           PsiType tempType,
                                           @NotNull PsiClass parentClass) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, parentClass)) return true;

    final OccurrenceManager occurrenceManager = createOccurrenceManager(selectedExpr, parentClass);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();

    List<RangeHighlighter> highlighters = null;
    if (editor != null) {
      highlighters = RefactoringUtil.highlightAllOccurrences(project, occurrences, editor);
    }

    PsiElement tempAnchorElement = CommonJavaRefactoringUtil.getParentExpressionAnchorElement(selectedExpr);

    final Settings settings =
      showRefactoringDialog(project, editor, parentClass, selectedExpr, tempType,
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
      new ConvertToFieldRunnable(settings.getSelectedExpr(), settings, type, settings.getOccurrences(),
                                 anchorStatementIfAll, tempAnchorElement, editor, parentClass);

    if (IntentionPreviewUtils.isPreviewElement(parentClass)) {
      runnable.run();
    }
    else {
      WriteCommandAction.writeCommandAction(project).withName(getRefactoringName()).run(runnable::run);
    }
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
        PsiAnnotation annotation = JavaPsiFacade.getElementFactory(field.getProject())
          .createAnnotationFromText("@" + AnnotationUtil.NON_NLS, field);
        final PsiModifierList modifierList = field.getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.addAfter(annotation, null);
      }
    }
  }

  public static PsiElement getPhysicalElement(PsiExpression selectedExpr) {
    PsiElement element = selectedExpr.getUserData(ElementToWorkOn.PARENT);
    if (element == null) element = selectedExpr;
    return element;
  }

  protected OccurrenceManager createOccurrenceManager(PsiExpression selectedExpr, PsiClass parentClass){
    return myHelper.createOccurrenceManager(selectedExpr, parentClass);
  }

  protected final @Nullable PsiClass getParentClass() {
    return myParentClass;
  }

  protected @NlsContexts.DialogMessage @Nullable String checkClass(@NotNull PsiClass parentClass,
                                                                   @NotNull PsiExpression selectedExpr) {
    return myHelper.checkClass(parentClass, selectedExpr);
  }

  private static PsiElement getNormalizedAnchor(PsiElement anchorElement) {
    PsiElement child = anchorElement;
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (CommonJavaRefactoringUtil.isExpressionAnchorElement(prev)) break;
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

  private static void addInitializationToSetUp(PsiExpression initializer,
                                               PsiField field,
                                               PsiExpression[] occurrences,
                                               boolean replaceAll,
                                               PsiClass parentClass) throws IncorrectOperationException {
    final PsiMethod setupMethod = TestFrameworks.getInstance().findOrCreateSetUpMethod(parentClass);

    assert setupMethod != null;

    PsiElement anchor = null;
    if (PsiTreeUtil.isAncestor(setupMethod, initializer, true)) {
      anchor = replaceAll
               ? CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, setupMethod)
               : PsiTreeUtil.getParentOfType(initializer, PsiStatement.class);
    }

    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)JavaPsiFacade.getElementFactory(parentClass.getProject())
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
                                                      PsiMethod enclosingConstructor,
                                                      PsiClass parentClass) {
    try {
      PsiClass aClass = field.getContainingClass();
      assert aClass != null;
      PsiMethod[] constructors = aClass.getConstructors();

      boolean added = false;
      for (PsiMethod constructor : constructors) {
        if (constructor == enclosingConstructor) continue;
        PsiCodeBlock body = constructor.getBody();
        if (body == null) continue;
        PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          PsiStatement first = statements[0];
          if (first instanceof PsiExpressionStatement statement) {
            PsiExpression expression = statement.getExpression();
            if (expression instanceof PsiMethodCallExpression call) {
              @NonNls String text = call.getMethodExpression().getText();
              if ("this".equals(text)) {
                continue;
              }
            }
          }
        }
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild(), parentClass);
        if (assignment == null) return;
        assignment = (PsiStatement)body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringChangeUtil.createThisExpression(field.getManager(), null));
        added = true;
      }
      if (!added && enclosingConstructor == null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
        PsiMethod constructor = (PsiMethod)aClass.add(factory.createConstructor());
        final PsiCodeBlock body = constructor.getBody();
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild(), parentClass);
        assignment = (PsiStatement)body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringChangeUtil.createThisExpression(field.getManager(), null));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static PsiField createField(String fieldName, PsiType type, boolean includeInitializer, PsiClass parentClass) {
    @NonNls StringBuilder pattern = new StringBuilder("private int ").append(fieldName);
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(parentClass.getProject());
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);
      field.getTypeElement().replace(factory.createTypeElement(type));
      field = (PsiField)CodeStyleManager.getInstance(parentClass.getProject()).reformat(field);
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private static PsiStatement createAssignment(PsiField field, PsiExpression initializerExpr, PsiElement context, PsiClass parentClass) {
    try {
      @NonNls String pattern = "x=0;";
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(parentClass.getProject());
      PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(pattern, null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(parentClass.getProject()).reformat(statement);

      PsiAssignmentExpression expr = (PsiAssignmentExpression)statement.getExpression();
      final PsiExpression rExpression = expr.getRExpression();
      LOG.assertTrue(rExpression != null);
      rExpression.replace(initializerExpr);
      final PsiReferenceExpression fieldReference = RenameJavaMemberProcessor.createMemberReference(field, context);
      expr.getLExpression().replace(fieldReference);

      return statement;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  protected boolean accept(ElementToWorkOn elementToWorkOn) {
    return myHelper.accept(elementToWorkOn);
  }

  protected ElementToWorkOn.ElementsProcessor<ElementToWorkOn> getElementProcessor(Project project, Editor editor) {
    return new ElementToWorkOn.ElementsProcessor<>() {
      @Override
      public boolean accept(ElementToWorkOn el) {
        return BaseExpressionToFieldHandler.this.accept(el);
      }

      @Override
      public void pass(ElementToWorkOn elementToWorkOn) {
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

  @NlsContexts.DialogTitle
  @NotNull
  public String getRefactoringName() {
    return myHelper.getRefactoringName();
  }

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

    public @Nullable PsiClass getDestinationClass() {
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
                    InitializationPlace initializerPlace,
                    @PsiModifier.ModifierConstant String visibility,
                    PsiLocalVariable localVariableToRemove,
                    PsiType forcedType,
                    boolean deleteLocalVariable,
                    TargetDestination targetDestination,
                    boolean annotateAsNonNls,
                    boolean introduceEnumConstant) {

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
                    InitializationPlace initializerPlace,
                    @PsiModifier.ModifierConstant String visibility,
                    PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable,
                    PsiClass targetClass,
                    boolean annotateAsNonNls,
                    boolean introduceEnumConstant) {

      this(fieldName, selectedExpression, occurrences, replaceAll, declareStatic, declareFinal, initializerPlace, visibility,
           localVariableToRemove, forcedType, deleteLocalVariable, new TargetDestination(targetClass), annotateAsNonNls,
           introduceEnumConstant);
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

    public @Nullable PsiClass getTargetClass() {
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
        psiDirectory =
          directories.length > 1 ? DirectoryChooserUtil.chooseDirectory(directories, null, myProject, new HashMap<>()) : directories[0];
      }
      else {
        psiDirectory =
          PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myParentClass.getContainingFile().getContainingDirectory(),
                                                      false);
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
    private final PsiElement myAnchorStatementIfAll;
    private final PsiElement myAnchorElementIfOne;
    private final Boolean myOutOfCodeBlockExtraction;
    private final PsiElement myElement;
    private boolean myDeleteSelf;
    private final @Nullable Editor myEditor;
    private final PsiClass myParentClass;

    private PsiField myField;

    public ConvertToFieldRunnable(PsiExpression selectedExpr,
                                  Settings settings,
                                  PsiType type,
                                  PsiExpression[] occurrences,
                                  PsiElement anchorStatementIfAll,
                                  PsiElement anchorElementIfOne,
                                  @Nullable Editor editor,
                                  PsiClass parentClass) {
      mySelectedExpr = selectedExpr;
      mySettings = settings;
      myAnchorElement = settings.isReplaceAll() ? anchorStatementIfAll : anchorElementIfOne;
      myProject = selectedExpr.getProject();
      myFieldName = settings.getFieldName();
      myType = type;
      myOccurrences = occurrences;
      myReplaceAll = settings.isReplaceAll();
      myAnchorStatementIfAll = anchorStatementIfAll;
      myAnchorElementIfOne = anchorElementIfOne;
      myOutOfCodeBlockExtraction = selectedExpr.getUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK);
      myDeleteSelf = myOutOfCodeBlockExtraction != null;
      myElement = getPhysicalElement(selectedExpr);
      if (myElement.getParent() instanceof PsiExpressionStatement statement &&
          getNormalizedAnchor(myAnchorElement).equals(myAnchorElement) &&
          (selectedExpr.isPhysical() || selectedExpr.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) != null) &&
          statement.getParent() instanceof PsiCodeBlock) {
        myDeleteSelf = true;
      }

      myEditor = editor;
      myParentClass = parentClass;
    }

    @Override
    public void run() {
      try {
        InitializationPlace initializerPlace = mySettings.getInitializerPlace();
        final PsiLocalVariable localVariable = mySettings.getLocalVariable();
        final boolean deleteLocalVariable = mySettings.isDeleteLocalVariable();
        @Nullable PsiExpression initializer = null;
        if (localVariable != null) {
          initializer = localVariable.getInitializer();
        }
        else if (!(mySelectedExpr instanceof PsiReferenceExpression ref && ref.resolve() == null)) {
          initializer = mySelectedExpr;
        }

        final SmartTypePointer type = SmartTypePointerManager.getInstance(myProject).createSmartTypePointer(myType);
        initializer = IntroduceVariableBase.simplifyVariableInitializer(initializer, myType,
                                                                        initializerPlace == InitializationPlace.IN_FIELD_DECLARATION);

        final PsiMethod enclosingConstructor = getEnclosingConstructor(myParentClass, myAnchorElement);
        PsiClass destClass = mySettings.getDestinationClass() == null ? myParentClass : mySettings.getDestinationClass();

        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, destClass.getContainingFile())) return;

        ChangeContextUtil.encodeContextInfo(destClass, true);
        boolean includeInitializer = initializerPlace == InitializationPlace.IN_FIELD_DECLARATION && initializer != null;
        myField = mySettings.isIntroduceEnumConstant()
                  ? EnumConstantsUtil.createEnumConstant(destClass, myFieldName, initializer)
                  : createField(myFieldName, type.getType(), includeInitializer, myParentClass);

        setModifiers(myField, mySettings);
        PsiElement anchor = null;
        if (destClass == myParentClass) {
          for (anchor = myAnchorElement;
               anchor != null && anchor.getParent() != destClass;
               anchor = anchor.getParent()) {
          }
        }
        PsiMember anchorMember = anchor instanceof PsiMember member ? member : null;

        if (anchorMember instanceof PsiEnumConstant constant && destClass == anchorMember.getContainingClass() &&
            initializer != null && PsiTreeUtil.isAncestor(constant.getArgumentList(), initializer, false)) {
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
          }
          else {
            destClass = innerClass;
          }
          anchorMember = null;
        }
        myField = appendField(initializer, initializerPlace, destClass, myParentClass, myField, anchorMember);
        if (includeInitializer) {
          // It's important that we append field before adding the initializer to make sure that the replacement takes into account the
          // context of the file
          LOG.assertTrue(myField.getInitializer() != null);
          myField.getInitializer().replace(initializer);
        }
        if (!mySettings.isIntroduceEnumConstant()) {
          VisibilityUtil.fixVisibility(myOccurrences, myField, mySettings.getFieldVisibility());
        }
        PsiStatement assignStatement = null;
        PsiElement anchorElementHere = null;
        if (initializerPlace == InitializationPlace.IN_CURRENT_METHOD && initializer != null ||
            initializerPlace == InitializationPlace.IN_CONSTRUCTOR && enclosingConstructor != null && initializer != null) {
          if (myReplaceAll) {
            if (enclosingConstructor != null) {
              final PsiElement anchorInConstructor =
                CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(mySettings.myOccurrences,
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
          if (assignStatement != null &&
              anchorElementHere != null &&
              !CommonJavaRefactoringUtil.isLoopOrIf(anchorElementHere.getParent())) {
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
            occurrence = CommonJavaRefactoringUtil.outermostParenthesizedExpression(occurrence);
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
              highlightManager.addOccurrenceHighlights(myEditor, exprsToHighlight,
                                                       EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
              WindowManager
                .getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
            }
          }
        }
        else {
          if (!myDeleteSelf) {
            mySelectedExpr = CommonJavaRefactoringUtil.outermostParenthesizedExpression(mySelectedExpr);
            RefactoringUtil.replaceOccurenceWithFieldRef(mySelectedExpr, myField, destClass);
          }
        }

        if (anchorElementHere != null && CommonJavaRefactoringUtil.isLoopOrIf(anchorElementHere.getParent())) {
          CommonJavaRefactoringUtil.putStatementInLoopBody(assignStatement, anchorElementHere.getParent(), anchorElementHere);
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

    static PsiField appendField(PsiExpression initializer,
                                InitializationPlace initializerPlace,
                                PsiClass destClass,
                                PsiClass parentClass,
                                PsiField psiField,
                                PsiMember anchorMember) {
      PsiField reference = initializerPlace == InitializationPlace.IN_FIELD_DECLARATION ? checkForwardRefs(initializer, parentClass) : null;
      return CommonJavaRefactoringUtil.appendField(destClass, psiField, anchorMember, reference);
    }

    static @Nullable PsiField checkForwardRefs(@Nullable PsiExpression initializer, PsiClass parentClass) {
      if (initializer == null) return null;
      final PsiField[] refConstantFields = new PsiField[1];
      initializer.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          final PsiElement resolve = expression.resolve();
          if (resolve instanceof PsiField field &&
              PsiTreeUtil.isAncestor(parentClass, resolve, false) && field.hasInitializer() &&
              !PsiTreeUtil.isAncestor(initializer, resolve, false)) {
            if (refConstantFields[0] == null || refConstantFields[0].getTextOffset() < resolve.getTextOffset()) {
              refConstantFields[0] = field;
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

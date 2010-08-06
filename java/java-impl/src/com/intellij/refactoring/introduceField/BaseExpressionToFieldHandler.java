/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 29.05.2002
 * Time: 13:05:34
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.TestUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.EnumConstantsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class BaseExpressionToFieldHandler extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler");

  public enum InitializationPlace {
    IN_CURRENT_METHOD,
    IN_FIELD_DECLARATION,
    IN_CONSTRUCTOR,
    IN_SETUP_METHOD
  }

  private PsiClass myParentClass;

  protected boolean invokeImpl(final Project project, @NotNull final PsiExpression selectedExpr, final Editor editor) {
    final PsiElement element = getPhysicalElement(selectedExpr);

    final PsiFile file = element.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + selectedExpr);
    }

    myParentClass = getParentClass(selectedExpr);
    if (myParentClass == null) {
      if (JspPsiUtil.isInJspFile(file)) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("error.not.supported.for.jsp", getRefactoringName()),
                                            getRefactoringName(), getHelpID());
        return false;
      }
      else {
        LOG.assertTrue(false);
        return false;
      }
    }

    if (!validClass(myParentClass, editor)) {
      return false;
    }

    PsiType tempType = getTypeByExpression(selectedExpr);
    if (tempType == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), getHelpID());
      return false;
    }

    if (PsiType.VOID.equals(tempType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), getHelpID());
      return false;
    }


    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    final PsiClass parentClass = myParentClass;
    final OccurenceManager occurenceManager = createOccurenceManager(selectedExpr, parentClass);
    final PsiExpression[] occurrences = occurenceManager.getOccurences();
    final PsiElement anchorStatementIfAll = occurenceManager.getAnchorStatementForAll();

    List<RangeHighlighter> highlighters = null;
    if (editor != null) {
      highlighters = RefactoringUtil.highlightAllOccurences(project, occurrences, editor);
    }

    PsiElement tempAnchorElement = RefactoringUtil.getParentExpressionAnchorElement(selectedExpr);

    final Settings settings =
      showRefactoringDialog(project, editor, myParentClass, selectedExpr, tempType,
                            occurrences, tempAnchorElement, anchorStatementIfAll);

    if (settings == null) return false;

    if (settings.getForcedType() != null) {
      tempType = settings.getForcedType();
    }
    final PsiType type = tempType;

    final String fieldName = settings.getFieldName();
    final PsiElement anchorElementIfOne = tempAnchorElement;
    final boolean replaceAll = settings.isReplaceAll();
    if (replaceAll) {
      tempAnchorElement = anchorStatementIfAll;
    }
    final PsiElement anchorElement = tempAnchorElement;


    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      for (RangeHighlighter highlighter : highlighters) {
        highlightManager.removeSegmentHighlighter(editor, highlighter);
      }
    }

    PsiElement anchor = getNormalizedAnchor(anchorElement);

    final Boolean outOfCodeBlockExtraction = selectedExpr.getUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK);
    boolean tempDeleteSelf = outOfCodeBlockExtraction != null;
    if (element.getParent() instanceof PsiExpressionStatement && anchor.equals(anchorElement)) {
      PsiStatement statement = (PsiStatement)element.getParent();
      if (statement.getParent() instanceof PsiCodeBlock) {
        tempDeleteSelf = true;
      }
    }
    final boolean deleteSelf = tempDeleteSelf;

    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiExpression expr = selectedExpr;
          InitializationPlace initializerPlace = settings.getInitializerPlace();
          final PsiLocalVariable localVariable = settings.getLocalVariable();
          final boolean deleteLocalVariable = settings.isDeleteLocalVariable();
          @Nullable PsiExpression initializer;
          if (localVariable != null) {
            initializer = localVariable.getInitializer();
          }
          else {
            initializer = expr;
          }

          final PsiMethod enclosingConstructor = getEnclosingConstructor(myParentClass, anchorElement);
          final PsiClass destClass = settings.getDestinationClass() == null ? myParentClass : settings.getDestinationClass();

          if (!CommonRefactoringUtil.checkReadOnlyStatus(project, destClass.getContainingFile())) return;

          if (initializer != null) {
            ChangeContextUtil.encodeContextInfo(initializer, true);
          }
          PsiField field = settings.isIntroduceEnumConstant() ? EnumConstantsUtil.createEnumConstant(destClass, fieldName, initializer) : createField(fieldName, type, initializer, initializerPlace == InitializationPlace.IN_FIELD_DECLARATION && initializer != null);

          PsiElement finalAnchorElement = null;
          if (destClass == myParentClass) {
            for (finalAnchorElement = anchorElement;
                 finalAnchorElement != null && finalAnchorElement.getParent() != destClass;
                 finalAnchorElement = finalAnchorElement.getParent()) {

            }
          }
          PsiMember anchorMember = finalAnchorElement instanceof PsiMember ? (PsiMember)finalAnchorElement : null;
          setModifiers(field, settings, settings.isDeclareStatic());
          if ((anchorMember instanceof PsiField) &&
              anchorMember.hasModifierProperty(PsiModifier.STATIC) == field.hasModifierProperty(PsiModifier.STATIC)) {
            field = (PsiField)destClass.addBefore(field, anchorMember);
          }
          else if (anchorMember instanceof PsiClassInitializer) {
            field = (PsiField)destClass.addBefore(field, anchorMember);
            destClass.addBefore(CodeEditUtil.createLineFeed(field.getManager()), anchorMember);
          }
          else {
            field = (PsiField)destClass.add(field);
          }
          if (!settings.isIntroduceEnumConstant()) {
            VisibilityUtil.fixVisibility(occurrences, field, settings.getFieldVisibility());
          }
          PsiStatement assignStatement = null;
          PsiElement anchorElementHere = null;
          if (initializerPlace == InitializationPlace.IN_CURRENT_METHOD && initializer != null ||
              initializerPlace == InitializationPlace.IN_CONSTRUCTOR && enclosingConstructor != null && initializer != null) {
            if (replaceAll) {
              if (enclosingConstructor != null) {
                final PsiElement anchorInConstructor = occurenceManager.getAnchorStatementForAllInScope(enclosingConstructor);
                anchorElementHere = anchorInConstructor != null ? anchorInConstructor : anchorStatementIfAll;
              }
              else {
                anchorElementHere = anchorStatementIfAll;
              }
            }
            else {
              anchorElementHere = anchorElementIfOne;
            }
            assignStatement = createAssignment(field, initializer, anchorElementHere);
            if (!IntroduceVariableBase.isLoopOrIf(anchorElementHere.getParent())) {
              anchorElementHere.getParent().addBefore(assignStatement, getNormalizedAnchor(anchorElementHere));
            }
          }
          if (initializerPlace == InitializationPlace.IN_CONSTRUCTOR && initializer != null) {
            addInitializationToConstructors(initializer, field, enclosingConstructor);
          }
          if (initializerPlace == InitializationPlace.IN_SETUP_METHOD && initializer != null) {
            addInitializationToSetUp(initializer, field, occurenceManager, replaceAll);
          }
          if (expr.getParent() instanceof PsiParenthesizedExpression) {
            expr = (PsiExpression)expr.getParent();
          }
          if (outOfCodeBlockExtraction != null) {
            final int endOffset = selectedExpr.getUserData(ElementToWorkOn.TEXT_RANGE).getEndOffset();
            PsiElement endElement = element.getContainingFile().findElementAt(endOffset);
            while (true) {
              final PsiElement parent = endElement.getParent();
              if (parent instanceof PsiClass) break;
              endElement = parent;
            }
            element.getParent().deleteChildRange(element, PsiTreeUtil.skipSiblingsBackward(endElement, PsiWhiteSpace.class));
          } else if (deleteSelf) {
            element.getParent().delete();
          }

          if (replaceAll) {
            List<PsiElement> array = new ArrayList<PsiElement>();
            for (PsiExpression occurrence : occurrences) {
              if (occurrence instanceof PsiExpression) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
              }
              if (deleteSelf && occurrence.equals(expr)) continue;
              final PsiElement replaced = RefactoringUtil.replaceOccurenceWithFieldRef(occurrence, field, destClass);
              if (replaced != null) {
                array.add(replaced);
              }
            }

            if (editor != null) {
              if (!ApplicationManager.getApplication().isUnitTestMode()) {
                PsiElement[] exprsToHighlight = array.toArray(new PsiElement[array.size()]);
                HighlightManager highlightManager = HighlightManager.getInstance(project);
                highlightManager.addOccurrenceHighlights(editor, exprsToHighlight, highlightAttributes(), true, null);
                WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
              }
            }
          }
          else {
            if (!deleteSelf) {
              expr = RefactoringUtil.outermostParenthesizedExpression(expr);
              RefactoringUtil.replaceOccurenceWithFieldRef(expr, field, destClass);
            }
          }

          if (anchorElementHere != null && IntroduceVariableBase.isLoopOrIf(anchorElementHere.getParent())) {
            IntroduceVariableBase.putStatementInLoopBody(assignStatement, anchorElementHere.getParent(), anchorElementHere);
          }


          if (localVariable != null) {
            if (deleteLocalVariable) {
              localVariable.normalizeDeclaration();
              localVariable.getParent().delete();
            }
          }

          if (initializer != null) {
            ChangeContextUtil.clearContextInfo(initializer);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      },
      getRefactoringName(), null
      );

    return true;
  }

  public static void setModifiers(PsiField field, Settings settings, final boolean declareStatic) {
    if (!settings.isIntroduceEnumConstant()) {
      if (declareStatic) {
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

  private static PsiElement getPhysicalElement(final PsiExpression selectedExpr) {
    PsiElement element = selectedExpr.getUserData(ElementToWorkOn.PARENT);
    if (element == null) element = selectedExpr;
    return element;
  }

  private static TextAttributes highlightAttributes() {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(
                EditorColors.SEARCH_RESULT_ATTRIBUTES
              );
  }

  protected abstract OccurenceManager createOccurenceManager(PsiExpression selectedExpr, PsiClass parentClass);

  protected final PsiClass getParentClass() {
    return myParentClass;
  }

  protected abstract boolean validClass(PsiClass parentClass, Editor editor);

  private static PsiElement getNormalizedAnchor(PsiElement anchorElement) {
    PsiElement child = anchorElement;
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (RefactoringUtil.isExpressionAnchorElement(prev)) break;
      if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.LBRACE) break;
      child = prev;
    }

    child = PsiTreeUtil.skipSiblingsForward(child, PsiWhiteSpace.class, PsiComment.class);
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
                                                    PsiType type, PsiExpression[] occurences, PsiElement anchorElement,
                                                    PsiElement anchorElementIfAll);


  private static PsiType getTypeByExpression(PsiExpression expr) {
    return RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
  }

  public PsiClass getParentClass(@NotNull PsiExpression initializerExpression) {
    PsiElement element = initializerExpression.getUserData(ElementToWorkOn.PARENT);
    if (element == null) element = initializerExpression.getParent();
    PsiElement parent = element;
    while (parent != null) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
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
      if (PsiTreeUtil.isAncestor(constructor, element, false)) return constructor;
    }
    return null;
  }

  private void addInitializationToSetUp(final PsiExpression initializer,
                                        final PsiField field,
                                        final OccurenceManager occurenceManager, final boolean replaceAll) throws IncorrectOperationException {
    final PsiMethod setupMethod = TestUtil.findOrCreateSetUpMethod(myParentClass);

    assert setupMethod != null;

    PsiElement anchor = null;
    if (PsiTreeUtil.isAncestor(setupMethod, initializer, true)) {
      anchor = replaceAll
               ? occurenceManager.getAnchorStatementForAllInScope(setupMethod)
               : PsiTreeUtil.getParentOfType(initializer, PsiStatement.class);
    }

    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)JavaPsiFacade.getInstance(myParentClass.getProject()).getElementFactory()
        .createStatementFromText(field.getName() + "= expr;", null);
    PsiAssignmentExpression expr = (PsiAssignmentExpression)expressionStatement.getExpression();
    final PsiExpression rExpression = expr.getRExpression();
    LOG.assertTrue(rExpression != null);
    rExpression.replace(initializer);

    final PsiCodeBlock body = setupMethod.getBody();
    assert body != null;
    body.addBefore(expressionStatement, anchor);
  }

  private void addInitializationToConstructors(PsiExpression initializerExpression, PsiField field, PsiMethod enclosingConstructor) {
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
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild());
        assignment = (PsiStatement) body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringUtil.createThisExpression(field.getManager(), null));
        added = true;
      }
      if (!added && enclosingConstructor == null) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
        PsiMethod constructor = (PsiMethod)aClass.add(factory.createConstructor());
        final PsiCodeBlock body = constructor.getBody();
        PsiStatement assignment = createAssignment(field, initializerExpression, body.getLastChild());
        assignment = (PsiStatement) body.add(assignment);
        ChangeContextUtil.decodeContextInfo(assignment, field.getContainingClass(),
                                            RefactoringUtil.createThisExpression(field.getManager(), null));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiField createField(String fieldName, PsiType type, PsiExpression initializerExpr, boolean includeInitializer) {
    @NonNls StringBuilder pattern = new StringBuilder();
    pattern.append("private int ");
    pattern.append(fieldName);
    if (includeInitializer) {
      pattern.append("=0");
    }
    pattern.append(";");
    PsiManager psiManager = myParentClass.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    try {
      PsiField field = factory.createFieldFromText(pattern.toString(), null);
      field = (PsiField)CodeStyleManager.getInstance(psiManager.getProject()).reformat(field);
      field.getTypeElement().replace(factory.createTypeElement(type));
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

  private PsiStatement createAssignment(PsiField field, PsiExpression initializerExpr, PsiElement context) {
    try {
      @NonNls String pattern = "x=0;";
      PsiManager psiManager = myParentClass.getManager();
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
  

  protected Pass<ElementToWorkOn> getElementProcessor(final Project project, final Editor editor) {
    return new Pass<ElementToWorkOn>() {
      @Override
      public void pass(final ElementToWorkOn elementToWorkOn) {
        if (elementToWorkOn == null) return;

        if (elementToWorkOn.getExpression() == null) {
          final PsiLocalVariable localVariable = elementToWorkOn.getLocalVariable();
          final boolean result = invokeImpl(project, localVariable, editor);
          if (result) {
            editor.getSelectionModel().removeSelection();
          }
        }
        else if (invokeImpl(project, elementToWorkOn.getExpression(), editor)) {
          editor.getSelectionModel().removeSelection();
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

    public Settings(String fieldName, boolean replaceAll,
                    boolean declareStatic, boolean declareFinal,
                    InitializationPlace initializerPlace, String visibility, PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable,
                    TargetDestination targetDestination,
                    final boolean annotateAsNonNls,
                    final boolean introduceEnumConstant) {

      myFieldName = fieldName;
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

    public Settings(String fieldName, boolean replaceAll,
                    boolean declareStatic, boolean declareFinal,
                    InitializationPlace initializerPlace, String visibility, PsiLocalVariable localVariableToRemove, PsiType forcedType,
                    boolean deleteLocalVariable,
                    PsiClass targetClass,
                    final boolean annotateAsNonNls,
                    final boolean introduceEnumConstant) {

      this(fieldName, replaceAll, declareStatic, declareFinal, initializerPlace, visibility, localVariableToRemove, forcedType, deleteLocalVariable, new TargetDestination(targetClass), annotateAsNonNls, introduceEnumConstant);
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
      PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
      final PsiDirectory psiDirectory;
      if (psiPackage != null) {
        final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.allScope(myProject));
        psiDirectory = directories.length > 1 ? DirectoryChooserUtil.chooseDirectory(directories, null, myProject, new HashMap<PsiDirectory, String>()) : directories[0];
      } else {
        psiDirectory = PackageUtil.findOrCreateDirectoryForPackage(myProject, packageName, myParentClass.getContainingFile().getContainingDirectory(), false);
      }
      final String shortName = StringUtil.getShortName(myQualifiedName);
      myTargetClass = psiDirectory != null ? JavaDirectoryService.getInstance().createClass(psiDirectory, shortName) : null;
      return myTargetClass;
    }
  }
}

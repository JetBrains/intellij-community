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
package com.intellij.refactoring.anonymousToInner;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AnonymousToInnerHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.anonymousToInner.AnonymousToInnerHandler");

  static final String REFACTORING_NAME = RefactoringBundle.message("anonymousToInner.refactoring.name");

  private Project myProject;

  private PsiManager myManager;

  private PsiAnonymousClass myAnonClass;
  private PsiClass myTargetClass;
  protected String myNewClassName;

  private VariableInfo[] myVariableInfos;
  protected boolean myMakeStatic;
  private final Set<PsiTypeParameter> myTypeParametersToCreate = new LinkedHashSet<>();

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiAnonymousClass) {
      invoke(project, CommonDataKeys.EDITOR.getData(dataContext), (PsiAnonymousClass)elements[0]);
    }
  }

  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    final int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiAnonymousClass anonymousClass = findAnonymousClass(file, offset);
    if (anonymousClass == null) {
      showErrorMessage(editor, RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.anonymous")));
      return;
    }
    final PsiElement parent = anonymousClass.getParent();
    if (parent instanceof PsiEnumConstant) {
      showErrorMessage(editor, RefactoringBundle.getCannotRefactorMessage("Enum constant can't be converted to inner class"));
      return;
    }
    invoke(project, editor, anonymousClass);
  }

  private void showErrorMessage(Editor editor, String message) {
    CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.ANONYMOUS_TO_INNER);
  }

  public void invoke(final Project project, Editor editor, final PsiAnonymousClass anonymousClass) {
    myProject = project;

    myManager = PsiManager.getInstance(myProject);
    myAnonClass = anonymousClass;

    PsiClassType baseRef = myAnonClass.getBaseClassType();

    if (baseRef.resolve() == null) {
      String message = RefactoringBundle.message("error.cannot.resolve", baseRef.getCanonicalText());
      showErrorMessage(editor, message);
      return;
    }
    PsiElement targetContainer = findTargetContainer(myAnonClass);
    if (FileTypeUtils.isInServerPageFile(targetContainer) && targetContainer instanceof PsiFile) {
      String message = RefactoringBundle.message("error.not.supported.for.jsp", REFACTORING_NAME);
      showErrorMessage(editor, message);
      return;
    }
    LOG.assertTrue(targetContainer instanceof PsiClass);
    myTargetClass = (PsiClass) targetContainer;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myTargetClass)) return;

    Map<PsiVariable,VariableInfo> variableInfoMap = new LinkedHashMap<>();
    collectUsedVariables(variableInfoMap, myAnonClass);
    final VariableInfo[] infos = variableInfoMap.values().toArray(new VariableInfo[variableInfoMap.values().size()]);
    myVariableInfos = infos;
    Arrays.sort(myVariableInfos, (o1, o2) -> {
      final PsiType type1 = o1.variable.getType();
      final PsiType type2 = o2.variable.getType();
      if (type1 instanceof PsiEllipsisType) {
        return 1;
      }
      if (type2 instanceof PsiEllipsisType) {
        return -1;
      }
      return ArrayUtil.find(infos, o1) > ArrayUtil.find(infos, o2) ? 1 : -1;
    });
    if (!showRefactoringDialog()) return;

    CommandProcessor.getInstance().executeCommand(
      myProject, () -> {
        final Runnable action = () -> {
          try {
            doRefactoring();
          } catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      },
      REFACTORING_NAME,
      null
    );

  }

  protected boolean showRefactoringDialog() {
    final boolean anInterface = myTargetClass.isInterface();
    final boolean needsThis = needsThis() || PsiUtil.isInnerClass(myTargetClass);
    final AnonymousToInnerDialog dialog = new AnonymousToInnerDialog(
      myProject,
      myAnonClass,
      myVariableInfos,
      needsThis || anInterface);
    if (!dialog.showAndGet()) {
      return false;
    }
    myNewClassName = dialog.getClassName();
    myVariableInfos = dialog.getVariableInfos();
    myMakeStatic = !needsThis && (anInterface || dialog.isMakeStatic());
    return true;
  }

  private void doRefactoring() throws IncorrectOperationException {
    calculateTypeParametersToCreate();
    PsiClass aClass = createClass(myNewClassName);
    myTargetClass.add(aClass);

    PsiNewExpression newExpr = (PsiNewExpression) myAnonClass.getParent();
    @NonNls StringBuffer buf = new StringBuffer();
    buf.append("new ");
    buf.append(aClass.getName());
    if (!myTypeParametersToCreate.isEmpty()) {
      buf.append("<");
      int idx = 0;
      //noinspection ForLoopThatDoesntUseLoopVariable
      for (Iterator<PsiTypeParameter> it = myTypeParametersToCreate.iterator(); it.hasNext();  idx++) {
        if (idx > 0) buf.append(", ");
        String typeParamName = it.next().getName();
        buf.append(typeParamName);
      }
      buf.append(">");
    }
    buf.append("(");
    boolean isFirstParameter = true;
    for (VariableInfo info : myVariableInfos) {
      if (info.passAsParameter) {
        if (isFirstParameter) {
          isFirstParameter = false;
        }
        else {
          buf.append(",");
        }
        buf.append(info.variable.getName());
      }
    }
    buf.append(")");
    PsiNewExpression newClassExpression =
      (PsiNewExpression)JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createExpressionFromText(buf.toString(), null);
    newClassExpression = (PsiNewExpression)newExpr.replace(newClassExpression);
    if (PsiDiamondTypeUtil.canCollapseToDiamond(newClassExpression, newClassExpression, newClassExpression.getType())) {
      PsiDiamondTypeUtil.replaceExplicitWithDiamond(newClassExpression.getClassOrAnonymousClassReference().getParameterList());
    }
  }

  @Nullable
  public static PsiAnonymousClass findAnonymousClass(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    while (element != null) {
      if (element instanceof PsiAnonymousClass) {
        return (PsiAnonymousClass) element;
      }
      if (element instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)element;
        if (newExpression.getAnonymousClass() != null) {
          return newExpression.getAnonymousClass();
        }
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiElement findTargetContainer(PsiAnonymousClass anonClass) {
    PsiElement parent = anonClass.getParent();
    while (true) {
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        return parent;
      }
      if (parent instanceof PsiFile) {
        return parent;
      }
      parent = parent.getParent();
    }
  }

  private void collectUsedVariables(final Map<PsiVariable, VariableInfo> variableInfoMap,
                                    PsiElement scope) {
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.getQualifierExpression() == null) {
          PsiElement refElement = expression.resolve();
          if (refElement instanceof PsiVariable && !(refElement instanceof PsiField)) {
            PsiVariable var = (PsiVariable)refElement;

            final PsiClass containingClass = PsiTreeUtil.getParentOfType(var, PsiClass.class);
            if (PsiTreeUtil.isAncestor(containingClass, myAnonClass, true)) {
              saveVariable(variableInfoMap, var, expression);
            }
          }
        }
        super.visitReferenceExpression(expression);
      }
    });
  }

  private Boolean cachedNeedsThis;
  public boolean needsThis() {
    if(cachedNeedsThis == null) {

      ElementNeedsThis memberNeedsThis = new ElementNeedsThis(myTargetClass, myAnonClass);
      myAnonClass.accept(memberNeedsThis);
      class HasExplicitThis extends JavaRecursiveElementWalkingVisitor {
        boolean hasExplicitThis;
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        @Override public void visitThisExpression(PsiThisExpression expression) {
          hasExplicitThis = true;
        }
      }
      final HasExplicitThis hasExplicitThis = new HasExplicitThis();
      PsiExpressionList argList = myAnonClass.getArgumentList();
      if (argList != null) argList.accept(hasExplicitThis);
      cachedNeedsThis = memberNeedsThis.usesMembers() || hasExplicitThis.hasExplicitThis;
    }
    return cachedNeedsThis.booleanValue();
  }


  private void saveVariable(Map<PsiVariable, VariableInfo> variableInfoMap,
                            PsiVariable var,
                            PsiReferenceExpression usage) {
    VariableInfo info = variableInfoMap.get(var);
    if (info == null) {
      info = new VariableInfo(var);
      variableInfoMap.put(var, info);
    }
    info.saveInField |= !isUsedInInitializer(usage);
  }

  private boolean isUsedInInitializer(PsiElement usage) {
    PsiElement parent = usage.getParent();
    while (!myAnonClass.equals(parent)) {
      if (parent instanceof PsiExpressionList) {
        PsiExpressionList expressionList = (PsiExpressionList) parent;
        if (myAnonClass.equals(expressionList.getParent())) {
          return true;
        }
      } else if (parent instanceof PsiClassInitializer && myAnonClass.equals(((PsiClassInitializer)parent).getContainingClass())) {
        //class initializers will be moved to constructor to be generated
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  private PsiClass createClass(String name) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myAnonClass.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    final PsiNewExpression newExpression = (PsiNewExpression) myAnonClass.getParent();
    final PsiMethod superConstructor = newExpression.resolveConstructor();

    PsiClass aClass = factory.createClass(name);
    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    LOG.assertTrue(typeParameterList != null);
    for (PsiTypeParameter typeParameter : myTypeParametersToCreate) {
      typeParameterList.add((typeParameter));
    }

    if (!myTargetClass.isInterface()) {
      PsiUtil.setModifierProperty(aClass, PsiModifier.PRIVATE, true);
      PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(myAnonClass, PsiModifierListOwner.class);
      if (owner != null && owner.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(aClass, PsiModifier.STATIC, true);
      }
    } else {
      PsiUtil.setModifierProperty(aClass, PsiModifier.PACKAGE_LOCAL, true);
    }
    PsiJavaCodeReferenceElement baseClassRef = myAnonClass.getBaseClassReference();
    PsiClass baseClass = (PsiClass)baseClassRef.resolve();
    if (baseClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())) {
      PsiReferenceList refList = baseClass != null && baseClass.isInterface() ?
                                 aClass.getImplementsList() :
                                 aClass.getExtendsList();
      if (refList != null) refList.add(baseClassRef);
    }

    renameReferences(myAnonClass);
    copyClassBody(myAnonClass, aClass, myVariableInfos.length > 0);

    if (myVariableInfos.length > 0) {
      createFields(aClass);
    }

    PsiExpressionList argList = newExpression.getArgumentList();
    assert argList != null;
    PsiExpression[] originalExpressions = argList.getExpressions();
    final PsiReferenceList superConstructorThrowsList =
            superConstructor != null && superConstructor.getThrowsList().getReferencedTypes().length > 0
            ? superConstructor.getThrowsList()
            : null;
    if (myVariableInfos.length > 0 || originalExpressions.length > 0 || superConstructorThrowsList != null) {
      PsiMethod constructor = factory.createConstructor();
      if (superConstructorThrowsList != null) {
        constructor.getThrowsList().replace(superConstructorThrowsList);
      }
      if (originalExpressions.length > 0) {
        createSuperStatement(constructor, originalExpressions);
      }
      if (myVariableInfos.length > 0) {
        fillParameterList(constructor);
        createAssignmentStatements(constructor);

        appendInitializers(constructor);
      }

      constructor = (PsiMethod) codeStyleManager.reformat(constructor);
      aClass.add(constructor);
    }

    if (!needsThis() && myMakeStatic && !myTargetClass.isInterface()) {
      PsiUtil.setModifierProperty(aClass, PsiModifier.STATIC, true);
    }
    PsiElement lastChild = aClass.getLastChild();
    if (lastChild instanceof PsiJavaToken && ((PsiJavaToken)lastChild).getTokenType() == JavaTokenType.SEMICOLON) {
      lastChild.delete();
    }

    return aClass;
  }

  private void appendInitializers(final PsiMethod constructor) throws IncorrectOperationException {
    PsiCodeBlock constructorBody = constructor.getBody();
    assert constructorBody != null;

    List<PsiElement> toAdd = new ArrayList<>();
    for (PsiClassInitializer initializer : myAnonClass.getInitializers()) {
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        toAdd.add(initializer);
      }
    }
    for (PsiField field : myAnonClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null) {
        toAdd.add(field);
      }
    }

    Collections.sort(toAdd, (e1, e2) -> e1.getTextRange().getStartOffset() - e2.getTextRange().getStartOffset());

    for (PsiElement element : toAdd) {
      if (element instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer) element;
        final PsiCodeBlock initializerBody = initializer.getBody();
        PsiElement firstBodyElement = initializerBody.getFirstBodyElement();
        if (firstBodyElement != null) {
          constructorBody.addRange(firstBodyElement, initializerBody.getLastBodyElement());
        }
      } else {
        PsiField field = (PsiField) element;
        final PsiExpressionStatement statement = (PsiExpressionStatement)JavaPsiFacade.getInstance(myManager.getProject())
          .getElementFactory()
          .createStatementFromText(field.getName() + "= 0;", null);
        PsiExpression rightExpression = ((PsiAssignmentExpression) statement.getExpression()).getRExpression();
        assert rightExpression != null;
        PsiExpression fieldInitializer = field.getInitializer();
        assert fieldInitializer != null;
        rightExpression.replace(fieldInitializer);
        constructorBody.add(statement);
      }
    }
  }

  private static void copyClassBody(PsiClass sourceClass,
                                    PsiClass targetClass,
                                    boolean appendInitializersToConstructor) throws IncorrectOperationException {
    PsiElement lbrace = sourceClass.getLBrace();
    PsiElement rbrace = sourceClass.getRBrace();
    if (lbrace != null) {
      targetClass.addRange(lbrace.getNextSibling(), rbrace != null ? rbrace.getPrevSibling() : sourceClass.getLastChild());
      if (appendInitializersToConstructor) {  //see SCR 41692
        final PsiClassInitializer[] initializers = targetClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
          if (!initializer.hasModifierProperty(PsiModifier.STATIC)) initializer.delete();
        }
        final PsiField[] fields = targetClass.getFields();
        for (PsiField field : fields) {
          PsiExpression initializer = field.getInitializer();
          if (!field.hasModifierProperty(PsiModifier.STATIC) && initializer != null) {
            initializer.delete();
          }
        }
      }
    }
  }

  private void fillParameterList(PsiMethod constructor) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(constructor.getProject()).getElementFactory();
    PsiParameterList parameterList = constructor.getParameterList();
    for (VariableInfo info : myVariableInfos) {
      if (info.passAsParameter) {
        parameterList.add(factory.createParameter(info.parameterName, info.variable.getType()));
      }
    }
  }

  private void createFields(PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    for (VariableInfo info : myVariableInfos) {
      if (info.saveInField) {
        PsiType type = info.variable.getType();
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        PsiField field = factory.createField(info.fieldName, type);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        aClass.add(field);
      }
    }
  }

  private void createAssignmentStatements(PsiMethod constructor) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(constructor.getProject()).getElementFactory();
    for (VariableInfo info : myVariableInfos) {
      if (info.saveInField) {
        @NonNls String text = info.fieldName + "=a;";
        boolean useThis = info.passAsParameter && info.parameterName.equals(info.fieldName);
        if (useThis) {
          text = "this." + text;
        }
        PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(text, null);
        statement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(statement);
        // in order for "..." trick to work, the statement must be added to constructor first
        PsiCodeBlock constructorBody = constructor.getBody();
        assert constructorBody != null;
        statement = (PsiExpressionStatement)constructorBody.add(statement);

        PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
        PsiReferenceExpression rExpr = (PsiReferenceExpression)assignment.getRExpression();
        assert rExpr != null;
        if (info.passAsParameter) {
          rExpr.replace(factory.createExpressionFromText(info.parameterName, null));
        }
        else {
          rExpr.delete();
        }
      }
    }
  }

  private void renameReferences(PsiElement scope) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    for (VariableInfo info : myVariableInfos) {
      for (PsiReference reference : ReferencesSearch.search(info.variable, new LocalSearchScope(scope))) {
        PsiElement ref = reference.getElement();
        PsiIdentifier identifier = (PsiIdentifier)((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
        assert identifier != null;
        boolean renameToFieldName = !isUsedInInitializer(ref);
        PsiIdentifier newNameIdentifier = factory.createIdentifier(renameToFieldName ? info.fieldName : info.parameterName);
        if (renameToFieldName) {
          identifier.replace(newNameIdentifier);
        }
        else {
          if (info.passAsParameter) {
            identifier.replace(newNameIdentifier);
          }
        }
      }
    }
  }

  private void createSuperStatement(PsiMethod constructor, PsiExpression[] paramExpressions) throws IncorrectOperationException {
    PsiCodeBlock body = constructor.getBody();
    assert body != null;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(constructor.getProject()).getElementFactory();

    PsiStatement statement = factory.createStatementFromText("super();", null);
    statement = (PsiStatement) CodeStyleManager.getInstance(myProject).reformat(statement);
    statement = (PsiStatement) body.add(statement);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) ((PsiExpressionStatement) statement).getExpression();
    PsiExpressionList exprList = methodCall.getArgumentList();


    {
      final PsiThisExpression qualifiedThis =
        (PsiThisExpression) factory.createExpressionFromText("A.this", null);
      final PsiJavaCodeReferenceElement targetClassRef = factory.createClassReferenceElement(myTargetClass);
      PsiJavaCodeReferenceElement thisQualifier = qualifiedThis.getQualifier();
      assert thisQualifier != null;
      thisQualifier.replace(targetClassRef);

      for (PsiExpression expr : paramExpressions) {
        ChangeContextUtil.encodeContextInfo(expr, true);
        final PsiElement newExpr = exprList.add(expr);
        ChangeContextUtil.decodeContextInfo(newExpr, myTargetClass, qualifiedThis);
      }
    }

    class SupersConvertor extends JavaRecursiveElementVisitor {
      @Override public void visitThisExpression(PsiThisExpression expression) {
        try {
          final PsiThisExpression qualifiedThis =
                  (PsiThisExpression) factory.createExpressionFromText("A.this", null);
          final PsiJavaCodeReferenceElement targetClassRef = factory.createClassReferenceElement(myTargetClass);
          PsiJavaCodeReferenceElement thisQualifier = qualifiedThis.getQualifier();
          assert thisQualifier != null;
          thisQualifier.replace(targetClassRef);
          expression.replace(qualifiedThis);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      }
    }

    final SupersConvertor supersConvertor = new SupersConvertor();
    methodCall.getArgumentList().accept(supersConvertor);
  }

  private void calculateTypeParametersToCreate () {
    myAnonClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiTypeParameter) {
          final PsiTypeParameterListOwner owner = ((PsiTypeParameter)resolved).getOwner();
          if (owner != null && !PsiTreeUtil.isAncestor(myAnonClass, owner, false)) {
            myTypeParametersToCreate.add((PsiTypeParameter)resolved);
          }
        }
      }
    });
  }
}

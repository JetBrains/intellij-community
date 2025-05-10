// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.anonymousToInner;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandlerOnPsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.jdk.VarargParameterInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AnonymousToInnerHandler implements RefactoringActionHandlerOnPsiElement<PsiClass> {
  private static final Logger LOG = Logger.getInstance(AnonymousToInnerHandler.class);

  private Project myProject;

  private PsiManager myManager;

  private PsiClass myAnonOrLocalClass;
  private PsiClass myTargetClass;
  protected String myNewClassName;

  protected VariableInfo[] myVariableInfos;
  protected boolean myMakeStatic;
  private final Set<PsiTypeParameter> myTypeParametersToCreate = new LinkedHashSet<>();

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length == 1 && elements[0] instanceof PsiClass aClass &&
        (aClass instanceof PsiAnonymousClass || PsiUtil.isLocalClass(aClass))) {
      invoke(project, CommonDataKeys.EDITOR.getData(dataContext), aClass);
    }
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile file, DataContext dataContext) {
    myProject = project;
    final int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final @Nullable PsiClass anonymousOrLocal = findClassToMove(file, offset);
    if (anonymousOrLocal == null) {
      showErrorMessage(editor, RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.anonymous")));
      return;
    }
    final PsiElement parent = anonymousOrLocal.getParent();
    if (parent instanceof PsiEnumConstant) {
      showErrorMessage(editor, RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("anonymous.to.inner.enum.constant.cannot.refactor.message")));
      return;
    }
    invoke(project, editor, anonymousOrLocal);
  }

  private void showErrorMessage(Editor editor, @NlsContexts.DialogMessage String message) {
    CommonRefactoringUtil.showErrorHint(myProject, editor, message, JavaRefactoringBundle.message("anonymousToInner.refactoring.name"), HelpID.ANONYMOUS_TO_INNER);
  }

  @Override
  public void invoke(final Project project, Editor editor, final PsiClass anonymousOrLocalClass) {
    myProject = project;

    myManager = PsiManager.getInstance(myProject);
    myAnonOrLocalClass = anonymousOrLocalClass;

    for (PsiClassType baseRef : myAnonOrLocalClass.getSuperTypes()) {
      // Ignore j.l.Record and j.l.Enum base classes, so we can move records/enums with broken/missing JDK 
      String refText = baseRef.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_RECORD.equals(refText) ||
          CommonClassNames.JAVA_LANG_ENUM.equals(refText)) {
        continue;
      }
      PsiClass baseClass = baseRef.resolve();
      if (baseClass == null) {
        String message = JavaRefactoringBundle.message("error.cannot.resolve", refText);
        showErrorMessage(editor, message);
        return;
      }

      if (PsiUtil.isLocalClass(baseClass)) {
        String message = JavaRefactoringBundle.message("error.not.supported.for.local");
        showErrorMessage(editor, message);
        return;
      }
    }

    PsiElement targetContainer = findTargetContainer(myAnonOrLocalClass);
    if (FileTypeUtils.isInServerPageFile(targetContainer) && targetContainer instanceof PsiFile) {
      String message = JavaRefactoringBundle.message("error.not.supported.for.jsp",
                                                     JavaRefactoringBundle.message("anonymousToInner.refactoring.name"));
      showErrorMessage(editor, message);
      return;
    }
    LOG.assertTrue(targetContainer instanceof PsiClass);
    myTargetClass = (PsiClass) targetContainer;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, myTargetClass)) return;

    Map<PsiVariable,VariableInfo> variableInfoMap = new LinkedHashMap<>();
    if (!myAnonOrLocalClass.hasModifierProperty(PsiModifier.STATIC)) {
      collectUsedVariables(variableInfoMap, myAnonOrLocalClass);
    }
    final VariableInfo[] infos = variableInfoMap.values().toArray(new VariableInfo[0]);
    myVariableInfos = infos;
    Arrays.sort(myVariableInfos,
                Comparator.comparing((VariableInfo vi) -> vi.variable.getType() instanceof PsiEllipsisType)
                  .thenComparing(vi -> ArrayUtil.find(infos, vi)));
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
      JavaRefactoringBundle.message("anonymousToInner.refactoring.name"),
      null
    );

  }

  protected boolean showRefactoringDialog() {
    final boolean anInterface = myTargetClass.isInterface();
    final boolean needsThis = needsThis() || PsiUtil.isInnerClass(myTargetClass);
    boolean showCanBeStatic = !needsThis && !anInterface && !myAnonOrLocalClass.hasModifierProperty(PsiModifier.STATIC);
    final AnonymousToInnerDialog dialog = new AnonymousToInnerDialog(
      myProject,
      myAnonOrLocalClass,
      myVariableInfos,
      showCanBeStatic);
    if (!dialog.showAndGet()) {
      return false;
    }
    myNewClassName = dialog.getClassName();
    myVariableInfos = dialog.getVariableInfos();
    myMakeStatic = !myAnonOrLocalClass.hasModifierProperty(PsiModifier.STATIC) && !needsThis && (anInterface || dialog.isMakeStatic());
    return true;
  }

  private void doRefactoring() throws IncorrectOperationException {
    calculateTypeParametersToCreate();
    ChangeContextUtil.encodeContextInfo(myAnonOrLocalClass, false);
    updateLocalClassConstructors(myAnonOrLocalClass);
    PsiClass innerClass = (PsiClass)myTargetClass.add(createClass(myNewClassName));
    ChangeContextUtil.decodeContextInfo(innerClass, myTargetClass, RefactoringChangeUtil.createThisExpression(myTargetClass.getManager(), myTargetClass));

    if (myAnonOrLocalClass instanceof PsiAnonymousClass) {
      migrateAnonymousClassCreation(innerClass);
    } else {
      migrateLocalClassReferences(innerClass);
    }
    collapseDiamonds(innerClass);
  }

  private void collapseDiamonds(PsiClass innerClass) {
    for (PsiReference reference : ReferencesSearch.search(innerClass, new LocalSearchScope(myTargetClass)).findAll()) {
      if (reference.getElement() instanceof PsiJavaCodeReferenceElement refElement && refElement.isValid()) {
        PsiElement parent = refElement.getParent();
        if (parent instanceof PsiAnonymousClass anonymousClass) {
          parent = anonymousClass.getParent();
        }
        if (parent instanceof PsiNewExpression newExpression) {
          PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
          if (ref != null && PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, newExpression.getType())) {
            RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(ref.getParameterList());
          }
        }
      }
    }
  }

  private void migrateLocalClassReferences(PsiClass innerClass) {
    SearchScope scope = myAnonOrLocalClass.getUseScope();
    Collection<PsiReference> refs = ReferencesSearch.search(myAnonOrLocalClass, scope).findAll();
    myAnonOrLocalClass.delete();
    for (PsiReference reference : refs) {
      if (reference.getElement().isValid()) {
        reference.bindToElement(innerClass);
      }
    }
  }

  private void migrateAnonymousClassCreation(PsiClass innerClass) {
    PsiNewExpression newExpr = (PsiNewExpression) myAnonOrLocalClass.getParent();
    @NonNls StringBuilder buf = new StringBuilder();
    buf.append("new ");
    buf.append(innerClass.getName());
    if (!myTypeParametersToCreate.isEmpty()) {
      buf.append("<");
      int idx = 0;
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
      (PsiNewExpression)JavaPsiFacade.getElementFactory(myManager.getProject()).createExpressionFromText(buf.toString(), null);
    newExpr.replace(newClassExpression);
  }

  private static @Nullable PsiClass findClassToMove(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    while (element != null) {
      if (element instanceof PsiAnonymousClass anonymousClass) {
        return anonymousClass;
      }
      if (element instanceof PsiClass psiClass && PsiUtil.isLocalClass(psiClass)) {
        return psiClass;
      }
      if (element instanceof PsiNewExpression newExpression && newExpression.getAnonymousClass() != null) {
        return newExpression.getAnonymousClass();
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiElement findTargetContainer(PsiClass anonOrLocalClass) {
    PsiElement parent = anonOrLocalClass.getParent();
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

  private void collectUsedVariables(final Map<PsiVariable, VariableInfo> variableInfoMap, PsiElement scope) {
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.getQualifierExpression() == null) {
          PsiElement refElement = expression.resolve();
          if (refElement instanceof PsiVariable var && !(refElement instanceof PsiField)) {
            final PsiClass containingClass = PsiTreeUtil.getParentOfType(var, PsiClass.class);
            if (PsiTreeUtil.isAncestor(containingClass, myAnonOrLocalClass, true)) {
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

      ElementNeedsThis memberNeedsThis = new ElementNeedsThis(myTargetClass, myAnonOrLocalClass);
      myAnonOrLocalClass.accept(memberNeedsThis);
      class HasExplicitThis extends JavaRecursiveElementWalkingVisitor {
        boolean hasExplicitThis;
        @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        }

        @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
          hasExplicitThis = true;
        }
      }
      final HasExplicitThis hasExplicitThis = new HasExplicitThis();
      if (myAnonOrLocalClass instanceof PsiAnonymousClass anonymousClass) {
        PsiExpressionList argList = anonymousClass.getArgumentList();
        if (argList != null) argList.accept(hasExplicitThis);
      }
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
    while (!myAnonOrLocalClass.equals(parent)) {
      if (parent instanceof PsiExpressionList expressionList) {
        if (myAnonOrLocalClass.equals(expressionList.getParent())) {
          return true;
        }
      } else if ((parent instanceof PsiClassInitializer || parent instanceof PsiField || 
                  parent instanceof PsiMethod method && method.isConstructor())
                 && myAnonOrLocalClass.equals(((PsiMember)parent).getContainingClass())) {
        //class and field initializers will be moved to constructor to be generated
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  private PsiClass createClass(String name) throws IncorrectOperationException {
    renameReferences(myAnonOrLocalClass);
    updateSelfReferences(myAnonOrLocalClass, name);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);

    PsiClass aClass;
    if (myAnonOrLocalClass instanceof PsiAnonymousClass anonymousClass) {
      aClass = createInnerFromAnonymous(name, anonymousClass, factory);
    }
    else {
      aClass = createInnerFromLocal(name, factory);
    }
    final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    LOG.assertTrue(typeParameterList != null);
    for (PsiTypeParameter typeParameter : myTypeParametersToCreate) {
      typeParameterList.add(typeParameter);
    }

    setupModifiers(aClass);

    if (myVariableInfos.length > 0) {
      removeInitializers(aClass);
      createFields(aClass);
    }

    if (myAnonOrLocalClass instanceof PsiAnonymousClass) {
      createAnonymousClassConstructor(aClass);
    }

    PsiElement lastChild = aClass.getLastChild();
    if (PsiUtil.isJavaToken(lastChild, JavaTokenType.SEMICOLON)) {
      lastChild.delete();
    }

    return aClass;
  }

  private @NotNull PsiClass createInnerFromLocal(String name, PsiElementFactory factory) {
    PsiClass aClass = (PsiClass)myAnonOrLocalClass.copy();
    PsiIdentifier identifier = factory.createIdentifier(name);
    Objects.requireNonNull(aClass.getNameIdentifier()).replace(identifier);
    for (PsiMethod method : aClass.getMethods()) {
      if (method.isConstructor()) {
        PsiIdentifier methodName = method.getNameIdentifier();
        if (methodName != null) {
          methodName.replace(identifier);
        }
      }
    }
    return aClass;
  }

  private @NotNull PsiClass createInnerFromAnonymous(String name, PsiAnonymousClass anonymousClass, PsiElementFactory factory) {
    PsiClass aClass = factory.createClass(name);
    PsiJavaCodeReferenceElement baseClassRef = anonymousClass.getBaseClassReference();
    final PsiReferenceParameterList parameterList = baseClassRef.getParameterList();
    if (parameterList != null) {
      final PsiTypeElement[] parameterElements = parameterList.getTypeParameterElements();
      if (parameterElements.length == 1 && parameterElements[0].getType() instanceof PsiDiamondType) {
        baseClassRef = (PsiJavaCodeReferenceElement)PsiDiamondTypeUtil.replaceDiamondWithExplicitTypes(parameterList);
      }
    }
    PsiClass baseClass = (PsiClass)baseClassRef.resolve();
    if (baseClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName())) {
      PsiReferenceList refList = baseClass != null && baseClass.isInterface() ?
                                 aClass.getImplementsList() :
                                 aClass.getExtendsList();
      if (refList != null) refList.add(baseClassRef);
    }
    PsiElement lbrace = myAnonOrLocalClass.getLBrace();
    PsiElement rbrace = myAnonOrLocalClass.getRBrace();
    if (lbrace != null) {
      aClass.addRange(lbrace.getNextSibling(), rbrace != null ? rbrace.getPrevSibling() : myAnonOrLocalClass.getLastChild());
    }
    return aClass;
  }

  private void setupModifiers(@NotNull PsiClass aClass) {
    if (!myTargetClass.isInterface()) {
      PsiUtil.setModifierProperty(aClass, PsiModifier.PRIVATE, true);
      if (aClass.isInterface() || aClass.isEnum() || aClass.isRecord()) {
        // always implicitly static
        return;
      }
      PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(myAnonOrLocalClass, PsiModifierListOwner.class);
      if (owner != null && owner.hasModifierProperty(PsiModifier.STATIC) || myMakeStatic) {
        PsiUtil.setModifierProperty(aClass, PsiModifier.STATIC, true);
      }
    } else {
      PsiUtil.setModifierProperty(aClass, PsiModifier.PACKAGE_LOCAL, true);
    }
  }

  private void updateSelfReferences(@NotNull PsiClass aClass, String name) {
    if (aClass instanceof PsiAnonymousClass) return;
    if (name.equals(aClass.getName()) && myTypeParametersToCreate.isEmpty()) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
    int origCount = aClass.getTypeParameters().length;
    for (PsiReference reference : ReferencesSearch.search(aClass, aClass.getUseScope()).findAll()) {
      reference.handleElementRename(name);
      if (!myTypeParametersToCreate.isEmpty()) {
        if (reference.getElement() instanceof PsiJavaCodeReferenceElement refElement &&
            !(refElement.getParent() instanceof PsiTypeElement typeElement && 
              typeElement.getParent() instanceof PsiClassObjectAccessExpression)) {
          PsiReferenceParameterList list = PsiTreeUtil.getChildOfType(refElement, PsiReferenceParameterList.class);
          // Do not modify broken or already raw parameter lists
          if (list != null && list.getTypeArgumentCount() == origCount) {
            for (PsiTypeParameter parameter : myTypeParametersToCreate) {
              PsiTypeElement element = factory.createTypeElement(factory.createType(parameter));
              list.add(element);
            }
          }
        }
      }
    }
  }

  private void updateLocalClassConstructors(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass || myVariableInfos.length == 0) return;
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      constructors = new PsiMethod[]{AddDefaultConstructorFix.addDefaultConstructor(aClass)};
    }
    Map<PsiMethod, List<PsiNewExpression>> newExpressions = new HashMap<>();
    for (PsiMethod constructor : constructors) {
      newExpressions.put(constructor, new ArrayList<>());
      if (constructor.isVarArgs()) {
        VarargParameterInspection.convertVarargMethodToArray(constructor);
      }
    }
    for (PsiReference reference : ReferencesSearch.search(aClass, aClass.getUseScope()).findAll()) {
      if (reference.getElement().getParent() instanceof PsiMethodReferenceExpression methodRef && methodRef.isConstructor()) {
        LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, false, true);
      }      
    }
    for (PsiReference reference : ReferencesSearch.search(aClass, aClass.getUseScope()).findAll()) {
      PsiElement parent = reference.getElement().getParent();
      if (parent instanceof PsiAnonymousClass cls) {
        parent = cls.getParent();
      }
      if (parent instanceof PsiNewExpression newExpression) {
        PsiMethod method = newExpression.resolveConstructor();
        List<PsiNewExpression> newList = newExpressions.get(method);
        if (newList != null) {
          newList.add(newExpression);
        }
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    newExpressions.forEach((constructor, callSites) -> {
      fillParameterList(constructor);
      createAssignmentStatements(constructor);

      appendInitializers(constructor);
      for (PsiNewExpression callSite : callSites) {
        PsiExpressionList arguments = callSite.getArgumentList();
        if (arguments != null) {
          for (VariableInfo info : myVariableInfos) {
            arguments.add(factory.createExpressionFromText(Objects.requireNonNull(info.variable.getName()), arguments));
          }
        }
      }
    });
  }

  private void createAnonymousClassConstructor(@NotNull PsiClass aClass) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
    final PsiNewExpression newExpression = (PsiNewExpression) myAnonOrLocalClass.getParent();
    PsiExpressionList argList = newExpression.getArgumentList();
    assert argList != null;
    PsiExpression[] originalExpressions = argList.getExpressions();
    final PsiMethod superConstructor = newExpression.resolveConstructor();
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
  }

  private void appendInitializers(final PsiMethod constructor) throws IncorrectOperationException {
    PsiCodeBlock constructorBody = constructor.getBody();
    assert constructorBody != null;

    List<PsiElement> toAdd = new ArrayList<>();
    for (PsiClassInitializer initializer : myAnonOrLocalClass.getInitializers()) {
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        toAdd.add(initializer);
      }
    }
    for (PsiField field : myAnonOrLocalClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() != null) {
        toAdd.add(field);
      }
    }

    toAdd.sort(Comparator.comparingInt(e -> e.getTextRange().getStartOffset()));

    for (PsiElement element : toAdd) {
      if (element instanceof PsiClassInitializer initializer) {
        final PsiCodeBlock initializerBody = initializer.getBody();
        PsiElement firstBodyElement = initializerBody.getFirstBodyElement();
        if (firstBodyElement != null) {
          constructorBody.addRange(firstBodyElement, initializerBody.getLastBodyElement());
        }
      } else {
        PsiField field = (PsiField) element;
        final PsiExpressionStatement statement = (PsiExpressionStatement)JavaPsiFacade.getElementFactory(myManager.getProject())
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

  private static void removeInitializers(PsiClass targetClass) {
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

  private void fillParameterList(PsiMethod constructor) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());
    PsiParameterList parameterList = constructor.getParameterList();
    for (VariableInfo info : myVariableInfos) {
      if (info.passAsParameter) {
        parameterList.add(factory.createParameter(info.parameterName, info.variable.getType()));
      }
    }
  }

  private void createFields(PsiClass aClass) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myManager.getProject());
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
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());
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
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myManager.getProject());
    for (VariableInfo info : myVariableInfos) {
      for (PsiReference reference : ReferencesSearch.search(info.variable, new LocalSearchScope(scope)).asIterable()) {
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
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(constructor.getProject());

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
      @Override public void visitThisExpression(@NotNull PsiThisExpression expression) {
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

      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      }
    }

    final SupersConvertor supersConvertor = new SupersConvertor();
    methodCall.getArgumentList().accept(supersConvertor);
  }

  private void calculateTypeParametersToCreate () {
    JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiTypeParameter typeParameter) {
          final PsiTypeParameterListOwner owner = typeParameter.getOwner();
          if (owner != null && !PsiTreeUtil.isAncestor(myAnonOrLocalClass, owner, false) &&
              (!PsiTreeUtil.isAncestor(owner, myTargetClass, false) || myMakeStatic)) {
            myTypeParametersToCreate.add(typeParameter);
          }
        }
      }
    };
    myAnonOrLocalClass.accept(visitor);
    for (VariableInfo info : myVariableInfos) {
      PsiTypeElement typeElement = info.variable.getTypeElement();
      if (typeElement != null) {
        typeElement.accept(visitor);
      }
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull PsiElement element) {
    if (element instanceof PsiClass aClass) {
      myProject = project;
      myManager = PsiManager.getInstance(myProject);
      myAnonOrLocalClass = aClass;
      myNewClassName = AnonymousToInnerDialog.suggestNewClassNames(aClass)[0];
      PsiElement targetContainer = findTargetContainer(myAnonOrLocalClass);
      if (targetContainer instanceof PsiClass) {
        myTargetClass = (PsiClass)targetContainer;
      }
      else {
        return IntentionPreviewInfo.EMPTY;
      }
      myMakeStatic = !needsThis() && !myTargetClass.isInterface();
      Map<PsiVariable,VariableInfo> variableInfoMap = new LinkedHashMap<>();
      collectUsedVariables(variableInfoMap, myAnonOrLocalClass);
      myVariableInfos = variableInfoMap.values().toArray(new VariableInfo[0]);
      VariableData [] variableData = new VariableData[myVariableInfos.length];
      AnonymousToInnerDialog.fillVariableData(myProject, myVariableInfos, variableData);
      AnonymousToInnerDialog.getVariableInfos(myProject, variableData, variableInfoMap);
      doRefactoring();
      return IntentionPreviewInfo.DIFF;
    }
    return IntentionPreviewInfo.EMPTY;
  }
}

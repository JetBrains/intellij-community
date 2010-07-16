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
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineMethodProcessor");

  private PsiMethod myMethod;
  private PsiJavaCodeReferenceElement myReference;
  private final Editor myEditor;
  private final boolean myInlineThisOnly;

  private final PsiManager myManager;
  private final PsiElementFactory myFactory;
  private final CodeStyleManager myCodeStyleManager;
  private final JavaCodeStyleManager myJavaCodeStyle;

  private PsiBlockStatement[] myAddedBraces;
  private final String myDescriptiveName;
  private Map<PsiField, PsiClassInitializer> myAddedClassInitializers;
  private PsiMethod myMethodCopy;

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiJavaCodeReferenceElement reference,
                               Editor editor,
                               boolean isInlineThisOnly) {
    super(project);
    myMethod = method;
    myReference = reference;
    myEditor = editor;
    myInlineThisOnly = isInlineThisOnly;

    myManager = PsiManager.getInstance(myProject);
    myFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    myJavaCodeStyle = JavaCodeStyleManager.getInstance(myProject);
    myDescriptiveName = UsageViewUtil.getDescriptiveName(myMethod);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.method.command", myDescriptiveName);
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myReference)};
    Set<UsageInfo> usages = new HashSet<UsageInfo>();
    if (myReference != null) {
      usages.add(new UsageInfo(myReference));
    }
    for (PsiReference reference : ReferencesSearch.search(myMethod)) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myMethod = (PsiMethod)elements[0];
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usagesIn = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    if (!myInlineThisOnly) {
      final PsiMethod[] superMethods = myMethod.findSuperMethods();
      for (PsiMethod method : superMethods) {
        final String message = method.hasModifierProperty(PsiModifier.ABSTRACT) ? RefactoringBundle
          .message("inlined.method.implements.method.from.0", method.getContainingClass().getQualifiedName()) : RefactoringBundle
          .message("inlined.method.overrides.method.from.0", method.getContainingClass().getQualifiedName());
        conflicts.putValue(method, message);
      }
    }

    addInaccessibleMemberConflicts(myMethod, usagesIn, new ReferencedElementsCollector(), conflicts);

    addInaccessibleSuperCallsConflicts(usagesIn, conflicts);

    if (!myInlineThisOnly) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myMethod)) return false;
    }
    return showConflicts(conflicts, usagesIn);
  }

  private void addInaccessibleSuperCallsConflicts(final UsageInfo[] usagesIn, final MultiMap<PsiElement, String> conflicts) {

    myMethod.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override
      public void visitClass(PsiClass aClass) {}

      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {}

      @Override
      public void visitSuperExpression(PsiSuperExpression expression) {
        super.visitSuperExpression(expression);
        final PsiType type = expression.getType();
        final PsiClass superClass = PsiUtil.resolveClassInType(type);
        if (superClass != null) {
          final Set<PsiClass> targetContainingClasses = new HashSet<PsiClass>();
          for (UsageInfo info : usagesIn) {
            final PsiElement element = info.getElement();
            if (element != null) {
              final PsiClass targetContainingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
              if (targetContainingClass != null && !InheritanceUtil.isInheritorOrSelf(targetContainingClass, superClass, true)) {
                targetContainingClasses.add(targetContainingClass);
              }
            }
          }
          if (!targetContainingClasses.isEmpty()) {
            final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
            LOG.assertTrue(methodCallExpression != null);
            conflicts.putValue(expression, "Inlined method calls " + methodCallExpression.getText() + " which won't be accessed in " +
                                           StringUtil.join(targetContainingClasses, new Function<PsiClass, String>() {
                                             public String fun(PsiClass psiClass) {
                                               return RefactoringUIUtil.getDescription(psiClass, false);
                                             }
                                           }, ","));
          }
        }
      }
    });
  }

  public static void addInaccessibleMemberConflicts(final PsiElement element,
                                                    final UsageInfo[] usages,
                                                    final ReferencedElementsCollector collector,
                                                    final MultiMap<PsiElement, String> conflicts) {
    element.accept(collector);
    final Map<PsiMember, Set<PsiMember>> containersToReferenced = getInaccessible(collector.myReferencedMembers, usages);

    final Set<PsiMember> containers = containersToReferenced.keySet();
    for (PsiMember container : containers) {
      Set<PsiMember> referencedInaccessible = containersToReferenced.get(container);
      for (PsiMember referenced : referencedInaccessible) {
        final String referencedDescription = RefactoringUIUtil.getDescription(referenced, true);
        final String containerDescription = RefactoringUIUtil.getDescription(container, true);
        String message = RefactoringBundle.message("0.that.is.used.in.inlined.method.is.not.accessible.from.call.site.s.in.1",
                                                   referencedDescription, containerDescription);
        conflicts.putValue(container, CommonRefactoringUtil.capitalize(message));
      }
    }
  }

  /**
   * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
   * to subsets of referencedElemens that are not accessible from that container
   *
   * @param referencedElements
   * @param usages
   */
  private static Map<PsiMember, Set<PsiMember>> getInaccessible(HashSet<PsiMember> referencedElements, UsageInfo[] usages) {
    Map<PsiMember, Set<PsiMember>> result = new HashMap<PsiMember, Set<PsiMember>>();

    for (UsageInfo usage : usages) {
      final PsiElement container = ConflictsUtil.getContainer(usage.getElement());
      if (!(container instanceof PsiMember)) continue;    // usage in import statement
      PsiMember memberContainer = (PsiMember)container;
      Set<PsiMember> inaccessibleReferenced = result.get(memberContainer);
      if (inaccessibleReferenced == null) {
        inaccessibleReferenced = new HashSet<PsiMember>();
        result.put(memberContainer, inaccessibleReferenced);
        for (PsiMember member : referencedElements) {
          if (!PsiUtil.isAccessible(member, usage.getElement(), null)) {
            inaccessibleReferenced.add(member);
          }
        }
      }
    }

    return result;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    int col = -1;
    int line = -1;
    if (myEditor != null) {
      col = myEditor.getCaretModel().getLogicalPosition().column;
      line = myEditor.getCaretModel().getLogicalPosition().line;
      LogicalPosition pos = new LogicalPosition(0, 0);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }

    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    try {
      doRefactoring(usages);
    }
    finally {
      a.finish();
    }

    if (myEditor != null) {
      LogicalPosition pos = new LogicalPosition(line, col);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    }
  }

  private void doRefactoring(UsageInfo[] usages) {
    try {
      if (myInlineThisOnly) {
        if (myMethod.isConstructor()) {
          PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(myReference);
          if (constructorCall != null) {
            inlineConstructorCall(constructorCall);
          }
        }
        else {
          myReference = addBracesWhenNeeded(new PsiReferenceExpression[]{(PsiReferenceExpression)myReference})[0];
          inlineMethodCall((PsiReferenceExpression)myReference);
        }
      }
      else {
        RefactoringUtil.sortDepthFirstRightLeftOrder(usages);
        if (myMethod.isConstructor()) {
          for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element instanceof PsiJavaCodeReferenceElement) {
              PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)element);
              if (constructorCall != null) {
                inlineConstructorCall(constructorCall);
              }
            }
            else if (element instanceof PsiEnumConstant) {
              inlineConstructorCall((PsiEnumConstant) element);
            }
          }
          myMethod.delete();
        }
        else {
          List<PsiReferenceExpression> refExprList = new ArrayList<PsiReferenceExpression>();
          for (final UsageInfo usage : usages) {
            final PsiElement element = usage.getElement();
            if (element instanceof PsiReferenceExpression) {
              refExprList.add((PsiReferenceExpression)element);
            }
          }
          PsiReferenceExpression[] refs = refExprList.toArray(new PsiReferenceExpression[refExprList.size()]);
          refs = addBracesWhenNeeded(refs);
          for (PsiReferenceExpression ref : refs) {
            inlineMethodCall(ref);
          }
          myMethod.delete();
        }
      }
      removeAddedBracesWhenPossible();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static void inlineConstructorCall(PsiCall constructorCall) {
    final PsiMethod oldConstructor = constructorCall.resolveMethod();
    LOG.assertTrue(oldConstructor != null);
    PsiExpression[] instanceCreationArguments = constructorCall.getArgumentList().getExpressions();
    if (oldConstructor.isVarArgs()) { //wrap with explicit array
      final PsiParameter[] parameters = oldConstructor.getParameterList().getParameters();
      final PsiType varargType = parameters[parameters.length - 1].getType();
      if (varargType instanceof PsiEllipsisType) {
        final PsiType arrayType =
          constructorCall.resolveMethodGenerics().getSubstitutor().substitute(((PsiEllipsisType)varargType).getComponentType());
        final PsiExpression[] exprs = new PsiExpression[parameters.length];
        System.arraycopy(instanceCreationArguments, 0, exprs, 0, parameters.length - 1);
        StringBuffer varargs = new StringBuffer();
        for (int i = parameters.length - 1; i < instanceCreationArguments.length; i++) {
          if (varargs.length() > 0) varargs.append(", ");
          varargs.append(instanceCreationArguments[i].getText());
        }

        exprs[parameters.length - 1] = JavaPsiFacade.getElementFactory(constructorCall.getProject())
          .createExpressionFromText("new " + arrayType.getCanonicalText() + "[]{" + varargs.toString() + "}", constructorCall);

        instanceCreationArguments = exprs;
      }
    }

    PsiStatement[] statements = oldConstructor.getBody().getStatements();
    LOG.assertTrue(statements.length == 1 && statements[0] instanceof PsiExpressionStatement);
    PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    LOG.assertTrue(expression instanceof PsiMethodCallExpression);
    ChangeContextUtil.encodeContextInfo(expression, true);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression.copy();
    final PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      replaceParameterReferences(arg, oldConstructor, instanceCreationArguments);
    }

    try {
      final PsiExpressionList exprList = (PsiExpressionList) constructorCall.getArgumentList().replace(methodCall.getArgumentList());
      ChangeContextUtil.decodeContextInfo(exprList, PsiTreeUtil.getParentOfType(constructorCall, PsiClass.class), null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    ChangeContextUtil.clearContextInfo(expression);
  }

  private static void replaceParameterReferences(final PsiElement element,
                                                 final PsiMethod oldConstructor,
                                                 final PsiExpression[] instanceCreationArguments) {
    boolean isParameterReference = false;
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)element;
      PsiElement resolved = expression.resolve();
      if (resolved instanceof PsiParameter &&
          element.getManager().areElementsEquivalent(((PsiParameter)resolved).getDeclarationScope(), oldConstructor)) {
        isParameterReference = true;
        PsiElement declarationScope = ((PsiParameter)resolved).getDeclarationScope();
        PsiParameter[] declarationParameters = ((PsiMethod)declarationScope).getParameterList().getParameters();
        for (int j = 0; j < declarationParameters.length; j++) {
          if (declarationParameters[j] == resolved) {
            try {
              expression.replace(instanceCreationArguments[j]);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
    if (!isParameterReference) {
      PsiElement child = element.getFirstChild();
      while (child != null) {
        PsiElement next = child.getNextSibling();
        replaceParameterReferences(child, oldConstructor, instanceCreationArguments);
        child = next;
      }
    }
  }

  private void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
    InlineUtil.TailCallType tailCall = InlineUtil.getTailCallType(ref);
    ChangeContextUtil.encodeContextInfo(myMethod, false);
    myMethodCopy = (PsiMethod)myMethod.copy();
    ChangeContextUtil.clearContextInfo(myMethod);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    PsiSubstitutor callSubstitutor = getCallSubstitutor(methodCall);
    BlockData blockData = prepareBlock(ref, callSubstitutor, methodCall.getArgumentList(), tailCall);
    solveVariableNameConflicts(blockData.block, ref);
    if (callSubstitutor != PsiSubstitutor.EMPTY) {
      substituteMethodTypeParams(blockData.block, callSubstitutor);
    }
    addParmAndThisVarInitializers(blockData, methodCall);

    PsiElement anchor = RefactoringUtil.getParentStatement(methodCall, true);
    if (anchor == null) {
      PsiEnumConstant enumConstant = PsiTreeUtil.getParentOfType(methodCall, PsiEnumConstant.class);
      if (enumConstant != null) {
        PsiExpression returnExpr = getSimpleReturnedExpression(myMethod);
        if (returnExpr != null) {
          methodCall.replace(returnExpr);
        }
      }
      return;
    }
    PsiElement anchorParent = anchor.getParent();
    PsiLocalVariable thisVar = null;
    PsiLocalVariable[] parmVars = new PsiLocalVariable[blockData.parmVars.length];
    PsiLocalVariable resultVar = null;
    PsiStatement[] statements = blockData.block.getStatements();
    if (statements.length > 0) {
      int last = statements.length - 1;
      /*PsiElement first = statements[0];
      PsiElement last = statements[statements.length - 1];*/

      if (statements.length > 0 && statements[statements.length - 1] instanceof PsiReturnStatement &&
          tailCall != InlineUtil.TailCallType.Return) {
        last--;
      }

      int first = 0;
      if (first <= last) {
        final PsiElement rBraceOrReturnStatement =
          PsiTreeUtil.skipSiblingsForward(statements[last], PsiWhiteSpace.class, PsiComment.class);
        LOG.assertTrue(rBraceOrReturnStatement != null);
        final PsiElement beforeRBraceStatement = rBraceOrReturnStatement.getPrevSibling();
        LOG.assertTrue(beforeRBraceStatement != null);
        PsiElement firstAdded = anchorParent.addRangeBefore(statements[first], beforeRBraceStatement, anchor);

        PsiElement current = firstAdded.getPrevSibling();
        LOG.assertTrue(current != null);
        if (blockData.thisVar != null) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          thisVar = (PsiLocalVariable)statement.getDeclaredElements()[0];
          current = statement;
        }
        for (int i = 0; i < parmVars.length; i++) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          parmVars[i] = (PsiLocalVariable)statement.getDeclaredElements()[0];
          current = statement;
        }
        if (blockData.resultVar != null) {
          PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
          resultVar = (PsiLocalVariable)statement.getDeclaredElements()[0];
        }
      }
      if (statements.length > 0) {
        final PsiStatement lastStatement = statements[statements.length - 1];
        if (lastStatement instanceof PsiReturnStatement && tailCall != InlineUtil.TailCallType.Return) {
          final PsiExpression returnValue = ((PsiReturnStatement)lastStatement).getReturnValue();
          if (returnValue != null && PsiUtil.isStatement(returnValue)) {
            PsiExpressionStatement exprStatement = (PsiExpressionStatement)myFactory.createStatementFromText("a;", null);
            exprStatement.getExpression().replace(returnValue);
            anchorParent.addBefore(exprStatement, anchor);
          }
        }
      }
    }

    if (methodCall.getParent() instanceof PsiExpressionStatement || tailCall == InlineUtil.TailCallType.Return) {
      methodCall.getParent().delete();
    }
    else {
      if (blockData.resultVar != null) {
        PsiExpression expr = myFactory.createExpressionFromText(blockData.resultVar.getName(), null);
        methodCall.replace(expr);
      }
      else {
        //??
      }
    }

    PsiClass thisClass = myMethod.getContainingClass();
    PsiExpression thisAccessExpr;
    if (thisVar != null) {
      if (!canInlineParmOrThisVariable(thisVar)) {
        thisAccessExpr = myFactory.createExpressionFromText(thisVar.getName(), null);
      }
      else {
        thisAccessExpr = thisVar.getInitializer();
      }
    }
    else {
      thisAccessExpr = null;
    }
    ChangeContextUtil.decodeContextInfo(anchorParent, thisClass, thisAccessExpr);

    if (thisVar != null) {
      inlineParmOrThisVariable(thisVar, false);
    }
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    for (int i = 0; i < parmVars.length; i++) {
      final PsiParameter parameter = parameters[i];
      final boolean strictlyFinal = parameter.hasModifierProperty(PsiModifier.FINAL) && isStrictlyFinal(parameter);
      inlineParmOrThisVariable(parmVars[i], strictlyFinal);
    }
    if (resultVar != null) {
      inlineResultVariable(resultVar);
    }

    ChangeContextUtil.clearContextInfo(anchorParent);
  }

  private PsiSubstitutor getCallSubstitutor(PsiMethodCallExpression methodCall) {
    JavaResolveResult resolveResult = methodCall.getMethodExpression().advancedResolve(false);
    LOG.assertTrue(myManager.areElementsEquivalent(resolveResult.getElement(), myMethod));
    if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
      Iterator<PsiTypeParameter> oldTypeParameters = PsiUtil.typeParametersIterator(myMethod);
      Iterator<PsiTypeParameter> newTypeParameters = PsiUtil.typeParametersIterator(myMethodCopy);
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      while (newTypeParameters.hasNext()) {
        final PsiTypeParameter newTypeParameter = newTypeParameters.next();
        final PsiTypeParameter oldTypeParameter = oldTypeParameters.next();
        substitutor = substitutor.put(newTypeParameter, resolveResult.getSubstitutor().substitute(oldTypeParameter));
      }
      return substitutor;
    }

    return PsiSubstitutor.EMPTY;
  }

  private void substituteMethodTypeParams(PsiElement scope, final PsiSubstitutor substitutor) {
    InlineUtil.substituteTypeParams(scope, substitutor, myFactory);
  }

  private boolean isStrictlyFinal(PsiParameter parameter) {
    for (PsiReference reference : ReferencesSearch.search(parameter, GlobalSearchScope.projectScope(myProject), false)) {
      final PsiElement refElement = reference.getElement();
      final PsiElement anonymousClass = PsiTreeUtil.getParentOfType(refElement, PsiAnonymousClass.class);
      if (anonymousClass != null && PsiTreeUtil.isAncestor(myMethod, anonymousClass, true)) {
        return true;
      }
    }
    return false;
  }


  private boolean syncNeeded(final PsiReferenceExpression ref) {
    if (!myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    final PsiMethod containingMethod = Util.getContainingMethod(ref);
    if (containingMethod == null) return true;
    if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return true;
    final PsiClass sourceContainingClass = myMethod.getContainingClass();
    final PsiClass targetContainingClass = containingMethod.getContainingClass();
    return !sourceContainingClass.equals(targetContainingClass);
  }

  private BlockData prepareBlock(PsiReferenceExpression ref,
                                 final PsiSubstitutor callSubstitutor,
                                 final PsiExpressionList argumentList,
                                 final InlineUtil.TailCallType tailCallType)
    throws IncorrectOperationException {
    final PsiCodeBlock block = myMethodCopy.getBody();
    final PsiStatement[] originalStatements = block.getStatements();

    PsiLocalVariable resultVar = null;
    PsiType returnType = callSubstitutor.substitute(myMethod.getReturnType());
    String resultName = null;
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(myMethod, callSubstitutor, argumentList);
    if (returnType != null && returnType != PsiType.VOID && tailCallType == InlineUtil.TailCallType.None) {
      resultName = myJavaCodeStyle.propertyNameToVariableName("result", VariableKind.LOCAL_VARIABLE);
      resultName = myJavaCodeStyle.suggestUniqueVariableName(resultName, block.getFirstChild(), true);
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(resultName, returnType, null);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      resultVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
    }

    PsiParameter[] parms = myMethodCopy.getParameterList().getParameters();
    PsiLocalVariable[] parmVars = new PsiLocalVariable[parms.length];
    for (int i = parms.length - 1; i >= 0; i--) {
      PsiParameter parm = parms[i];
      String parmName = parm.getName();
      String name = parmName;
      name = myJavaCodeStyle.variableNameToPropertyName(name, VariableKind.PARAMETER);
      name = myJavaCodeStyle.propertyNameToVariableName(name, VariableKind.LOCAL_VARIABLE);
      if (!name.equals(parmName)) {
        name = myJavaCodeStyle.suggestUniqueVariableName(name, block.getFirstChild(), true);
      }
      RefactoringUtil.renameVariableReferences(parm, name, new LocalSearchScope(myMethodCopy.getBody()), true);
      PsiType paramType = parm.getType();
      @NonNls String defaultValue;
      if (paramType instanceof PsiEllipsisType) {
        final PsiEllipsisType ellipsisType = (PsiEllipsisType)paramType;
        paramType = ellipsisType.toArrayType();
        if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
          defaultValue = "new " + ellipsisType.getComponentType().getCanonicalText() + "[]{}";
        }
        else {
          defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
        }
      }
      else {
        defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
      }

      PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
      PsiDeclarationStatement declaration =
        myFactory.createVariableDeclarationStatement(name, callSubstitutor.substitute(paramType), initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parmVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiUtil.setModifierProperty(parmVars[i], PsiModifier.FINAL, parm.hasModifierProperty(PsiModifier.FINAL));
    }

    PsiLocalVariable thisVar = null;
    if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = myMethod.getContainingClass();

      if (containingClass != null) {
        PsiType thisType = myFactory.createType(containingClass, callSubstitutor);
        String[] names = myJavaCodeStyle.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, thisType)
          .names;
        String thisVarName = names[0];
        thisVarName = myJavaCodeStyle.suggestUniqueVariableName(thisVarName, block.getFirstChild(), true);
        PsiExpression initializer = myFactory.createExpressionFromText("null", null);
        PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(thisVarName, thisType, initializer);
        declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
        thisVar = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      }
    }

    if (thisVar != null && syncNeeded(ref)) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)myFactory.createStatementFromText("synchronized(" + thisVar.getName() + "){}", block);
      synchronizedStatement = (PsiSynchronizedStatement)CodeStyleManager.getInstance(myProject).reformat(synchronizedStatement);
      synchronizedStatement = (PsiSynchronizedStatement)block.add(synchronizedStatement);
      final PsiCodeBlock synchronizedBody = synchronizedStatement.getBody();
      for (final PsiStatement originalStatement : originalStatements) {
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
    }

    if (resultName != null || tailCallType == InlineUtil.TailCallType.Simple) {
      PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(myMethodCopy);
      for (PsiReturnStatement returnStatement : returnStatements) {
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) continue;
        PsiStatement statement;
        if (tailCallType == InlineUtil.TailCallType.Simple) {
          if (returnValue instanceof PsiCallExpression) {
            PsiExpressionStatement exprStatement = (PsiExpressionStatement) myFactory.createStatementFromText("a;", null);
            exprStatement.getExpression().replace(returnValue);
            returnStatement.getParent().addBefore(exprStatement, returnStatement);
          }
          statement = myFactory.createStatementFromText("return;", null);
        }
        else {
          statement = myFactory.createStatementFromText(resultName + "=0;", null);
          statement = (PsiStatement)myCodeStyleManager.reformat(statement);
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)((PsiExpressionStatement)statement).getExpression();
          assignment.getRExpression().replace(returnValue);
        }
        returnStatement.replace(statement);
      }
    }

    return new BlockData(block, thisVar, parmVars, resultVar);
  }

  private void solveVariableNameConflicts(PsiElement scope, final PsiElement placeToInsert) throws IncorrectOperationException {
    if (scope instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)scope;
      String name = var.getName();
      String oldName = name;
      while (true) {
        String newName = myJavaCodeStyle.suggestUniqueVariableName(name, placeToInsert, true);
        if (newName.equals(name)) break;
        name = newName;
        newName = myJavaCodeStyle.suggestUniqueVariableName(name, var, true);
        if (newName.equals(name)) break;
        name = newName;
      }
      if (!name.equals(oldName)) {
        RefactoringUtil.renameVariableReferences(var, name, new LocalSearchScope(myMethodCopy.getBody()), true);
        var.getNameIdentifier().replace(myFactory.createIdentifier(name));
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      solveVariableNameConflicts(child, placeToInsert);
    }
  }

  private void addParmAndThisVarInitializers(BlockData blockData, PsiMethodCallExpression methodCall) throws IncorrectOperationException {
    PsiExpression[] args = methodCall.getArgumentList().getExpressions();
    if (blockData.parmVars.length > 0) {
      for (int i = 0; i < args.length; i++) {
        int j = Math.min(i, blockData.parmVars.length - 1);
        final PsiExpression initializer = blockData.parmVars[j].getInitializer();
        LOG.assertTrue(initializer != null);
        if (initializer instanceof PsiNewExpression && ((PsiNewExpression)initializer).getArrayInitializer() != null) { //varargs initializer
          final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
          arrayInitializer.add(args[i]);
          continue;
        }

        initializer.replace(args[i]);
      }
    }

    if (blockData.thisVar != null) {
      PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
      if (qualifier == null) {
        PsiElement parent = methodCall.getParent();
        while (true) {
          if (parent instanceof PsiClass) break;
          if (parent instanceof PsiFile) break;
          parent = parent.getParent();
        }
        if (parent instanceof PsiClass) {
          PsiClass parentClass = (PsiClass)parent;
          final PsiClass containingClass = myMethod.getContainingClass();
          if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
            qualifier = myFactory.createExpressionFromText("this", null);
          }
          else {
            if (PsiTreeUtil.isAncestor(containingClass, parent, false)) {
              String name = containingClass.getName();
              if (name != null) {
                qualifier = myFactory.createExpressionFromText(name + ".this", null);
              }
              else { //?
                qualifier = myFactory.createExpressionFromText("this", null);
              }
            } else { // we are inside the inheritor
              do {
                parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
                if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                  LOG.assertTrue(parentClass != null);
                  final String childClassName = parentClass.getName();
                  qualifier = myFactory.createExpressionFromText(childClassName != null ? childClassName + ".this" : "this", null);
                  break;
                }
              }
              while (parentClass != null);
            }
          }
        }
        else {
          qualifier = myFactory.createExpressionFromText("this", null);
        }
      }
      else if (qualifier instanceof PsiSuperExpression) {
        qualifier = myFactory.createExpressionFromText("this", null);
      }
      blockData.thisVar.getInitializer().replace(qualifier);
    }
  }

  private boolean canInlineParmOrThisVariable(PsiLocalVariable variable) {
    boolean isAccessedForWriting = false;
    for (PsiReference ref : ReferencesSearch.search(variable)) {
      PsiElement refElement = ref.getElement();
      if (refElement instanceof PsiExpression) {
        if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          isAccessedForWriting = true;
        }
      }
    }

    PsiExpression initializer = variable.getInitializer();
    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && false;
    return canInlineParmOrThisVariable(initializer, shouldBeFinal, false, ReferencesSearch.search(variable).findAll().size(), isAccessedForWriting);
  }

  private void inlineParmOrThisVariable(PsiLocalVariable variable, boolean strictlyFinal) throws IncorrectOperationException {
    PsiReference firstRef = ReferencesSearch.search(variable).findFirst();

    if (firstRef == null) {
      variable.getParent().delete(); //Q: side effects?
      return;
    }


    boolean isAccessedForWriting = false;
    final Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
    for (PsiReference ref : refs) {
      PsiElement refElement = ref.getElement();
      if (refElement instanceof PsiExpression) {
        if (PsiUtil.isAccessedForWriting((PsiExpression)refElement)) {
          isAccessedForWriting = true;
        }
      }
    }

    PsiExpression initializer = variable.getInitializer();
    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    if (canInlineParmOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting)) {
      if (shouldBeFinal) {
        declareUsedLocalsFinal(initializer, strictlyFinal);
      }
      for (PsiReference ref : refs) {
        final PsiJavaCodeReferenceElement javaRef = (PsiJavaCodeReferenceElement)ref;
        if (initializer instanceof PsiThisExpression && ((PsiThisExpression)initializer).getQualifier() == null) {
          final PsiClass varThisClass = RefactoringUtil.getThisClass(variable);
          if (RefactoringUtil.getThisClass(javaRef) != varThisClass) {
            initializer = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createExpressionFromText(varThisClass.getName() + ".this", variable);
          }
        }

        PsiExpression expr = InlineUtil.inlineVariable(variable, initializer, javaRef);

        InlineUtil.tryToInlineArrayCreationForVarargs(expr);

        //Q: move the following code to some util? (addition to inline?)
        if (expr instanceof PsiThisExpression) {
          if (expr.getParent() instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExpr = (PsiReferenceExpression)expr.getParent();
            PsiElement refElement = refExpr.resolve();
            PsiExpression exprCopy = (PsiExpression)refExpr.copy();
            refExpr = (PsiReferenceExpression)refExpr.replace(myFactory.createExpressionFromText(refExpr.getReferenceName(), null));
            if (refElement != null) {
              PsiElement newRefElement = refExpr.resolve();
              if (!refElement.equals(newRefElement)) {
                // change back
                refExpr.replace(exprCopy);
              }
            }
          }
        }
      }
      variable.getParent().delete();
    }
  }

  private boolean canInlineParmOrThisVariable(PsiExpression initializer,
                                              boolean shouldBeFinal,
                                              boolean strictlyFinal,
                                              int accessCount,
                                              boolean isAccessedForWriting) {
    if (strictlyFinal) {
      class CanAllLocalsBeDeclaredFinal extends JavaRecursiveElementWalkingVisitor {
        boolean success = true;

        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          final PsiElement psiElement = expression.resolve();
          if (psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) {
            if (!RefactoringUtil.canBeDeclaredFinal((PsiVariable)psiElement)) {
              success = false;
            }
          }
        }

        @Override public void visitElement(PsiElement element) {
          if (success) {
            super.visitElement(element);
          }
        }
      }

      final CanAllLocalsBeDeclaredFinal canAllLocalsBeDeclaredFinal = new CanAllLocalsBeDeclaredFinal();
      initializer.accept(canAllLocalsBeDeclaredFinal);
      if (!canAllLocalsBeDeclaredFinal.success) return false;
    }
    if (initializer instanceof PsiReferenceExpression) {
      PsiVariable refVar = (PsiVariable)((PsiReferenceExpression)initializer).resolve();
      if (refVar == null) {
        return !isAccessedForWriting;
      }
      if (refVar instanceof PsiField) {
        if (isAccessedForWriting) return false;
        /*
        PsiField field = (PsiField)refVar;
        if (isFieldNonModifiable(field)){
          return true;
        }
        //TODO: other cases
        return false;
        */
        return true; //TODO: "suspicous" places to review by user!
      }
      else {
        if (isAccessedForWriting) {
          if (refVar.hasModifierProperty(PsiModifier.FINAL) || shouldBeFinal) return false;
          PsiReference[] refs =
            ReferencesSearch.search(refVar, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);
          return refs.length == 1; //TODO: control flow
        }
        else {
          if (shouldBeFinal) {
            return refVar.hasModifierProperty(PsiModifier.FINAL) || RefactoringUtil.canBeDeclaredFinal(refVar);
          }
          return true;
        }
      }
    }
    else if (isAccessedForWriting) {
      return false;
    }
    else if (initializer instanceof PsiCallExpression) {
      if (accessCount > 1) return false;
      if (initializer instanceof PsiNewExpression) {
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        if (arrayInitializer != null) {
          for (PsiExpression expression : arrayInitializer.getInitializers()) {
            if (!canInlineParmOrThisVariable(expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
              return false;
            }
          }
          return true;
        }
      }
      final PsiExpressionList argumentList = ((PsiCallExpression)initializer).getArgumentList();
      if (argumentList == null) return false;
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (PsiExpression expression : expressions) {
        if (!canInlineParmOrThisVariable(expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
          return false;
        }
      }
      return true; //TODO: "suspicous" places to review by user!
    }
    else if (initializer instanceof PsiLiteralExpression) {
      return true;
    }
    else if (initializer instanceof PsiArrayAccessExpression) {
      final PsiExpression arrayExpression = ((PsiArrayAccessExpression)initializer).getArrayExpression();
      final PsiExpression indexExpression = ((PsiArrayAccessExpression)initializer).getIndexExpression();
      return canInlineParmOrThisVariable(arrayExpression, shouldBeFinal, strictlyFinal, accessCount, false) &&
             canInlineParmOrThisVariable(indexExpression, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiParenthesizedExpression) {
      PsiExpression expr = ((PsiParenthesizedExpression)initializer).getExpression();
      return expr == null || canInlineParmOrThisVariable(expr, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      return operand != null && canInlineParmOrThisVariable(operand, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiBinaryExpression) {
      PsiBinaryExpression binExpr = (PsiBinaryExpression)initializer;
      PsiExpression lOperand = binExpr.getLOperand();
      PsiExpression rOperand = binExpr.getROperand();
      return rOperand != null && canInlineParmOrThisVariable(lOperand, shouldBeFinal, strictlyFinal, accessCount, false) &&
             canInlineParmOrThisVariable(rOperand, shouldBeFinal, strictlyFinal, accessCount, false);
    }
    else if (initializer instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    else if (initializer instanceof PsiThisExpression) {
      return true;
    }
    else if (initializer instanceof PsiSuperExpression) {
      return true;
    }
    else {
      return false;
    }
  }

  private static void declareUsedLocalsFinal(PsiElement expr, boolean strictlyFinal) throws IncorrectOperationException {
    if (expr instanceof PsiReferenceExpression) {
      PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        if (strictlyFinal || RefactoringUtil.canBeDeclaredFinal((PsiVariable)refElement)) {
          PsiUtil.setModifierProperty(((PsiVariable)refElement), PsiModifier.FINAL, true);
        }
      }
    }
    PsiElement[] children = expr.getChildren();
    for (PsiElement child : children) {
      declareUsedLocalsFinal(child, strictlyFinal);
    }
  }

  /*
  private boolean isFieldNonModifiable(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.FINAL)){
      return true;
    }
    PsiElement[] refs = myManager.getSearchHelper().findReferences(field, null, false);
    for(int i = 0; i < refs.length; i++){
      PsiReferenceExpression ref = (PsiReferenceExpression)refs[i];
      if (PsiUtil.isAccessedForWriting(ref)) {
        PsiElement container = ref.getParent();
        while(true){
          if (container instanceof PsiMethod ||
            container instanceof PsiField ||
            container instanceof PsiClassInitializer ||
            container instanceof PsiFile) break;
          container = container.getParent();
        }
        if (container instanceof PsiMethod && ((PsiMethod)container).isConstructor()) continue;
        return false;
      }
    }
    return true;
  }
  */

  private void inlineResultVariable(PsiVariable resultVar) throws IncorrectOperationException {
    PsiAssignmentExpression assignment = null;
    PsiReferenceExpression resultUsage = null;
    for (PsiReference ref1 : ReferencesSearch.search(resultVar, GlobalSearchScope.projectScope(myProject), false)) {
      PsiReferenceExpression ref = (PsiReferenceExpression)ref1;
      if (ref.getParent() instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)ref.getParent()).getLExpression().equals(ref)) {
        if (assignment != null) {
          assignment = null;
          break;
        }
        else {
          assignment = (PsiAssignmentExpression)ref.getParent();
        }
      }
      else {
        LOG.assertTrue(resultUsage == null);
        resultUsage = ref;
      }
    }

    if (assignment == null) return;
    boolean condition = assignment.getParent() instanceof PsiExpressionStatement;
    LOG.assertTrue(condition);
    // SCR3175 fixed: inline only if declaration and assignment is in the same code block.
    if (!(assignment.getParent().getParent() == resultVar.getParent().getParent())) return;
    if (resultUsage != null) {
      String name = resultVar.getName();
      PsiDeclarationStatement declaration =
        myFactory.createVariableDeclarationStatement(name, resultVar.getType(), assignment.getRExpression());
      declaration = (PsiDeclarationStatement)assignment.getParent().replace(declaration);
      resultVar.getParent().delete();
      resultVar = (PsiVariable)declaration.getDeclaredElements()[0];

      PsiElement parentStatement = RefactoringUtil.getParentStatement(resultUsage, true);
      PsiElement next = declaration.getNextSibling();
      boolean canInline = false;
      while (true) {
        if (next == null) break;
        if (parentStatement.equals(next)) {
          canInline = true;
          break;
        }
        if (next instanceof PsiStatement) break;
        next = next.getNextSibling();
      }

      if (canInline) {
        InlineUtil.inlineVariable(resultVar, resultVar.getInitializer(), resultUsage);
        declaration.delete();
      }
    }
    else {
      PsiExpression rExpression = assignment.getRExpression();
      while (rExpression instanceof PsiReferenceExpression) rExpression = ((PsiReferenceExpression)rExpression).getQualifierExpression();
      if (rExpression == null || !PsiUtil.isStatement(rExpression)) {
        assignment.delete();
      }
      else {
        assignment.replace(rExpression);
      }
      resultVar.delete();
    }
  }

  private static final Key<String> MARK_KEY = Key.create("");

  private PsiReferenceExpression[] addBracesWhenNeeded(PsiReferenceExpression[] refs) throws IncorrectOperationException {
    ArrayList<PsiReferenceExpression> refsVector = new ArrayList<PsiReferenceExpression>();
    ArrayList<PsiBlockStatement> addedBracesVector = new ArrayList<PsiBlockStatement>();
    myAddedClassInitializers = new HashMap<PsiField, PsiClassInitializer>();

    for (PsiReferenceExpression ref : refs) {
      ref.putCopyableUserData(MARK_KEY, "");
    }

    RefLoop:
    for (PsiReferenceExpression ref : refs) {
      if (!ref.isValid()) continue;

      PsiElement parentStatement = RefactoringUtil.getParentStatement(ref, true);
      if (parentStatement != null) {
        PsiElement parent = ref.getParent();
        while (!parent.equals(parentStatement)) {
          if (parent instanceof PsiStatement && !(parent instanceof PsiDeclarationStatement)) {
            String text = "{\n}";
            PsiBlockStatement blockStatement = (PsiBlockStatement)myFactory.createStatementFromText(text, null);
            blockStatement = (PsiBlockStatement)myCodeStyleManager.reformat(blockStatement);
            blockStatement.getCodeBlock().add(parent);
            blockStatement = (PsiBlockStatement)parent.replace(blockStatement);

            PsiElement newStatement = blockStatement.getCodeBlock().getStatements()[0];
            addMarkedElements(refsVector, newStatement);
            addedBracesVector.add(blockStatement);
            continue RefLoop;
          }
          parent = parent.getParent();
        }
      }
      else {
        final PsiField field = PsiTreeUtil.getParentOfType(ref, PsiField.class);
        if (field != null) {
          if (field instanceof PsiEnumConstant) {
            inlineEnumConstantParameter(refsVector, ref);
            continue;
          }
          field.normalizeDeclaration();
          final PsiExpression initializer = field.getInitializer();
          LOG.assertTrue(initializer != null);
          PsiClassInitializer classInitializer = myFactory.createClassInitializer();
          final PsiClass containingClass = field.getContainingClass();
          classInitializer = (PsiClassInitializer)containingClass.addAfter(classInitializer, field);
          containingClass.addAfter(CodeEditUtil.createLineFeed(field.getManager()), field);
          final PsiCodeBlock body = classInitializer.getBody();
          PsiExpressionStatement statement = (PsiExpressionStatement)myFactory.createStatementFromText(field.getName() + " = 0;", body);
          statement = (PsiExpressionStatement)body.add(statement);
          final PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
          assignment.getLExpression().replace(RenameJavaVariableProcessor.createMemberReference(field, assignment));
          assignment.getRExpression().replace(initializer);
          addMarkedElements(refsVector, statement);
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            PsiUtil.setModifierProperty(classInitializer, PsiModifier.STATIC, true);
          }
          myAddedClassInitializers.put(field, classInitializer);
          continue;
        }
      }

      refsVector.add(ref);
    }

    for (PsiReferenceExpression ref : refs) {
      ref.putCopyableUserData(MARK_KEY, null);
    }

    myAddedBraces = addedBracesVector.toArray(new PsiBlockStatement[addedBracesVector.size()]);
    return refsVector.toArray(new PsiReferenceExpression[refsVector.size()]);
  }

  private void inlineEnumConstantParameter(final List<PsiReferenceExpression> refsVector,
                                           final PsiReferenceExpression ref) throws IncorrectOperationException {
    PsiExpression expr = getSimpleReturnedExpression(myMethod);
    if (expr != null) {
      refsVector.add(ref);
    }
    else {
      PsiCall call = PsiTreeUtil.getParentOfType(ref, PsiCall.class);
      @NonNls String text = "new Object() { " + myMethod.getReturnTypeElement().getText() + " evaluate() { return " + call.getText() + ";}}.evaluate";
      PsiExpression callExpr = JavaPsiFacade.getInstance(myProject).getParserFacade().createExpressionFromText(text, call);
      PsiElement classExpr = ref.replace(callExpr);
      classExpr.accept(new JavaRecursiveElementWalkingVisitor() {
        public void visitReturnStatement(final PsiReturnStatement statement) {
          super.visitReturnStatement(statement);
          PsiExpression expr = statement.getReturnValue();
          if (expr instanceof PsiMethodCallExpression) {
            refsVector.add(((PsiMethodCallExpression) expr).getMethodExpression());
          }
        }
      });
      if (classExpr.getParent() instanceof PsiMethodCallExpression) {
        PsiExpressionList args = ((PsiMethodCallExpression)classExpr.getParent()).getArgumentList();
        PsiExpression[] argExpressions = args.getExpressions();
        if (argExpressions.length > 0) {
          args.deleteChildRange(argExpressions [0], argExpressions [argExpressions.length-1]);
        }
      }
    }
  }

  @Nullable
  private static PsiExpression getSimpleReturnedExpression(final PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return null;
    PsiStatement[] psiStatements = body.getStatements();
    if (psiStatements.length != 1) return null;
    PsiStatement statement = psiStatements[0];
    if (!(statement instanceof PsiReturnStatement)) return null;
    return ((PsiReturnStatement) statement).getReturnValue();
  }

  private static void addMarkedElements(final List<PsiReferenceExpression> array, PsiElement scope) {
    scope.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override public void visitElement(PsiElement element) {
        if (element.getCopyableUserData(MARK_KEY) != null) {
          array.add((PsiReferenceExpression)element);
          element.putCopyableUserData(MARK_KEY, null);
        }
        super.visitElement(element);
      }
    });
  }

  private void removeAddedBracesWhenPossible() throws IncorrectOperationException {
    if (myAddedBraces == null) return;

    for (PsiBlockStatement blockStatement : myAddedBraces) {
      PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length == 1) {
        blockStatement.replace(statements[0]);
      }
    }

    final Set<PsiField> fields = myAddedClassInitializers.keySet();

    for (PsiField psiField : fields) {
      final PsiClassInitializer classInitializer = myAddedClassInitializers.get(psiField);
      final PsiExpression initializer = getSimpleFieldInitializer(psiField, classInitializer);
      if (initializer != null) {
        psiField.getInitializer().replace(initializer);
        classInitializer.delete();
      }
      else {
        psiField.getInitializer().delete();
      }
    }
  }

  @Nullable
  private PsiExpression getSimpleFieldInitializer(PsiField field, PsiClassInitializer initializer) {
    final PsiStatement[] statements = initializer.getBody().getStatements();
    if (statements.length != 1) return null;
    if (!(statements[0] instanceof PsiExpressionStatement)) return null;
    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiAssignmentExpression)) return null;
    final PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
    if (!(lExpression instanceof PsiReferenceExpression)) return null;
    final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
    if (!myManager.areElementsEquivalent(field, resolved)) return null;
    return ((PsiAssignmentExpression)expression).getRExpression();
  }

  public static boolean checkBadReturns(PsiMethod method) {
    PsiReturnStatement[] returns = RefactoringUtil.findReturnStatements(method);
    if (returns.length == 0) return false;
    PsiCodeBlock body = method.getBody();
    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Control flow:");
      LOG.debug(controlFlow.toString());
    }

    List<Instruction> instructions = new ArrayList<Instruction>(controlFlow.getInstructions());

    // temporary replace all return's with empty statements in the flow
    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getStartOffset(aReturn);
      int endOffset = controlFlow.getEndOffset(aReturn);
      while (offset <= endOffset && !(instructions.get(offset) instanceof GoToInstruction)) {
        offset++;
      }
      LOG.assertTrue(instructions.get(offset) instanceof GoToInstruction);
      instructions.set(offset, EmptyInstruction.INSTANCE);
    }

    for (PsiReturnStatement aReturn : returns) {
      int offset = controlFlow.getEndOffset(aReturn);
      while (true) {
        if (offset == instructions.size()) break;
        Instruction instruction = instructions.get(offset);
        if (instruction instanceof GoToInstruction) {
          offset = ((GoToInstruction)instruction).offset;
        }
        else if (instruction instanceof ThrowToInstruction) {
          offset = ((ThrowToInstruction)instruction).offset;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          // In case of "conditional throw to", control flow will not be altered
          // If exception handler is in method, we will inline it to ivokation site
          // If exception handler is at invocation site, execution will continue to get there
          offset++;
        }
        else {
          return true;
        }
      }
    }

    return false;
  }

  private static class BlockData {
    final PsiCodeBlock block;
    final PsiLocalVariable thisVar;
    final PsiLocalVariable[] parmVars;
    final PsiLocalVariable resultVar;

    public BlockData(PsiCodeBlock block, PsiLocalVariable thisVar, PsiLocalVariable[] parmVars, PsiLocalVariable resultVar) {
      this.block = block;
      this.thisVar = thisVar;
      this.parmVars = parmVars;
      this.resultVar = resultVar;
    }
  }

  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.singletonList(myReference);
    }
    else {
      return myReference == null ? Collections.singletonList(myMethod) : Arrays.asList(myReference, myMethod);
    }
  }
}

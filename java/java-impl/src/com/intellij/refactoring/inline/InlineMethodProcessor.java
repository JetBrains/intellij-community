// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
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
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.rename.RenameJavaMemberProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.tryCast;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineMethodProcessor");

  private PsiMethod myMethod;
  private PsiJavaCodeReferenceElement myReference;
  private final Editor myEditor;
  private final boolean myInlineThisOnly;
  private final boolean mySearchInComments;
  private final boolean mySearchForTextOccurrences;
  private final boolean myDeleteTheDeclaration;
  private final Function<PsiReference, InlineTransformer> myTransformerChooser;

  private final PsiManager myManager;
  private final PsiElementFactory myFactory;
  private final CodeStyleManager myCodeStyleManager;
  private final JavaCodeStyleManager myJavaCodeStyle;

  private PsiCodeBlock[] myAddedBraces;
  private final String myDescriptiveName;
  private Map<PsiField, PsiClassInitializer> myAddedClassInitializers;
  private PsiMethod myMethodCopy;
  private Map<Language,InlineHandler.Inliner> myInliners;

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiJavaCodeReferenceElement reference,
                               Editor editor,
                               boolean isInlineThisOnly) {
    this(project, method, reference, editor, isInlineThisOnly, false, false, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                             @NotNull PsiMethod method,
                             @Nullable PsiJavaCodeReferenceElement reference,
                             Editor editor,
                             boolean isInlineThisOnly,
                             boolean searchInComments,
                             boolean searchForTextOccurrences) {
    this(project, method, reference, editor, isInlineThisOnly, searchInComments, searchForTextOccurrences, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiJavaCodeReferenceElement reference,
                               Editor editor,
                               boolean isInlineThisOnly,
                               boolean searchInComments,
                               boolean searchForTextOccurrences,
                               boolean isDeleteTheDeclaration) {
    super(project);
    myMethod = InlineMethodSpecialization.specialize(method, reference);
    myTransformerChooser = InlineTransformer.getSuitableTransformer(myMethod);
    myReference = reference;
    myEditor = editor;
    myInlineThisOnly = isInlineThisOnly;
    mySearchInComments = searchInComments;
    mySearchForTextOccurrences = searchForTextOccurrences;
    myDeleteTheDeclaration = isDeleteTheDeclaration;

    myManager = PsiManager.getInstance(myProject);
    myFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    myJavaCodeStyle = JavaCodeStyleManager.getInstance(myProject);
    myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myMethod);
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return RefactoringBundle.message("inline.method.command", myDescriptiveName);
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @Override
  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myReference)};
    Set<UsageInfo> usages = new HashSet<>();
    if (myReference != null) {
      usages.add(new UsageInfo(myReference));
    }
    for (PsiReference reference : ReferencesSearch.search(myMethod, myRefactoringScope)) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    OverridingMethodsSearch.search(myMethod, myRefactoringScope, false).forEach(method -> {
      if (AnnotationUtil.isAnnotated(method, Override.class.getName(), 0)) {
        usages.add(new UsageInfo(method));
      }
      return true;
    });

    if (mySearchInComments || mySearchForTextOccurrences) {
      final NonCodeUsageInfoFactory infoFactory = new NonCodeUsageInfoFactory(myMethod, myMethod.getName()) {
        @Override
        public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
          if (PsiTreeUtil.isAncestor(myMethod, usage, false)) return null;
          return super.createUsageInfo(usage, startOffset, endOffset);
        }
      };
      if (mySearchInComments) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(myMethod, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
        TextOccurrencesUtil.addUsagesInStringsAndComments(myMethod, myRefactoringScope, stringToSearch, usages, infoFactory);
      }

      if (mySearchForTextOccurrences && myRefactoringScope instanceof GlobalSearchScope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(myMethod, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(myMethod, stringToSearch, (GlobalSearchScope)myRefactoringScope, usages, infoFactory);
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected boolean isPreviewUsages(@NotNull UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) return true;
    }
    return super.isPreviewUsages(usages);
  }

  @Override
  protected void refreshElements(@NotNull PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myMethod = (PsiMethod)elements[0];
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (!myInlineThisOnly && checkReadOnly()) {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myMethod)) return false;
    }
    final UsageInfo[] usagesIn = refUsages.get();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    if (!myInlineThisOnly) {
      final PsiMethod[] superMethods = myMethod.findSuperMethods();
      for (PsiMethod method : superMethods) {
        String className = Objects.requireNonNull(method.getContainingClass()).getQualifiedName();
        final String message = method.hasModifierProperty(PsiModifier.ABSTRACT) ?
                               RefactoringBundle.message("inlined.method.implements.method.from.0", className) :
                               RefactoringBundle.message("inlined.method.overrides.method.from.0", className);
        conflicts.putValue(method, message);
      }

      for (UsageInfo info : usagesIn) {
        final PsiElement element = info.getElement();
        if (element instanceof PsiDocMethodOrFieldRef && !PsiTreeUtil.isAncestor(myMethod, element, false)) {
          conflicts.putValue(element, "Inlined method is used in javadoc");
        }
        if (element instanceof PsiLiteralExpression &&
            Stream.of(element.getReferences()).anyMatch(JavaLangClassMemberReference.class::isInstance)) {
          conflicts.putValue(element, "Inlined method is used reflectively");
        }
        if (element instanceof PsiMethodReferenceExpression) {
          final PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)element).getQualifierExpression();
          if (qualifierExpression != null) {
            final List<PsiElement> sideEffects = new ArrayList<>();
            SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
            if (!sideEffects.isEmpty()) {
              conflicts.putValue(element, "Inlined method is used in method reference with side effects in qualifier");
            }
          }
        }
        if (element instanceof PsiReferenceExpression && myTransformerChooser.apply((PsiReference)element).isFallBackTransformer()) {
          conflicts.putValue(element, RefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
        }

        final String errorMessage = checkUnableToInsertCodeBlock(myMethod.getBody(), element);
        if (errorMessage != null) {
          conflicts.putValue(element, errorMessage);
        }
      }
    }
    else if (myReference != null && myTransformerChooser.apply(myReference).isFallBackTransformer()) {
      conflicts.putValue(myReference, RefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
    }

    myInliners = GenericInlineHandler.initInliners(myMethod, usagesIn, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return myInlineThisOnly;
      }
    }, conflicts, JavaLanguage.INSTANCE);

    addInaccessibleMemberConflicts(myMethod, usagesIn, new ReferencedElementsCollector(), conflicts);

    addInaccessibleSuperCallsConflicts(usagesIn, conflicts);

    return showConflicts(conflicts, usagesIn);
  }


  private boolean checkReadOnly() {
    return myMethod.isWritable() || myMethod instanceof PsiCompiledElement;
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
          final Set<PsiClass> targetContainingClasses = new HashSet<>();
          PsiElement qualifiedCall = null;
          for (UsageInfo info : usagesIn) {
            final PsiElement element = info.getElement();
            if (element != null) {
              final PsiClass targetContainingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
              if (targetContainingClass != null &&
                  (!InheritanceUtil.isInheritorOrSelf(targetContainingClass, superClass, true) ||
                   PsiUtil.getEnclosingStaticElement(element, targetContainingClass) != null)) {
                targetContainingClasses.add(targetContainingClass);
              }
              else if (element instanceof PsiReferenceExpression && !ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)element)) {
                qualifiedCall = ((PsiReferenceExpression)element).getQualifierExpression();
              }
            }
          }
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
          LOG.assertTrue(methodCallExpression != null);
          if (!targetContainingClasses.isEmpty()) {
            conflicts.putValue(expression, "Inlined method calls " + methodCallExpression.getText() + " which won't be accessed in " +
                                           StringUtil.join(targetContainingClasses, psiClass -> RefactoringUIUtil.getDescription(psiClass, false), ","));
          }

          if (qualifiedCall != null) {
            conflicts.putValue(expression, "Inlined method calls " + methodCallExpression.getText() + " which won't be accessible on qualifier "
                                           + qualifiedCall.getText());
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
    final Map<PsiMember, Set<PsiMember>> containersToReferenced = getInaccessible(collector.myReferencedMembers, usages, element);

    containersToReferenced.forEach((container, referencedInaccessible) -> {
      for (PsiMember referenced : referencedInaccessible) {
        final String referencedDescription = RefactoringUIUtil.getDescription(referenced, true);
        final String containerDescription = RefactoringUIUtil.getDescription(container, true);
        String message = RefactoringBundle.message("0.that.is.used.in.inlined.method.is.not.accessible.from.call.site.s.in.1",
                                                   referencedDescription, containerDescription);
        conflicts.putValue(container, CommonRefactoringUtil.capitalize(message));
      }
    });
  }

  /**
   * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
   * to subsets of referencedElements that are not accessible from that container
   *
   * @param referencedElements
   * @param usages
   * @param elementToInline
   */
  private static Map<PsiMember, Set<PsiMember>> getInaccessible(HashSet<? extends PsiMember> referencedElements,
                                                                UsageInfo[] usages,
                                                                PsiElement elementToInline) {
    final Map<PsiMember, Set<PsiMember>> result = new HashMap<>();
    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(elementToInline.getProject()).getResolveHelper();
    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement == null) continue;
      final PsiElement container = ConflictsUtil.getContainer(usageElement);
      if (!(container instanceof PsiMember)) continue;    // usage in import statement
      PsiMember memberContainer = (PsiMember)container;
      Set<PsiMember> inaccessibleReferenced = result.get(memberContainer);
      if (inaccessibleReferenced == null) {
        inaccessibleReferenced = new HashSet<>();
        result.put(memberContainer, inaccessibleReferenced);
        for (PsiMember member : referencedElements) {
          if (PsiTreeUtil.isAncestor(elementToInline, member, false)) continue;
          if (elementToInline instanceof PsiClass &&
              InheritanceUtil.isInheritorOrSelf((PsiClass)elementToInline, member.getContainingClass(), true)) continue;
          PsiElement resolveScope = usageElement instanceof PsiReferenceExpression
                                    ? ((PsiReferenceExpression)usageElement).advancedResolve(false).getCurrentFileResolveScope()
                                    : null;
          if (!resolveHelper.isAccessible(member, member.getModifierList(), usageElement, null, resolveScope)) {
            inaccessibleReferenced.add(member);
          }
        }
      }
    }

    return result;
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    RangeMarker position = null;
    if (myEditor != null) {
      final int offset = myEditor.getCaretModel().getOffset();
      position = myEditor.getDocument().createRangeMarker(offset, offset + 1);
    }

    LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
    try {
      doRefactoring(usages);
    }
    finally {
      a.finish();
    }

    if (position != null) {
      if (position.isValid()) {
        myEditor.getCaretModel().moveToOffset(position.getStartOffset());
      }
      position.dispose();
    }
  }

  @Nullable
  @Override
  protected String getRefactoringId() {
    return "refactoring.inline.method";
  }

  @Nullable
  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(myMethod);
    return data;
  }

  private void doRefactoring(UsageInfo[] usages) {
    try {
      if (myInlineThisOnly) {
        if (myMethod.isConstructor() && InlineUtil.isChainingConstructor(myMethod)) {
          if (myReference instanceof PsiMethodReferenceExpression) {
            inlineMethodReference((PsiMethodReferenceExpression)myReference);
          }
          else {
            PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(myReference);
            if (constructorCall != null) {
              inlineConstructorCall(constructorCall);
            }
          }
        }
        else {
          myReference = addBracesWhenNeeded(new PsiReferenceExpression[]{(PsiReferenceExpression)myReference})[0];
          if (myReference instanceof PsiMethodReferenceExpression) {
            inlineMethodReference((PsiMethodReferenceExpression)myReference);
          }
          else {
            inlineMethodCall((PsiReferenceExpression)myReference);
          }
        }
      }
      else {
        CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
        if (myMethod.isConstructor()) {
          for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element instanceof PsiMethodReferenceExpression) {
              inlineMethodReference((PsiMethodReferenceExpression)element);
            }
            else if (element instanceof PsiJavaCodeReferenceElement) {
              PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)element);
              if (constructorCall != null) {
                inlineConstructorCall(constructorCall);
              }
            }
            else if (element instanceof PsiEnumConstant) {
              inlineConstructorCall((PsiEnumConstant) element);
            }
            else if (!(element instanceof PsiDocMethodOrFieldRef)){
              GenericInlineHandler.inlineReference(usage, myMethod, myInliners);
            }
          }
        }
        else {
          List<PsiReferenceExpression> refExprList = new ArrayList<>();
          final List<PsiElement> imports2Delete = new ArrayList<>();
          for (final UsageInfo usage : usages) {
            final PsiElement element = usage.getElement();
            if (element instanceof PsiReferenceExpression) {
              refExprList.add((PsiReferenceExpression)element);
            }
            else if (element instanceof PsiImportStaticReferenceElement) {
              final JavaResolveResult[] resolveResults = ((PsiImportStaticReferenceElement)element).multiResolve(false);
              if (resolveResults.length < 2) {
                //no overloads available: ensure broken import are deleted and
                //unused overloaded imports are deleted by optimize imports helper
                imports2Delete.add(PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class));
              }
            }
            else if (element instanceof PsiMethod) {
              PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiMethod) element, false, Override.class.getName());
              if (annotation != null) {
                annotation.delete();
              }
            }
            else if (JavaLanguage.INSTANCE != element.getLanguage()) {
              GenericInlineHandler.inlineReference(usage, myMethod, myInliners);
            }
          }
          PsiReferenceExpression[] refs = refExprList.toArray(new PsiReferenceExpression[0]);
          refs = addBracesWhenNeeded(refs);
          for (PsiReferenceExpression ref : refs) {
            if (ref instanceof PsiMethodReferenceExpression) {
              inlineMethodReference((PsiMethodReferenceExpression)ref);
            }
            else {
              inlineMethodCall(ref);
            }
          }
          for (PsiElement psiElement : imports2Delete) {
            if (psiElement != null && psiElement.isValid()) {
              psiElement.delete();
            }
          }
        }
        if (myMethod.isWritable() && myDeleteTheDeclaration) myMethod.delete();
      }
      removeAddedBracesWhenPossible();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void inlineMethodReference(PsiMethodReferenceExpression reference) {
    final PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(reference, false, false);
    if (lambdaExpression == null) return;
    final PsiExpression callExpression = LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody());
    if (callExpression instanceof PsiMethodCallExpression) {
      inlineMethodCall(((PsiMethodCallExpression)callExpression).getMethodExpression());
    }
    else if (callExpression instanceof PsiCall) {
      inlineConstructorCall((PsiCall)callExpression);
    }
    else {
      LOG.error("Unexpected expr: " + callExpression.getText());
    }
    LambdaRefactoringUtil.simplifyToExpressionLambda(lambdaExpression);

    if (myInlineThisOnly) {
      LambdaRefactoringUtil.removeSideEffectsFromLambdaBody(myEditor, lambdaExpression);
    }
  }

  public static void inlineConstructorCall(PsiCall constructorCall) {
    PsiMethod oldConstructor = constructorCall.resolveMethod();
    LOG.assertTrue(oldConstructor != null);
    oldConstructor = (PsiMethod)oldConstructor.getNavigationElement();
    PsiExpression[] instanceCreationArguments = constructorCall.getArgumentList().getExpressions();
    if (oldConstructor.isVarArgs()) { //wrap with explicit array
      final PsiParameter[] parameters = oldConstructor.getParameterList().getParameters();
      final PsiType varargType = parameters[parameters.length - 1].getType();
      if (varargType instanceof PsiEllipsisType) {
        final PsiType arrayType =
          constructorCall.resolveMethodGenerics().getSubstitutor().substitute(((PsiEllipsisType)varargType).getComponentType());
        final PsiExpression[] exprs = new PsiExpression[parameters.length];
        System.arraycopy(instanceCreationArguments, 0, exprs, 0, parameters.length - 1);
        StringBuilder varargs = new StringBuilder();
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

  public void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
    ChangeContextUtil.encodeContextInfo(myMethod, false);
    myMethodCopy = (PsiMethod)myMethod.copy();
    ChangeContextUtil.clearContextInfo(myMethod);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    PsiSubstitutor callSubstitutor = getCallSubstitutor(methodCall);
    BlockData blockData = prepareBlock(ref, callSubstitutor, methodCall.getArgumentList());
    InlineUtil.solveVariableNameConflicts(blockData.block, ref, myMethodCopy.getBody());
    addParmAndThisVarInitializers(blockData, methodCall);

    PsiElement anchor = RefactoringUtil.getParentStatement(methodCall, true);
    if (anchor == null) {
      PsiEnumConstant enumConstant = PsiTreeUtil.getParentOfType(methodCall, PsiEnumConstant.class);
      if (enumConstant != null) {
        PsiExpression returnExpr = getSimpleReturnedExpression(myMethod);
        if (returnExpr != null) {
          ChangeContextUtil.encodeContextInfo(returnExpr, true);
          PsiElement copy = returnExpr.copy();
          ChangeContextUtil.clearContextInfo(returnExpr);
          if (copy instanceof PsiReferenceExpression && ((PsiReferenceExpression)copy).getQualifierExpression() == null) {
            copy = inlineParameterReference((PsiReferenceExpression)copy, blockData);
          } else {
            copy.accept(new JavaRecursiveElementVisitor() {
              @Override
              public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                inlineParameterReference(expression, blockData);
              }
            });
          }
          PsiElement replace = methodCall.replace(copy);
          if (blockData.thisVar != null) {
            ChangeContextUtil.decodeContextInfo(replace, myMethod.getContainingClass(), blockData.thisVar.getInitializer());
          }
        }
      }
      return;
    }
    PsiElement anchorParent = anchor.getParent();
    PsiLocalVariable thisVar = null;
    PsiLocalVariable[] parmVars = new PsiLocalVariable[blockData.parmVars.length];
    PsiLocalVariable resultVar = null;
    PsiStatement[] statements = blockData.block.getStatements();
    PsiElement firstBodyElement = blockData.block.getFirstBodyElement();
    if (firstBodyElement instanceof PsiWhiteSpace) firstBodyElement = PsiTreeUtil.skipWhitespacesForward(firstBodyElement);
    PsiElement firstAdded = null;
    if (firstBodyElement != null && firstBodyElement != blockData.block.getRBrace()) {
      int last = statements.length - 1;

      final PsiElement rBraceOrReturnStatement =
        last >= 0 ? PsiTreeUtil.skipWhitespacesAndCommentsForward(statements[last]) : blockData.block.getLastBodyElement();
      LOG.assertTrue(rBraceOrReturnStatement != null);
      final PsiElement beforeRBraceStatement = rBraceOrReturnStatement.getPrevSibling();
      LOG.assertTrue(beforeRBraceStatement != null);

      firstAdded = anchorParent.addRangeBefore(firstBodyElement, beforeRBraceStatement, anchor);

      for (PsiElement e = firstAdded; e != anchor; e = e.getNextSibling()) {
        if (e instanceof PsiDeclarationStatement) {
          PsiElement[] elements = ((PsiDeclarationStatement)e).getDeclaredElements();
          PsiLocalVariable var = tryCast(ArrayUtil.getFirstElement(elements), PsiLocalVariable.class);
          if (var != null) {
            String name = var.getName();
            LOG.assertTrue(name != null);
            if (blockData.resultVar != null && name.equals(blockData.resultVar.getName())) {
              resultVar = var;
            }
            else if (blockData.thisVar != null && name.equals(blockData.thisVar.getName())) {
              thisVar = var;
            } else {
              for (int i = 0; i < blockData.parmVars.length; i++) {
                if (name.equals(blockData.parmVars[i].getName())) {
                  parmVars[i] = var;
                  break;
                }
              }
            }
          }
        }
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

    PsiReferenceExpression resultUsage = null;
    if (blockData.resultVar != null) {
      PsiExpression expr = myFactory.createExpressionFromText(Objects.requireNonNull(blockData.resultVar.getName()), null);
      resultUsage = (PsiReferenceExpression)new CommentTracker().replaceAndRestoreComments(methodCall, expr);
    }
    else {
      // If return var is not specified, we trust that InlineTransformer fully processed the original anchor statement,
      // and we can delete it.
      CommentTracker tracker = new CommentTracker();
      if (firstAdded != null) {
        tracker.delete(anchor);
        tracker.insertCommentsBefore(firstAdded);
      } else {
        tracker.deleteAndRestoreComments(anchor);
      }
    }

    if (thisVar != null) {
      inlineParmOrThisVariable(thisVar, false);
    }
    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    for (int i = 0; i < parmVars.length; i++) {
      final PsiParameter parameter = parameters[i];
      final boolean strictlyFinal = parameter.hasModifierProperty(PsiModifier.FINAL) && isStrictlyFinal(parameter);
      inlineParmOrThisVariable(parmVars[i], strictlyFinal);
    }
    if (resultVar != null && resultUsage != null) {
      inlineResultVariable(resultVar, resultUsage);
    }

    ChangeContextUtil.clearContextInfo(anchorParent);
  }

  @NotNull
  private PsiExpression inlineParameterReference(@NotNull PsiReferenceExpression expression, BlockData blockData) {
    if (expression.getQualifierExpression() != null) return expression;
    PsiElement resolve = expression.resolve();
    if (!(resolve instanceof PsiParameter)) return expression;
    int paramIdx = ArrayUtil.find(myMethod.getParameterList().getParameters(), resolve);
    if (paramIdx < 0) return expression;
    PsiExpression initializer = blockData.parmVars[paramIdx].getInitializer();
    if (initializer == null) return expression;
    return inlineInitializer((PsiVariable)resolve, initializer, expression);
  }

  private PsiSubstitutor getCallSubstitutor(PsiMethodCallExpression methodCall) {
    JavaResolveResult resolveResult = methodCall.getMethodExpression().advancedResolve(false);
    if (myMethod.isPhysical()) {
      // Could be specialized
      LOG.assertTrue(myManager.areElementsEquivalent(resolveResult.getElement(), myMethod));
    }
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
    for (PsiReference reference : ReferencesSearch.search(parameter, myRefactoringScope, false)) {
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

  private BlockData prepareBlock(PsiReferenceExpression ref, PsiSubstitutor callSubstitutor, PsiExpressionList argumentList)
    throws IncorrectOperationException {
    final PsiCodeBlock block = Objects.requireNonNull(myMethodCopy.getBody());
    if (callSubstitutor != PsiSubstitutor.EMPTY) {
      substituteMethodTypeParams(block, callSubstitutor);
    }
    final PsiStatement[] originalStatements = block.getStatements();

    PsiType returnType = callSubstitutor.substitute(myMethod.getReturnType());
    InlineTransformer transformer = myTransformerChooser.apply(ref);

    PsiLocalVariable[] parmVars = declareParameters(block, argumentList, callSubstitutor);

    PsiLocalVariable thisVar = declareThis(callSubstitutor, block);

    addSynchronization(ref, block, originalStatements, thisVar);

    PsiLocalVariable resultVar = transformer.transformBody(myMethodCopy, ref, returnType);

    return new BlockData(block, thisVar, parmVars, resultVar);
  }

  @NotNull
  private PsiLocalVariable[] declareParameters(PsiCodeBlock block, PsiExpressionList argumentList, PsiSubstitutor callSubstitutor) {
    final int applicabilityLevel = PsiUtil.getApplicabilityLevel(myMethod, callSubstitutor, argumentList);
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
        paramType = callSubstitutor.substitute(ellipsisType.toArrayType());
        if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
          PsiType componentType = ((PsiArrayType)paramType).getComponentType();
          defaultValue = "new " + ObjectUtils.notNull(TypeConversionUtil.erasure(componentType), componentType).getCanonicalText() + "[]{}";
        }
        else {
          defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
        }
      }
      else {
        defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
      }

      PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
      PsiType varType = GenericsUtil.getVariableTypeByExpressionType(callSubstitutor.substitute(paramType));
      PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(name, varType, initializer);
      declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
      parmVars[i] = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiUtil.setModifierProperty(parmVars[i], PsiModifier.FINAL, parm.hasModifierProperty(PsiModifier.FINAL));
    }
    return parmVars;
  }

  @Nullable
  private PsiLocalVariable declareThis(PsiSubstitutor callSubstitutor, PsiCodeBlock block) {
    PsiClass containingClass = myMethod.getContainingClass();
    if (myMethod.hasModifierProperty(PsiModifier.STATIC) || containingClass == null) return null;
    PsiType thisType = GenericsUtil.getVariableTypeByExpressionType(myFactory.createType(containingClass, callSubstitutor));
    String[] names = myJavaCodeStyle.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, thisType).names;
    String thisVarName = names[0];
    thisVarName = myJavaCodeStyle.suggestUniqueVariableName(thisVarName, myMethod.getFirstChild(), true);
    PsiExpression initializer = myFactory.createExpressionFromText("null", null);
    PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(thisVarName, thisType, initializer);
    declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
    return (PsiLocalVariable)declaration.getDeclaredElements()[0];
  }

  private void addSynchronization(PsiReferenceExpression ref,
                                  PsiCodeBlock block,
                                  PsiStatement[] originalStatements,
                                  PsiLocalVariable thisVar) {
    PsiClass containingClass = myMethod.getContainingClass();
    String lockName = null;
    if (thisVar != null) {
      lockName = thisVar.getName();
    }
    else if (myMethod.hasModifierProperty(PsiModifier.STATIC) && containingClass != null ) {
      lockName = containingClass.getQualifiedName() + ".class";
    }

    if (lockName != null && syncNeeded(ref)) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)myFactory.createStatementFromText("synchronized(" + lockName + "){}", block);
      synchronizedStatement = (PsiSynchronizedStatement)CodeStyleManager.getInstance(myProject).reformat(synchronizedStatement);
      synchronizedStatement = (PsiSynchronizedStatement)block.add(synchronizedStatement);
      final PsiCodeBlock synchronizedBody = Objects.requireNonNull(synchronizedStatement.getBody());
      for (final PsiStatement originalStatement : originalStatements) {
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
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
        PsiElement parent = methodCall.getContext();
        while (true) {
          if (parent instanceof PsiClass) break;
          if (parent instanceof PsiFile) break;
          assert parent != null : methodCall;
          parent = parent.getContext();
        }
        if (parent instanceof PsiClass) {
          PsiClass parentClass = (PsiClass)parent;
          final PsiClass containingClass = myMethod.getContainingClass();
          if (containingClass != null && parentClass.isInheritor(containingClass, true)) {
            qualifier = myFactory.createExpressionFromText(parentClass.getName() + ".this", null);
          }
          else if (containingClass != null && parentClass.equals(containingClass)) {
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

    PsiExpression initializer = variable.getInitializer();
    if (firstRef == null) {
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      if (initializer != null) {
        List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
        for (PsiStatement statement : StatementExtractor.generateStatements(sideEffects, initializer)) {
          declaration.getParent().addBefore(statement, declaration);
        }
      }
      declaration.delete();
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

    boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
    if (canInlineParmOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting)) {
      if (shouldBeFinal) {
        declareUsedLocalsFinal(initializer, strictlyFinal);
      }
      for (PsiReference ref : refs) {
        initializer = inlineInitializer(variable, initializer, (PsiJavaCodeReferenceElement)ref);
      }
      variable.getParent().delete();
    }
  }

  private PsiExpression inlineInitializer(PsiVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref) {
    if (initializer instanceof PsiThisExpression && ((PsiThisExpression)initializer).getQualifier() == null) {
      final PsiClass varThisClass = RefactoringChangeUtil.getThisClass(variable);
      if (RefactoringChangeUtil.getThisClass(ref) != varThisClass) {
        initializer = JavaPsiFacade.getElementFactory(myManager.getProject()).createExpressionFromText(varThisClass.getName() + ".this", variable);
      }
    }

    PsiExpression expr = InlineUtil.inlineVariable(variable, initializer, ref);

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

    if (expr instanceof PsiLiteralExpression && PsiType.BOOLEAN.equals(expr.getType())) {
      Boolean value = tryCast(((PsiLiteralExpression)expr).getValue(), Boolean.class);
      if (value != null) {
        SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expr, value);
        if (fix.isAvailable()) {
          fix.invoke(myProject, expr.getContainingFile(), expr, expr);
        }
      }
    }
    return initializer;
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
    if (initializer instanceof PsiFunctionalExpression) return accessCount <= 1;
    if (initializer instanceof PsiReferenceExpression) {
      PsiVariable refVar = (PsiVariable)((PsiReferenceExpression)initializer).resolve();
      if (refVar == null) {
        return !isAccessedForWriting;
      }
      if (refVar instanceof PsiField) {
        if (isAccessedForWriting) return false;
        if (refVar.hasModifierProperty(PsiModifier.VOLATILE)) return accessCount <= 1;
        /*
        PsiField field = (PsiField)refVar;
        if (isFieldNonModifiable(field)){
          return true;
        }
        //TODO: other cases
        return false;
        */
        return true; //TODO: "suspicious" places to review by user!
      }
      else {
        if (isAccessedForWriting) {
          if (refVar.hasModifierProperty(PsiModifier.FINAL) || shouldBeFinal) return false;
          PsiReference[] refs = ReferencesSearch.search(refVar, myRefactoringScope, false).toArray(PsiReference.EMPTY_ARRAY);
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
      if (accessCount != 1) return false;//don't allow deleting probable side effects or multiply those side effects
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
      return true; //TODO: "suspicious" places to review by user!
    }
    else if (initializer instanceof PsiLiteralExpression) {
      return true;
    }
    else if (initializer instanceof PsiPrefixExpression && ((PsiPrefixExpression)initializer).getOperand() instanceof PsiLiteralExpression) {
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
    else if (initializer instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression binExpr = (PsiPolyadicExpression)initializer;
      for (PsiExpression op : binExpr.getOperands()) {
        if (!canInlineParmOrThisVariable(op, shouldBeFinal, strictlyFinal, accessCount, false)) return false;
      }
      return true;
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

  private void inlineResultVariable(@NotNull PsiLocalVariable resultVar, @NotNull PsiReferenceExpression resultUsage) throws IncorrectOperationException {
    PsiElement context = PsiUtil.getVariableCodeBlock(resultVar, null);
    if (context == null) return;
    List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(resultVar, context);
    if (resultVar.getInitializer() == null) {
      PsiAssignmentExpression assignment = null;
      for (PsiReferenceExpression ref : references) {
        if (ref.getParent() instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)ref.getParent()).getLExpression().equals(ref)) {
          if (assignment != null) {
            assignment = null;
            break;
          }
          else {
            assignment = (PsiAssignmentExpression)ref.getParent();
          }
        }
      }

      if (assignment != null) {
        inlineSingleAssignment(resultVar, assignment, resultUsage);
        return;
      }
    }
    tryReplaceWithTarget(resultVar, resultUsage, context, references);
  }

  /**
   * If result of the method is an initializer of another var, try to reuse that var to store the result.
   */
  private static void tryReplaceWithTarget(@NotNull PsiLocalVariable variable,
                                           @NotNull PsiReferenceExpression usage,
                                           PsiElement context,
                                           List<PsiReferenceExpression> references) {
    PsiLocalVariable target = tryCast(PsiUtil.skipParenthesizedExprUp(usage.getParent()), PsiLocalVariable.class);
    if (target == null) return;
    String name = target.getName();
    if (name == null || !target.getType().equals(variable.getType())) return;
    PsiDeclarationStatement declaration = tryCast(target.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getDeclaredElements().length != 1) return;
    PsiModifierList modifiers = target.getModifierList();
    if (modifiers != null && modifiers.getAnnotations().length != 0) return;
    boolean effectivelyFinal = HighlightControlFlowUtil.isEffectivelyFinal(variable, context, null);
    if (!effectivelyFinal && !VariableAccessUtils.canUseAsNonFinal(target)) return;

    for (PsiReferenceExpression reference : references) {
      ExpressionUtils.bindReferenceTo(reference, name);
    }
    if (effectivelyFinal && target.hasModifierProperty(PsiModifier.FINAL)) {
      PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }
    variable.setName(name);
    new CommentTracker().deleteAndRestoreComments(declaration);
  }

  private void inlineSingleAssignment(@NotNull PsiVariable resultVar,
                                      @NotNull PsiAssignmentExpression assignment,
                                      @NotNull PsiReferenceExpression resultUsage) {
    LOG.assertTrue(assignment.getParent() instanceof PsiExpressionStatement);
    // SCR3175 fixed: inline only if declaration and assignment is in the same code block.
    if (!(assignment.getParent().getParent() == resultVar.getParent().getParent())) return;
    String name = Objects.requireNonNull(resultVar.getName());
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
      if (next.equals(parentStatement)) {
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

  private static final Key<String> MARK_KEY = Key.create("");

  private PsiReferenceExpression[] addBracesWhenNeeded(PsiReferenceExpression[] refs) throws IncorrectOperationException {
    ArrayList<PsiReferenceExpression> refsVector = new ArrayList<>();
    ArrayList<PsiCodeBlock> addedBracesVector = new ArrayList<>();
    myAddedClassInitializers = new HashMap<>();

    for (PsiReferenceExpression ref : refs) {
      if (ref instanceof PsiMethodReferenceExpression) continue;
      ref.putCopyableUserData(MARK_KEY, "");
    }

    RefLoop:
    for (PsiReferenceExpression ref : refs) {
      if (!ref.isValid()) continue;
      if (ref instanceof PsiMethodReferenceExpression) {
        refsVector.add(ref);
        continue;
      }

      PsiElement parentStatement = RefactoringUtil.getParentStatement(ref, true);
      if (parentStatement != null) {
        PsiElement parent = ref.getParent();
        while (!parent.equals(parentStatement)) {
          if (parent instanceof PsiExpressionStatement || parent instanceof PsiReturnStatement) {
            String text = "{\n}";
            PsiBlockStatement blockStatement = (PsiBlockStatement)myFactory.createStatementFromText(text, null);
            blockStatement = (PsiBlockStatement)myCodeStyleManager.reformat(blockStatement);
            blockStatement.getCodeBlock().add(parent);
            blockStatement = (PsiBlockStatement)parent.replace(blockStatement);

            PsiElement newStatement = blockStatement.getCodeBlock().getStatements()[0];
            addMarkedElements(refsVector, newStatement);
            addedBracesVector.add(blockStatement.getCodeBlock());
            continue RefLoop;
          }
          parent = parent.getParent();
        }
        final PsiElement lambdaExpr = parentStatement.getParent();
        if (lambdaExpr instanceof PsiLambdaExpression) {
          final PsiLambdaExpression newLambdaExpr = (PsiLambdaExpression)myFactory.createExpressionFromText(
            ((PsiLambdaExpression)lambdaExpr).getParameterList().getText() + " -> " + "{\n}", lambdaExpr);
          final PsiStatement statementFromText;
          if (PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)lambdaExpr))) {
            statementFromText = myFactory.createStatementFromText("a;", lambdaExpr);
            ((PsiExpressionStatement)statementFromText).getExpression().replace(parentStatement);
          } else {
            statementFromText = myFactory.createStatementFromText("return a;", lambdaExpr);
            ((PsiReturnStatement)statementFromText).getReturnValue().replace(parentStatement);
          }

          newLambdaExpr.getBody().add(statementFromText);

          final PsiCodeBlock body = (PsiCodeBlock)((PsiLambdaExpression)lambdaExpr.replace(newLambdaExpr)).getBody();
          PsiElement newStatement = body.getStatements()[0];
          addMarkedElements(refsVector, newStatement);
          addedBracesVector.add(body);
          continue;

        }
      }
      else {
        final PsiField field = PsiTreeUtil.getParentOfType(ref, PsiField.class);
        if (field != null) {
          if (field instanceof PsiEnumConstant) {
            inlineEnumConstantParameter(refsVector, ref);
            continue;
          }
          if (myAddedClassInitializers.containsKey(field)) {
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
          assignment.getLExpression().replace(RenameJavaMemberProcessor.createMemberReference(field, assignment));
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

    myAddedBraces = addedBracesVector.toArray(PsiCodeBlock.EMPTY_ARRAY);
    return refsVector.toArray(new PsiReferenceExpression[0]);
  }

  private void inlineEnumConstantParameter(final List<? super PsiReferenceExpression> refsVector,
                                           final PsiReferenceExpression ref) throws IncorrectOperationException {
    PsiExpression expr = getSimpleReturnedExpression(myMethod);
    if (expr != null) {
      refsVector.add(ref);
    }
    else {
      PsiCall call = PsiTreeUtil.getParentOfType(ref, PsiCall.class);
      @NonNls String text = "new Object() { " + myMethod.getReturnTypeElement().getText() + " evaluate() { return " + call.getText() + ";}}.evaluate";
      PsiExpression callExpr = JavaPsiFacade.getInstance(myProject).getParserFacade().createExpressionFromText(text, call);
      PsiReferenceExpression classExpr = (PsiReferenceExpression)ref.replace(callExpr);
      PsiNewExpression newObject = (PsiNewExpression)Objects.requireNonNull(classExpr.getQualifierExpression());
      PsiMethod evaluateMethod = Objects.requireNonNull(newObject.getAnonymousClass()).getMethods()[0];
      PsiExpression retVal = ((PsiReturnStatement)Objects.requireNonNull(evaluateMethod.getBody())
        .getStatements()[0]).getReturnValue();
      if (retVal instanceof PsiMethodCallExpression) {
        refsVector.add(((PsiMethodCallExpression) retVal).getMethodExpression());
      }
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

  private static void addMarkedElements(final List<? super PsiReferenceExpression> array, PsiElement scope) {
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

    for (PsiCodeBlock codeBlock : myAddedBraces) {
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length == 1) {
        final PsiElement codeBlockParent = codeBlock.getParent();
        PsiStatement statement = statements[0];
        if (codeBlockParent instanceof PsiLambdaExpression) {
          if (statement instanceof PsiReturnStatement) {
            final PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
            if (returnValue != null) {
              codeBlock.replace(returnValue);
            }
          } else if (statement instanceof PsiExpressionStatement){
            codeBlock.replace(((PsiExpressionStatement)statement).getExpression());
          }
        }
        else if (codeBlockParent instanceof PsiBlockStatement) {
          if (!(ifStatementWithAppendableElseBranch(statement) &&
                codeBlockParent.getParent() instanceof PsiIfStatement &&
                ((PsiIfStatement)codeBlockParent.getParent()).getElseBranch() != null)) {
            codeBlockParent.replace(statement);
          }
        }
        else {
          codeBlock.replace(statement);
        }
      }
    }

    myAddedClassInitializers.forEach((psiField, classInitializer) -> {
      PsiExpression newInitializer = getSimpleFieldInitializer(psiField, classInitializer);
      PsiExpression fieldInitializer = Objects.requireNonNull(psiField.getInitializer());
      if (newInitializer != null) {
        fieldInitializer.replace(newInitializer);
        classInitializer.delete();
      }
      else {
        fieldInitializer.delete();
      }
    });
  }

  private static boolean ifStatementWithAppendableElseBranch(PsiStatement statement) {
    if (statement instanceof PsiIfStatement) {
      PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
      return elseBranch == null || elseBranch instanceof PsiIfStatement;
    }
    return false;
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

  /**
   * @deprecated use {@link #checkUnableToInsertCodeBlock(PsiCodeBlock, PsiElement)}
   */
  @Deprecated
  public static String checkCalledInSuperOrThisExpr(PsiCodeBlock methodBody, final PsiElement element) {
    return checkUnableToInsertCodeBlock(methodBody, element,
                                        expr -> JavaPsiConstructorUtil.isConstructorCall(expr) && expr.getMethodExpression() != element)
           ? "Inline cannot be applied to multiline method in constructor call"
           : null;
  }

  public static String checkUnableToInsertCodeBlock(PsiCodeBlock methodBody, final PsiElement element) {
    if (checkUnableToInsertCodeBlock(methodBody, element,
                                     expr -> JavaPsiConstructorUtil.isConstructorCall(expr) && expr.getMethodExpression() != element)) {
      return "Inline cannot be applied to multiline method in constructor call";
    }
    return checkUnableToInsertCodeBlock(methodBody, element,
                                        expr -> {
                                          PsiElement parent = expr.getParent();
                                          return parent instanceof PsiLoopStatement && PsiUtil.isCondition(expr, parent);
                                        })
           ? "Inline cannot be applied to multiline method in loop condition"
           : null;
  }

  private static boolean checkUnableToInsertCodeBlock(final PsiCodeBlock methodBody,
                                                      final PsiElement element,
                                                      final Predicate<? super PsiMethodCallExpression> errorCondition) {
    PsiStatement[] statements = methodBody.getStatements();
    if (statements.length > 1 || statements.length == 1 && !(statements[0] instanceof PsiExpressionStatement)) {
      PsiMethodCallExpression expr = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, true, PsiStatement.class);
      while (expr != null) {
        if (errorCondition.test(expr)) {
          return true;
        }
        expr = PsiTreeUtil.getParentOfType(expr, PsiMethodCallExpression.class, true, PsiStatement.class);
      }
    }
    return false;
  }

  public static boolean checkBadReturns(PsiMethod method) {
    PsiReturnStatement[] returns = PsiUtil.findReturnStatements(method);
    PsiCodeBlock body = method.getBody();
    return checkBadReturns(returns, body);
  }

  public static boolean checkBadReturns(PsiReturnStatement[] returns, PsiCodeBlock body) {
    if (returns.length == 0) return false;
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

    List<Instruction> instructions = new ArrayList<>(controlFlow.getInstructions());

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
      while (offset != instructions.size()) {
        Instruction instruction = instructions.get(offset);
        if (instruction instanceof GoToInstruction) {
          offset = ((GoToInstruction)instruction).offset;
        }
        else if (instruction instanceof ThrowToInstruction) {
          offset = ((ThrowToInstruction)instruction).offset;
        }
        else if (instruction instanceof ConditionalThrowToInstruction) {
          // In case of "conditional throw to", control flow will not be altered
          // If exception handler is in method, we will inline it to invocation site
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

    BlockData(PsiCodeBlock block, PsiLocalVariable thisVar, PsiLocalVariable[] parmVars, PsiLocalVariable resultVar) {
      this.block = block;
      this.thisVar = thisVar;
      this.parmVars = parmVars;
      this.resultVar = resultVar;
    }
  }

  @Override
  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.singletonList(myReference);
    }
    else {
      if (!checkReadOnly()) return Collections.emptyList();
      return myReference == null ? Collections.singletonList(myMethod) : Arrays.asList(myReference, myMethod);
    }
  }
}
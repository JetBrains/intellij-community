// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.OverrideMethodsProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.rename.RenameJavaMemberProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(InlineMethodProcessor.class);

  private PsiMethod myMethod;
  private PsiReference myReference;
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
  @SuppressWarnings("LeakableMapKey") //short living refactoring
  private Map<Language, InlineHandler.Inliner> myInliners;

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiReference reference,
                               Editor editor,
                               boolean isInlineThisOnly) {
    this(project, method, reference, editor, isInlineThisOnly, false, false, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                             @NotNull PsiMethod method,
                             @Nullable PsiReference reference,
                             Editor editor,
                             boolean isInlineThisOnly,
                             boolean searchInComments,
                             boolean searchForTextOccurrences) {
    this(project, method, reference, editor, isInlineThisOnly, searchInComments, searchForTextOccurrences, true);
  }

  public InlineMethodProcessor(@NotNull Project project,
                               @NotNull PsiMethod method,
                               @Nullable PsiReference reference,
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
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myReference)};
    Set<UsageInfo> usages = new HashSet<>();
    if (myReference != null) {
      usages.add(new UsageInfo(myReference.getElement()));
    }
    for (PsiReference reference : MethodReferencesSearch.search(myMethod, myRefactoringScope, true)) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    if (myDeleteTheDeclaration) {
      OverridingMethodsSearch.search(myMethod, myRefactoringScope, true)
        .forEach(method -> {
          if (shouldDeleteOverrideAttribute(method)) {
            usages.add(new OverrideAttributeUsageInfo(method));
          }
          return true;
        });
    }

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

  private boolean shouldDeleteOverrideAttribute(PsiMethod method) {
    return method.getHierarchicalMethodSignature()
      .getSuperSignatures().stream()
      .allMatch(signature -> {
        PsiMethod superMethod = signature.getMethod();
        if (superMethod == myMethod) {
          return true;
        }
        if (JavaLanguage.INSTANCE == method.getLanguage() &&
            Objects.requireNonNull(superMethod.getContainingClass()).isInterface()) {
          return !PsiUtil.isLanguageLevel6OrHigher(method);
        }
        return false;
      });
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo @NotNull [] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof NonCodeUsageInfo) return true;
    }
    return super.isPreviewUsages(usages);
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
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

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.run(() -> {
        if (!myInlineThisOnly) {
          final PsiMethod[] superMethods = myMethod.findSuperMethods();
          for (PsiMethod method : superMethods) {
            String className = Objects.requireNonNull(method.getContainingClass()).getQualifiedName();
            final String message = method.hasModifierProperty(PsiModifier.ABSTRACT) ?
                                   JavaRefactoringBundle.message("inlined.method.implements.method.from.0", className) :
                                   JavaRefactoringBundle.message("inlined.method.overrides.method.from.0", className);
            conflicts.putValue(method, message);
          }

          for (UsageInfo info : usagesIn) {
            final PsiElement element = info.getElement();
            if (element instanceof PsiDocMethodOrFieldRef && !PsiTreeUtil.isAncestor(myMethod, element, false)) {
              conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.javadoc"));
            }
            if (element instanceof PsiLiteralExpression &&
                ContainerUtil.or(element.getReferences(), JavaLangClassMemberReference.class::isInstance)) {
              conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.reflection"));
            }
            if (element instanceof PsiMethodReferenceExpression) {
              final PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)element).getQualifierExpression();
              if (qualifierExpression != null) {
                final List<PsiElement> sideEffects = new ArrayList<>();
                SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
                if (!sideEffects.isEmpty()) {
                  conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.qualifier.usage.side.effect"));
                }
              }
            }
            if (element instanceof PsiReferenceExpression && myTransformerChooser.apply((PsiReference)element).isFallBackTransformer()) {
              conflicts.putValue(element, JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
            }

            final String errorMessage = checkUnableToInsertCodeBlock(myMethod.getBody(), element);
            if (errorMessage != null) {
              conflicts.putValue(element, errorMessage);
            }
          }
        }
        else if (myReference != null && myTransformerChooser.apply(myReference).isFallBackTransformer()) {
          conflicts.putValue(myReference.getElement(),
                             JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
        }
        addInaccessibleMemberConflicts(myMethod, usagesIn, new ReferencedElementsCollector(), conflicts);
        addInaccessibleSuperCallsConflicts(usagesIn, conflicts);
      }),
      RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    myInliners = GenericInlineHandler.initInliners(myMethod, usagesIn, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return myInlineThisOnly;
      }
    }, conflicts, JavaLanguage.INSTANCE);

    return showConflicts(conflicts, usagesIn);
  }


  private boolean checkReadOnly() {
    return myMethod.isWritable() || myMethod instanceof PsiCompiledElement;
  }

  private void addInaccessibleSuperCallsConflicts(final UsageInfo[] usagesIn, final MultiMap<PsiElement, String> conflicts) {

    myMethod.accept(new JavaRecursiveElementWalkingVisitor() {
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
            String descriptions = StringUtil.join(targetContainingClasses, psiClass -> RefactoringUIUtil.getDescription(psiClass, false), ",");
            conflicts.putValue(expression, JavaRefactoringBundle.message("inline.method.calls.not.accessible.in", methodCallExpression.getText(), descriptions));
          }

          if (qualifiedCall != null) {
            conflicts.putValue(expression, JavaRefactoringBundle.message("inline.method.calls.not.accessible.on.qualifier",
                                                                         methodCallExpression.getText(), qualifiedCall.getText()));
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
        conflicts.putValue(container, StringUtil.capitalize(message));
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
  static Map<PsiMember, Set<PsiMember>> getInaccessible(HashSet<? extends PsiMember> referencedElements,
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
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
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
        if (JavaLanguage.INSTANCE != myReference.getElement().getLanguage()) {
          GenericInlineHandler.inlineReference(new UsageInfo(myReference.getElement()), myMethod, myInliners);
        }
        else if (myMethod.isConstructor() && InlineUtil.isChainingConstructor(myMethod)) {
          if (myReference instanceof PsiMethodReferenceExpression) {
            inlineMethodReference((PsiMethodReferenceExpression)myReference);
          }
          else {
            PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)myReference);
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
            else if (!(element instanceof PsiDocMethodOrFieldRef)) {
              GenericInlineHandler.inlineReference(usage, myMethod, myInliners);
            }
          }
        }
        else {
          List<PsiReferenceExpression> refExprList = new ArrayList<>();
          final List<PsiElement> imports2Delete = new ArrayList<>();
          for (final UsageInfo usage : usages) {
            final PsiElement element = usage.getElement();
            if (element == null) continue;
            if (usage instanceof OverrideAttributeUsageInfo) {
              for (OverrideMethodsProcessor processor : OverrideMethodsProcessor.EP_NAME.getExtensionList()) {
                if (processor.removeOverrideAttribute(element)) {
                  break;
                }
              }
              continue;
            }
            
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
        if (myMethod.isValid() && myMethod.isWritable() && myDeleteTheDeclaration) {
          CommentTracker tracker = new CommentTracker();
          tracker.markUnchanged(myMethod.getBody());
          tracker.markUnchanged(myMethod.getDocComment());
          tracker.deleteAndRestoreComments(myMethod);
        }
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
          .createExpressionFromText("new " + arrayType.getCanonicalText() + "[]{" + varargs + "}", constructorCall);

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
    Map<PsiReferenceExpression, PsiExpression> replacement = new LinkedHashMap<>();
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter &&
            element.getManager().areElementsEquivalent(((PsiParameter)resolved).getDeclarationScope(), oldConstructor)) {
          int parameterIndex = oldConstructor.getParameterList().getParameterIndex((PsiParameter)resolved);
          if (parameterIndex >= 0) {
            replacement.put(expression, instanceCreationArguments[parameterIndex]);
          }
        }  
      }
    });
    for (Map.Entry<PsiReferenceExpression, PsiExpression> entry : replacement.entrySet()) {
      try {
        entry.getKey().replace(entry.getValue());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  public void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
    myMethodCopy = (PsiMethod)myMethod.copy();

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    InlineMethodHelper helper = new InlineMethodHelper(myProject, myMethod, myMethodCopy, methodCall);
    BlockData blockData = prepareBlock(ref, helper);
    ChangeContextUtil.encodeContextInfo(blockData.block, false);
    InlineUtil.solveVariableNameConflicts(blockData.block, ref, myMethodCopy.getBody());
    helper.initializeParameters(blockData.parmVars);
    addThisInitializer(methodCall, blockData.thisVar);
    
    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(methodCall, true);
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
      if (!InlineUtil.canInlineParameterOrThisVariable(thisVar)) {
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

    PsiReferenceExpression resultUsage = replaceCall(myFactory, methodCall, firstAdded, blockData.resultVar);

    if (thisVar != null) {
      InlineUtil.tryInlineGeneratedLocal(thisVar, false);
    }
    helper.inlineParameters(parmVars);
    if (resultVar != null && resultUsage != null) {
      InlineUtil.tryInlineResultVariable(resultVar, resultUsage);
    }

    ChangeContextUtil.clearContextInfo(anchorParent);
  }

  @Nullable
  static PsiReferenceExpression replaceCall(@NotNull PsiElementFactory factory,
                                            @NotNull PsiMethodCallExpression methodCall,
                                            @Nullable PsiElement firstAdded,
                                            @Nullable PsiLocalVariable resultVar) {
    if (resultVar != null) {
      PsiExpression expr = factory.createExpressionFromText(resultVar.getName(), null);
      return (PsiReferenceExpression)new CommentTracker().replaceAndRestoreComments(methodCall, expr);
    }
    // If return var is not specified, we trust that InlineTransformer fully processed the original anchor statement,
    // and we can delete it.
    CommentTracker tracker = new CommentTracker();
    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(methodCall, true);
    assert anchor != null;
    if (firstAdded != null) {
      tracker.delete(anchor);
      tracker.insertCommentsBefore(firstAdded);
    } else {
      tracker.deleteAndRestoreComments(anchor);
    }
    return null;
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
    return InlineUtil.inlineInitializer((PsiVariable)resolve, initializer, expression);
  }

  private void substituteMethodTypeParams(PsiElement scope, final PsiSubstitutor substitutor) {
    InlineUtil.substituteTypeParams(scope, substitutor, myFactory);
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

  private BlockData prepareBlock(PsiReferenceExpression ref, InlineMethodHelper helper)
    throws IncorrectOperationException {
    final PsiCodeBlock block = Objects.requireNonNull(myMethodCopy.getBody());
    PsiSubstitutor callSubstitutor = helper.getSubstitutor();
    if (callSubstitutor != PsiSubstitutor.EMPTY) {
      substituteMethodTypeParams(block, callSubstitutor);
    }
    final PsiStatement[] originalStatements = block.getStatements();

    PsiType returnType = callSubstitutor.substitute(myMethod.getReturnType());
    InlineTransformer transformer = myTransformerChooser.apply(ref);

    PsiLocalVariable[] parmVars = helper.declareParameters();

    PsiLocalVariable thisVar = declareThis(callSubstitutor, block);

    addSynchronization(ref, block, originalStatements, thisVar);

    PsiLocalVariable resultVar = transformer.transformBody(myMethodCopy, ref, returnType);

    return new BlockData(block, thisVar, parmVars, resultVar);
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

  private void addThisInitializer(PsiMethodCallExpression methodCall, PsiLocalVariable thisVar) throws IncorrectOperationException {
    if (thisVar != null) {
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
            String name = parentClass.getName();
            // We cannot have qualified this reference to an anonymous class, so we leave it unqualified
            // this might produce incorrect code in extremely rare cases 
            // when we inline a superclass method in an anonymous class, 
            // and the method body contains a nested class that refers to the outer one
            qualifier = myFactory.createExpressionFromText(name == null ? "this" : name + ".this", null);
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
      thisVar.getInitializer().replace(qualifier);
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

      PsiElement parentStatement = CommonJavaRefactoringUtil.getParentStatement(ref, true);
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
        else if (lambdaExpr instanceof PsiSwitchLabeledRuleStatement && parentStatement instanceof PsiExpressionStatement) {
          CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(ref);
          if (surrounder != null) {
            CodeBlockSurrounder.SurroundResult surround = surrounder.surround();
            PsiExpression expression = surround.getExpression();
            addMarkedElements(refsVector, expression);
            ContainerUtil.addIfNotNull(addedBracesVector, PsiTreeUtil.getParentOfType(surround.getAnchor(), PsiCodeBlock.class));
            continue;
          }
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
      @Override public void visitElement(@NotNull PsiElement element) {
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
          if (statement instanceof PsiYieldStatement) {
            PsiExpression expression = ((PsiYieldStatement)statement).getExpression();
            if (expression != null) {
              PsiExpressionStatement statementFromText = (PsiExpressionStatement)myFactory.createStatementFromText("a;", expression);
              statementFromText.getExpression().replace(expression);
              codeBlockParent.replace(statementFromText);
            }
          } else if (!(ifStatementWithAppendableElseBranch(statement) &&
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

  public static @NlsContexts.DialogMessage String checkUnableToInsertCodeBlock(PsiCodeBlock methodBody, final PsiElement element) {
    if (checkUnableToInsertCodeBlock(methodBody, element,
                                     expr -> JavaPsiConstructorUtil.isConstructorCall(expr) && expr.getMethodExpression() != element)) {
      return JavaRefactoringBundle.message("inline.method.multiline.method.in.ctor.call");
    }
    return checkUnableToInsertCodeBlock(methodBody, element,
                                        expr -> {
                                          PsiConditionalLoopStatement loopStatement = PsiTreeUtil.getParentOfType(expr, PsiConditionalLoopStatement.class);
                                          return loopStatement != null && PsiTreeUtil.isAncestor(loopStatement.getCondition(), expr, false);
                                        })
           ? JavaRefactoringBundle.message("inline.method.multiline.method.in.loop.condition")
           : null;
  }

  private static boolean checkUnableToInsertCodeBlock(final PsiCodeBlock methodBody,
                                                      final PsiElement element,
                                                      final Predicate<? super PsiMethodCallExpression> errorCondition) {
    PsiStatement[] statements = methodBody.getStatements();
    if (statements.length > 1 || statements.length == 1 && 
                                 !(statements[0] instanceof PsiExpressionStatement) && 
                                 !(statements[0] instanceof PsiReturnStatement)) {
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
      return Collections.singletonList(myReference.getElement());
    }
    else {
      if (!checkReadOnly()) return Collections.emptyList();
      return myReference == null ? Collections.singletonList(myMethod) : Arrays.asList(myReference.getElement(), myMethod);
    }
  }

  private static class OverrideAttributeUsageInfo extends UsageInfo {
    private OverrideAttributeUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }
}
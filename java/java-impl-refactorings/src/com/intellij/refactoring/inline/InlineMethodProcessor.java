// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.*;
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
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

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

  private final PsiElementFactory myFactory;

  private final String myDescriptiveName;
  private List<CodeBlockSurrounder.SurroundResult> mySurroundResults;
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

    myFactory = JavaPsiFacade.getElementFactory(myProject);
    myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myMethod);
  }

  @Override
  protected @NotNull String getCommandName() {
    return RefactoringBundle.message("inline.method.command", myDescriptiveName);
  }

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new InlineViewDescriptor(myMethod);
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myReference)};
    Set<UsageInfo> usages = new HashSet<>();
    if (myReference != null) {
      usages.add(new UsageInfo(myReference.getElement()));
    }
    for (PsiReference reference : MethodReferencesSearch.search(myMethod, myRefactoringScope, true).asIterable()) {
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

      if (mySearchForTextOccurrences && myRefactoringScope instanceof GlobalSearchScope scope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(myMethod, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(myMethod, stringToSearch, scope, usages, infoFactory);
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private boolean shouldDeleteOverrideAttribute(PsiMethod method) {
    return ContainerUtil.and(method.getHierarchicalMethodSignature().getSuperSignatures(), signature -> {
        PsiMethod superMethod = signature.getMethod();
        if (superMethod == myMethod) {
          return true;
        }
        if (JavaLanguage.INSTANCE == method.getLanguage() &&
            Objects.requireNonNull(superMethod.getContainingClass()).isInterface()) {
          return !PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, method);
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
  protected Set<UnloadedModuleDescription> computeUnloadedModulesFromUseScope(UsageViewDescriptor descriptor) {
    if (myInlineThisOnly) {
      return Collections.emptySet();
    }
    return super.computeUnloadedModulesFromUseScope(descriptor);
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
    final MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();

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
            if (element instanceof PsiMethodReferenceExpression ref) {
              processSideEffectsInMethodReferenceQualifier(conflicts, ref);
            }
            if (element instanceof PsiReferenceExpression ref && myTransformerChooser.apply(ref).isFallBackTransformer()) {
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
        else if (myReference instanceof PsiMethodReferenceExpression ref) {
          processSideEffectsInMethodReferenceQualifier(conflicts, ref);
        }
        addInaccessibleMemberConflicts(myMethod, usagesIn, new ReferencedElementsCollector(), conflicts);
        addInaccessibleSuperCallsConflicts(usagesIn, conflicts);
      }),
      RefactoringBundle.message("detecting.possible.conflicts"), true, myProject)) {
      return false;
    }

    //kotlin j2k fails badly if moved under progress
    myInliners = GenericInlineHandler.initInliners(myMethod, usagesIn, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return myInlineThisOnly;
      }
    }, conflicts, JavaLanguage.INSTANCE);

    return showConflicts(conflicts, usagesIn);
  }

  private static void processSideEffectsInMethodReferenceQualifier(@NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                                   @NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    if (qualifierExpression != null) {
      final List<PsiElement> sideEffects = new ArrayList<>();
      SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
      if (!sideEffects.isEmpty()) {
        conflicts.putValue(methodReferenceExpression, JavaRefactoringBundle.message("inline.method.qualifier.usage.side.effect"));
      }
    }
  }

  private boolean checkReadOnly() {
    return myMethod.isWritable() || myMethod instanceof PsiCompiledElement;
  }

  private void addInaccessibleSuperCallsConflicts(UsageInfo[] usagesIn, MultiMap<PsiElement, @DialogMessage String> conflicts) {
    myMethod.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {}

      @Override
      public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
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
              else if (element instanceof PsiReferenceExpression ref && !ExpressionUtil.isEffectivelyUnqualified(ref)) {
                qualifiedCall = ref.getQualifierExpression();
              }
            }
          }
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
          LOG.assertTrue(methodCallExpression != null);
          if (!targetContainingClasses.isEmpty()) {
            String names = StringUtil.join(targetContainingClasses, psiClass -> RefactoringUIUtil.getDescription(psiClass, false), ",");
            String message = JavaRefactoringBundle.message("inline.method.calls.not.accessible.in", methodCallExpression.getText(), names);
            conflicts.putValue(expression, message);
          }

          if (qualifiedCall != null) {
            conflicts.putValue(expression, JavaRefactoringBundle.message("inline.method.calls.not.accessible.on.qualifier",
                                                                         methodCallExpression.getText(), qualifiedCall.getText()));
          }
        }
      }
    });
  }

  public static void addInaccessibleMemberConflicts(PsiMethod method,
                                                    UsageInfo[] usages,
                                                    ReferencedElementsCollector collector,
                                                    MultiMap<PsiElement, @DialogMessage String> conflicts) {
    PsiCodeBlock body = Objects.requireNonNull(method.getBody());
    body.accept(collector);
    final Map<PsiMember, Set<PsiMember>> locationsToInaccessibles = getInaccessible(collector.myReferencedMembers, usages, method);
    String methodDescription = RefactoringUIUtil.getDescription(method, true);
    locationsToInaccessibles.forEach((container, inaccessibles) -> {
      for (PsiMember inaccessible : inaccessibles) {
        final String referencedDescription = RefactoringUIUtil.getDescription(inaccessible, true);
        final String containerDescription = RefactoringUIUtil.getDescription(container, true);
        String message = RefactoringBundle.message("0.which.is.used.in.1.not.accessible.from.call.site.s.in.2",
                                                   referencedDescription, methodDescription, containerDescription);
        conflicts.putValue(usages.length == 1 ? inaccessible : container, StringUtil.capitalize(message));
      }
    });
  }

  /**
   * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
   * to subsets of referencedElements that are not accessible from that container
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
      if (!(container instanceof PsiMember memberContainer)) continue;    // usage in import statement
      Set<PsiMember> inaccessibleReferenced = result.get(memberContainer);
      if (inaccessibleReferenced == null) {
        inaccessibleReferenced = new HashSet<>();
        result.put(memberContainer, inaccessibleReferenced);
        for (PsiMember member : referencedElements) {
          if (PsiTreeUtil.isAncestor(elementToInline, member, false)) continue;
          if (elementToInline instanceof PsiClass c && InheritanceUtil.isInheritorOrSelf(c, member.getContainingClass(), true)) continue;
          PsiElement resolveScope = usageElement instanceof PsiReferenceExpression ref
                                    ? ref.advancedResolve(false).getCurrentFileResolveScope()
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

  @Override
  protected String getRefactoringId() {
    return "refactoring.inline.method";
  }

  @Override
  protected RefactoringEventData getBeforeData() {
    final RefactoringEventData data = new RefactoringEventData();
    if (myDeleteTheDeclaration) data.addElement(myMethod);
    return data;
  }

  private void doRefactoring(UsageInfo[] usages) {
    try {
      if (myInlineThisOnly) {
        if (JavaLanguage.INSTANCE != myReference.getElement().getLanguage()) {
          GenericInlineHandler.inlineReference(new UsageInfo(myReference.getElement()), myMethod, myInliners);
        }
        else if (myMethod.isConstructor() && InlineUtil.isChainingConstructor(myMethod)) {
          if (myReference instanceof PsiMethodReferenceExpression ref) {
            inlineMethodReference(ref);
          }
          else {
            PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)myReference);
            if (constructorCall != null) {
              inlineConstructorCall(constructorCall);
            }
          }
        }
        else {
          myReference = surroundWithCodeBlock(new PsiReferenceExpression[]{(PsiReferenceExpression)myReference})[0];
          if (myReference instanceof PsiMethodReferenceExpression ref) {
            inlineMethodReference(ref);
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
            if (element instanceof PsiMethodReferenceExpression ref) {
              inlineMethodReference(ref);
            }
            else if (element instanceof PsiJavaCodeReferenceElement ref) {
              PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(ref);
              if (constructorCall != null) {
                inlineConstructorCall(constructorCall);
              }
            }
            else if (element instanceof PsiEnumConstant constant) {
              inlineConstructorCall(constant);
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
            
            if (element instanceof PsiReferenceExpression ref) {
              refExprList.add(ref);
            }
            else if (element instanceof PsiImportStaticReferenceElement ref) {
              final JavaResolveResult[] resolveResults = ref.multiResolve(false);
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
          refs = surroundWithCodeBlock(refs);
          for (PsiReferenceExpression ref : refs) {
            if (ref instanceof PsiMethodReferenceExpression methodRef) {
              inlineMethodReference(methodRef);
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
      if (mySurroundResults != null) {
        for (CodeBlockSurrounder.SurroundResult result : mySurroundResults) {
          result.collapse();
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void inlineMethodReference(PsiMethodReferenceExpression reference) {
    final PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(reference, false, false);
    if (lambdaExpression == null) return;
    final PsiExpression callExpression = LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody());
    if (callExpression instanceof PsiMethodCallExpression call) {
      inlineMethodCall(call.getMethodExpression());
    }
    else if (callExpression instanceof PsiCall call) {
      inlineConstructorCall(call);
    }
    else {
      LOG.error("Unexpected expr: " + callExpression.getText());
    }
    LambdaRefactoringUtil.simplifyToExpressionLambda(lambdaExpression);
  }

  public static void inlineConstructorCall(PsiCall constructorCall) {
    PsiMethod oldConstructor = constructorCall.resolveMethod();
    LOG.assertTrue(oldConstructor != null);
    oldConstructor = (PsiMethod)oldConstructor.getNavigationElement();

    PsiExpression[] arguments = CommonJavaRefactoringUtil.getNonVarargArguments(constructorCall);
    PsiStatement[] statements = oldConstructor.getBody().getStatements();
    LOG.assertTrue(statements.length == 1 && statements[0] instanceof PsiExpressionStatement);
    PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    LOG.assertTrue(expression instanceof PsiMethodCallExpression);
    ChangeContextUtil.encodeContextInfo(expression, true);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression.copy();
    for (PsiExpression arg : methodCall.getArgumentList().getExpressions()) {
      replaceParameterReferences(arg, oldConstructor, arguments);
    }
    try {
      final PsiExpressionList exprList = (PsiExpressionList) constructorCall.getArgumentList().replace(methodCall.getArgumentList());
      ChangeContextUtil.decodeContextInfo(exprList, PsiTreeUtil.getParentOfType(constructorCall, PsiClass.class), null);
      if (!exprList.isEmpty()) {
        PsiExpression[] expressions = exprList.getExpressions();
        CommonJavaRefactoringUtil.tryToInlineArrayCreationForVarargs(expressions[expressions.length - 1]);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    ChangeContextUtil.clearContextInfo(expression);
  }

  private static void replaceParameterReferences(PsiElement element, PsiMethod oldConstructor, PsiExpression[] instanceCreationArguments) {
    Map<PsiReferenceExpression, PsiExpression> replacement = new LinkedHashMap<>();
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter param &&
            element.getManager().areElementsEquivalent(param.getDeclarationScope(), oldConstructor)) {
          int parameterIndex = oldConstructor.getParameterList().getParameterIndex(param);
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

  public void inlineMethodCall(PsiReferenceExpression ref) {
    myMethodCopy = (PsiMethod)myMethod.copy();

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    InlineMethodHelper helper = new InlineMethodHelper(myProject, myMethod, myMethodCopy, methodCall);
    BlockData blockData = prepareBlock(ref, helper);
    PsiCodeBlock block = blockData.block;
    replaceWithAccessors(ref, block);
    ChangeContextUtil.encodeContextInfo(block, false);
    helper.substituteTypes(blockData.parmVars);
    InlineUtil.solveLocalNameConflicts(block, ref, myMethodCopy.getBody());
    helper.initializeParameters(blockData.parmVars);
    addThisInitializer(methodCall, blockData.thisVar);
    
    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(methodCall, true);
    if (anchor == null) {
      throw new IllegalStateException("Cannot inline: parent statement should be available after CodeBlockSurround");
    }
    PsiElement anchorParent = anchor.getParent();
    PsiLocalVariable thisVar = null;
    PsiLocalVariable[] parmVars = new PsiLocalVariable[blockData.parmVars.length];
    PsiLocalVariable resultVar = null;
    PsiStatement[] statements = block.getStatements();
    PsiElement firstBodyElement = block.getFirstBodyElement();
    if (firstBodyElement instanceof PsiWhiteSpace) firstBodyElement = PsiTreeUtil.skipWhitespacesForward(firstBodyElement);
    PsiElement firstAdded = null;
    if (firstBodyElement != null && firstBodyElement != block.getRBrace()) {
      int last = statements.length - 1;

      final PsiElement rBraceOrReturnStatement =
        last >= 0 ? PsiTreeUtil.skipWhitespacesAndCommentsForward(statements[last]) : block.getLastBodyElement();
      LOG.assertTrue(rBraceOrReturnStatement != null);
      final PsiElement beforeRBraceStatement = rBraceOrReturnStatement.getPrevSibling();
      LOG.assertTrue(beforeRBraceStatement != null);

      firstAdded = anchorParent.addRangeBefore(firstBodyElement, beforeRBraceStatement, anchor);
      JavaCodeStyleManager style = JavaCodeStyleManager.getInstance(myProject);

      for (PsiElement e = firstAdded; e != anchor; e = e.getNextSibling()) {
        style.shortenClassReferences(e);
        if (e instanceof PsiDeclarationStatement declaration && 
            ArrayUtil.getFirstElement(declaration.getDeclaredElements()) instanceof PsiLocalVariable var) {
          String name = var.getName();
          if (blockData.resultVar != null && name.equals(blockData.resultVar.getName())) {
            resultVar = var;
          }
          else if (blockData.thisVar != null && name.equals(blockData.thisVar.getName())) {
            thisVar = var;
          }
          else {
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

  private static void replaceWithAccessors(PsiReferenceExpression ref, PsiCodeBlock block) {
    List<PsiReferenceExpression> list = SyntaxTraverser.psiTraverser(block).filter(PsiReferenceExpression.class).toList();
    // Iterate in opposite order, so in case of nested accessors, we first replace method arguments, then methods itself
    for (int i = list.size() - 1; i >= 0; i--) {
      PsiReferenceExpression r = list.get(i);
      if (!r.isValid()) continue;
      FieldAccessFixer fixer = FieldAccessFixer.create(r, r.resolve(), ref);
      // Name-based is too risky for inline
      if (fixer != null && fixer.kind() != FieldAccessFixer.AccessorKind.NAME_BASED) {
        fixer.apply(r);
      }
    }
  }

  static @Nullable PsiReferenceExpression replaceCall(@NotNull PsiElementFactory factory,
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
    if (anchor instanceof PsiReturnStatement oldReturn &&
        PsiTreeUtil.skipWhitespacesAndCommentsBackward(anchor) instanceof PsiReturnStatement newReturn &&
        newReturn.getReturnValue() != null) {
      // Remove new return instead of old return to preserve surrounder anchors
      tracker.replace(Objects.requireNonNull(oldReturn.getReturnValue()), newReturn.getReturnValue());
      anchor = newReturn;
    }
    if (firstAdded != null) {
      tracker.delete(anchor);
      tracker.insertCommentsBefore(firstAdded);
    } else {
      tracker.deleteAndRestoreComments(anchor);
    }
    return null;
  }

  private void substituteMethodTypeParams(PsiElement scope, PsiSubstitutor substitutor) {
    InlineUtil.substituteTypeParams(scope, substitutor, myFactory);
  }

  private boolean syncNeeded(PsiReferenceExpression ref) {
    if (!myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    final PsiMethod containingMethod = Util.getContainingMethod(ref);
    if (containingMethod == null) return true;
    if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return true;
    final PsiClass sourceContainingClass = myMethod.getContainingClass();
    final PsiClass targetContainingClass = containingMethod.getContainingClass();
    return !sourceContainingClass.equals(targetContainingClass);
  }

  private BlockData prepareBlock(PsiReferenceExpression ref, InlineMethodHelper helper) {
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

  private @Nullable PsiLocalVariable declareThis(PsiSubstitutor callSubstitutor, PsiCodeBlock block) {
    PsiClass containingClass = myMethod.getContainingClass();
    if (myMethod.hasModifierProperty(PsiModifier.STATIC) || containingClass == null || containingClass instanceof PsiImplicitClass) {
      return null;
    }
    PsiType thisType = GenericsUtil.getVariableTypeByExpressionType(myFactory.createType(containingClass, callSubstitutor));
    String thisVarName = new VariableNameGenerator(myMethod.getFirstChild(), VariableKind.LOCAL_VARIABLE)
      .byType(thisType).byName("self").generate(true);
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
      for (PsiStatement originalStatement : originalStatements) {
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
    }
  }

  private void addThisInitializer(PsiMethodCallExpression methodCall, PsiLocalVariable thisVar) {
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
        if (parent instanceof PsiClass parentClass) {
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
      else if (qualifier.getType() != null && !thisVar.getType().isAssignableFrom(qualifier.getType())) {
        PsiTypeCastExpression cast = (PsiTypeCastExpression)myFactory.createExpressionFromText("(A)b", null);
        Objects.requireNonNull(cast.getOperand()).replace(qualifier);
        Objects.requireNonNull(cast.getCastType()).replace(myFactory.createTypeElement(thisVar.getType()));
        qualifier = cast;
      }
      Objects.requireNonNull(thisVar.getInitializer()).replace(qualifier);
    }
  }

  private static final Key<PsiReferenceExpression> MARK_KEY = Key.create("MarkForSurround");

  private PsiReferenceExpression[] surroundWithCodeBlock(PsiReferenceExpression[] refs) {
    mySurroundResults = new ArrayList<>();

    for (PsiReferenceExpression ref : refs) {
      if (ref instanceof PsiMethodReferenceExpression) continue;
      ref.putCopyableUserData(MARK_KEY, ref);
    }
    var visitor = new PsiRecursiveElementWalkingVisitor() {
      final Map<PsiReferenceExpression, PsiReferenceExpression> mapping = new HashMap<>();

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiReferenceExpression ref) {
          PsiReferenceExpression orig = element.getCopyableUserData(MARK_KEY);
          if (orig != null) {
            mapping.put(orig, ref);
          }
        }
        super.visitElement(element);
      }
    };

    for (PsiReferenceExpression ref : refs) {
      if (!ref.isValid() || ref instanceof PsiMethodReferenceExpression) continue;

      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(ref);
      if (surrounder != null) {
        CodeBlockSurrounder.SurroundResult surround = surrounder.surround();
        surround.getAnchor().accept(visitor);
        mySurroundResults.add(surround);
      }
    }

    return StreamEx.of(refs).map(ref -> visitor.mapping.getOrDefault(ref, ref))
      .peek(ref -> ref.putCopyableUserData(MARK_KEY, null))
      .toArray(new PsiReferenceExpression[0]);
  }

  public static @DialogMessage String checkUnableToInsertCodeBlock(PsiCodeBlock methodBody, PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.STATEMENTS_BEFORE_SUPER, element) &&
        checkUnableToInsertCodeBlock(methodBody, element,
                                     expr -> JavaPsiConstructorUtil.isConstructorCall(expr) && expr.getMethodExpression() != element)) {
      return JavaRefactoringBundle.message("inline.method.multiline.method.in.ctor.call");
    }
    Predicate<PsiMethodCallExpression> errorCondition = call -> {
      PsiConditionalLoopStatement loopStatement = PsiTreeUtil.getParentOfType(call, PsiConditionalLoopStatement.class);
      return loopStatement != null && PsiTreeUtil.isAncestor(loopStatement.getCondition(), call, false);
    };
    return checkUnableToInsertCodeBlock(methodBody, element, errorCondition)
           ? JavaRefactoringBundle.message("inline.method.multiline.method.in.loop.condition")
           : null;
  }

  private static boolean checkUnableToInsertCodeBlock(PsiCodeBlock methodBody,
                                                      PsiElement element,
                                                      Predicate<? super PsiMethodCallExpression> errorCondition) {
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
        if (instruction instanceof GoToInstruction g) {
          offset = g.offset;
        }
        else if (instruction instanceof ThrowToInstruction t) {
          offset = t.offset;
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

  private record BlockData(PsiCodeBlock block, PsiLocalVariable thisVar, PsiLocalVariable[] parmVars, PsiLocalVariable resultVar) {}

  @Override
  protected @NotNull Collection<? extends PsiElement> getElementsToWrite(@NotNull UsageViewDescriptor descriptor) {
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
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalLoopStatement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.OverrideMethodsProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.Util;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.FieldAccessFixer;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Util class that provides steps for "Inline Method" refactoring that can be accessed outside of {@link com.intellij.refactoring.BaseRefactoringProcessor}
 */
@ApiStatus.Internal
public final class InlineMethodProcessorUtil {
  private static final Logger LOG = Logger.getInstance(InlineMethodProcessorUtil.class);

  private InlineMethodProcessorUtil() { }

  public static UsageInfo @NotNull [] findUsages(@NotNull InlineMethodContext context,
                                                 @NotNull SearchScope refactoringScope,
                                                 boolean searchInComments,
                                                 boolean searchForTextOccurrences) {
    PsiMethod method = context.method();
    PsiReference reference = context.reference();
    if (context.inlineThisOnly()) return new UsageInfo[]{new UsageInfo(Objects.requireNonNull(reference))};
    Set<UsageInfo> usages = ConcurrentCollectionFactory.createConcurrentSet();
    if (reference != null) {
      usages.add(new UsageInfo(reference.getElement()));
    }
    for (PsiReference ref : MethodReferencesSearch.search(method, refactoringScope, true).findAll()) {
      usages.add(new UsageInfo(ref.getElement()));
    }

    if (context.deleteTheDeclaration()) {
      OverridingMethodsSearch.search(method, refactoringScope, true)
        .forEach(overridingMethod -> {
          if (shouldDeleteOverrideAttribute(method, overridingMethod)) {
            usages.add(new OverrideAttributeUsageInfo(overridingMethod));
          }
          return true;
        });
    }

    if (searchInComments || searchForTextOccurrences) {
      final NonCodeUsageInfoFactory infoFactory = new NonCodeUsageInfoFactory(method, method.getName()) {
        @Override
        public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
          if (PsiTreeUtil.isAncestor(method, usage, false)) return null;
          return super.createUsageInfo(usage, startOffset, endOffset);
        }
      };
      if (searchInComments) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
        TextOccurrencesUtil.addUsagesInStringsAndComments(method, refactoringScope, stringToSearch, usages, infoFactory);
      }

      if (searchForTextOccurrences && refactoringScope instanceof GlobalSearchScope scope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(method, stringToSearch, scope, usages, infoFactory);
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static boolean shouldDeleteOverrideAttribute(@NotNull PsiMethod inlinedMethod, @NotNull PsiMethod overridingMethod) {
    return ContainerUtil.and(overridingMethod.getHierarchicalMethodSignature().getSuperSignatures(), signature -> {
      PsiMethod superMethod = signature.getMethod();
      if (superMethod == inlinedMethod) {
        return true;
      }
      if (JavaLanguage.INSTANCE == overridingMethod.getLanguage() &&
          Objects.requireNonNull(superMethod.getContainingClass()).isInterface()) {
        return !PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, overridingMethod);
      }
      return false;
    });
  }

  public static void collectConflicts(@NotNull InlineMethodContext context,
                                      UsageInfo @NotNull [] usages,
                                      @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    PsiMethod method = context.method();
    PsiReference reference = context.reference();
    Function<PsiReference, InlineTransformer> transformerChooser = context.transformerChooser();
    if (!context.inlineThisOnly()) {
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        String className = Objects.requireNonNull(superMethod.getContainingClass()).getQualifiedName();
        final String message = superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                               JavaRefactoringBundle.message("inlined.method.implements.method.from.0", className) :
                               JavaRefactoringBundle.message("inlined.method.overrides.method.from.0", className);
        conflicts.putValue(superMethod, message);
      }

      for (UsageInfo info : usages) {
        final PsiElement element = info.getElement();
        if (element instanceof PsiDocMethodOrFieldRef && !PsiTreeUtil.isAncestor(method, element, false)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.javadoc"));
        }
        if (element instanceof PsiLiteralExpression &&
            ContainerUtil.or(element.getReferences(), JavaLangClassMemberReference.class::isInstance)) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inline.method.used.in.reflection"));
        }
        if (element instanceof PsiMethodReferenceExpression ref) {
          processSideEffectsInMethodReferenceQualifier(conflicts, ref);
        }
        if (element instanceof PsiReferenceExpression ref && transformerChooser.apply(ref).isFallBackTransformer()) {
          conflicts.putValue(element, JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
        }

        final String errorMessage = checkUnableToInsertCodeBlock(method.getBody(), element);
        if (errorMessage != null) {
          conflicts.putValue(element, errorMessage);
        }
      }
    }
    else if (reference != null && transformerChooser.apply(reference).isFallBackTransformer()) {
      conflicts.putValue(reference.getElement(),
                         JavaRefactoringBundle.message("inlined.method.will.be.transformed.to.single.return.form"));
    }
    else if (reference instanceof PsiMethodReferenceExpression ref) {
      processSideEffectsInMethodReferenceQualifier(conflicts, ref);
    }
    addInaccessibleMemberConflicts(method, usages, new ReferencedElementsCollector(), conflicts);
    addInaccessibleSuperCallsConflicts(method, usages, conflicts);
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
  static @NotNull Map<PsiMember, Set<PsiMember>> getInaccessible(HashSet<? extends PsiMember> referencedElements,
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

  private static void addInaccessibleSuperCallsConflicts(@NotNull PsiMethod method,
                                                          UsageInfo @NotNull [] usages,
                                                          MultiMap<PsiElement, @DialogMessage String> conflicts) {
    method.accept(new JavaRecursiveElementWalkingVisitor() {
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
          for (UsageInfo info : usages) {
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

  public static @NotNull Map<Language, InlineHandler.Inliner> initInliners(@NotNull InlineMethodContext context,
                                                                           UsageInfo @NotNull [] usages,
                                                                           @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts) {
    return GenericInlineHandler.initInliners(context.method(), usages, new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return context.inlineThisOnly();
      }
    }, conflicts, JavaLanguage.INSTANCE);
  }

  public static @Nullable PsiReference performRefactoring(@NotNull InlineMethodContext context,
                                                          UsageInfo @NotNull [] usages,
                                                          Map<Language, InlineHandler.Inliner> inliners) {
    PsiReference inlinedReference = context.reference();
    List<CodeBlockSurrounder.SurroundResult> surroundResults = new ArrayList<>();
    try {
      if (context.inlineThisOnly()) {
        Objects.requireNonNull(context.reference());
        if (JavaLanguage.INSTANCE != context.reference().getElement().getLanguage()) {
          GenericInlineHandler.inlineReference(new UsageInfo(context.reference().getElement()), context.method(), inliners);
        }
        else if (context.method().isConstructor() && InlineUtil.isChainingConstructor(context.method())) {
          if (context.reference() instanceof PsiMethodReferenceExpression ref) {
            inlineMethodReference(context, ref);
          }
          else {
            PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement)context.reference());
            if (constructorCall != null) {
              inlineConstructorCall(constructorCall);
            }
          }
        }
        else {
          inlinedReference =
            surroundWithCodeBlock(new PsiReferenceExpression[]{(PsiReferenceExpression)context.reference()}, surroundResults)[0];
          if (inlinedReference instanceof PsiMethodReferenceExpression ref) {
            inlineMethodReference(context, ref);
          }
          else {
            inlineMethodCall(context, (PsiReferenceExpression)inlinedReference);
          }
        }
      }
      else {
        CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
        if (context.method().isConstructor()) {
          for (UsageInfo usage : usages) {
            PsiElement element = usage.getElement();
            if (element instanceof PsiMethodReferenceExpression ref) {
              inlineMethodReference(context, ref);
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
              GenericInlineHandler.inlineReference(usage, context.method(), inliners);
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
              GenericInlineHandler.inlineReference(usage, context.method(), inliners);
            }
          }
          PsiReferenceExpression[] refs = refExprList.toArray(new PsiReferenceExpression[0]);
          refs = surroundWithCodeBlock(refs, surroundResults);
          for (PsiReferenceExpression ref : refs) {
            if (ref instanceof PsiMethodReferenceExpression methodRef) {
              inlineMethodReference(context, methodRef);
            }
            else {
              inlineMethodCall(context, ref);
            }
          }
          for (PsiElement psiElement : imports2Delete) {
            if (psiElement != null && psiElement.isValid()) {
              psiElement.delete();
            }
          }
        }
        if (context.method().isValid() && context.method().isWritable() && context.deleteTheDeclaration()) {
          CommentTracker tracker = new CommentTracker();
          tracker.markUnchanged(context.method().getBody());
          tracker.markUnchanged(context.method().getDocComment());
          tracker.deleteAndRestoreComments(context.method());
        }
      }
      for (CodeBlockSurrounder.SurroundResult result : surroundResults) {
        result.collapse();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return inlinedReference;
  }

  private static void inlineMethodReference(@NotNull InlineMethodContext context, PsiMethodReferenceExpression reference) {
    final PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(reference, false, false);
    if (lambdaExpression == null) return;
    final PsiExpression callExpression = LambdaUtil.extractSingleExpressionFromBody(lambdaExpression.getBody());
    if (callExpression instanceof PsiMethodCallExpression call) {
      inlineMethodCall(context, call.getMethodExpression());
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

  static void inlineMethodCall(@NotNull InlineMethodContext context, PsiReferenceExpression ref) {
    PsiMethod methodCopy = (PsiMethod)context.method().copy();
    PsiElementFactory factory = context.factory();

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();

    InlineMethodHelper helper = new InlineMethodHelper(context.project(), context.method(), methodCopy, methodCall);
    BlockData blockData = prepareBlock(context, methodCopy, ref, helper);
    PsiCodeBlock block = blockData.block;
    replaceWithAccessors(ref, block);
    ChangeContextUtil.encodeContextInfo(block, false);
    helper.substituteTypes(blockData.parmVars);
    InlineUtil.solveLocalNameConflicts(block, ref, methodCopy.getBody());
    helper.initializeParameters(blockData.parmVars);
    addThisInitializer(context, methodCall, blockData.thisVar);

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
      JavaCodeStyleManager style = JavaCodeStyleManager.getInstance(context.project());

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

    PsiClass thisClass = context.method().getContainingClass();
    PsiExpression thisAccessExpr;
    if (thisVar != null) {
      if (!InlineUtil.canInlineParameterOrThisVariable(thisVar)) {
        thisAccessExpr = factory.createExpressionFromText(thisVar.getName(), null);
      }
      else {
        thisAccessExpr = thisVar.getInitializer();
      }
    }
    else {
      thisAccessExpr = null;
    }
    ChangeContextUtil.decodeContextInfo(anchorParent, thisClass, thisAccessExpr);

    PsiReferenceExpression resultUsage = replaceCall(factory, methodCall, firstAdded, blockData.resultVar);

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
    for (PsiReferenceExpression r: list.reversed()) {
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

  private static void substituteMethodTypeParams(@NotNull PsiElementFactory factory,
                                                 PsiElement scope,
                                                 PsiSubstitutor substitutor) {
    InlineUtil.substituteTypeParams(scope, substitutor, factory);
  }

  private static boolean syncNeeded(@NotNull PsiMethod method, PsiReferenceExpression ref) {
    if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return false;
    final PsiMethod containingMethod = Util.getContainingMethod(ref);
    if (containingMethod == null) return true;
    if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) return true;
    final PsiClass sourceContainingClass = method.getContainingClass();
    final PsiClass targetContainingClass = containingMethod.getContainingClass();
    return !sourceContainingClass.equals(targetContainingClass);
  }

  private static @NotNull BlockData prepareBlock(@NotNull InlineMethodContext context,
                                        @NotNull PsiMethod methodCopy,
                                        PsiReferenceExpression ref,
                                        InlineMethodHelper helper) {
    final PsiCodeBlock block = Objects.requireNonNull(methodCopy.getBody());
    PsiSubstitutor callSubstitutor = helper.getSubstitutor();
    if (callSubstitutor != PsiSubstitutor.EMPTY) {
      substituteMethodTypeParams(context.factory(), block, callSubstitutor);
    }
    final PsiStatement[] originalStatements = block.getStatements();

    PsiType returnType = callSubstitutor.substitute(context.method().getReturnType());
    if (returnType != null) {
      returnType = PsiTypesUtil.removeExternalAnnotations(returnType);
    }
    InlineTransformer transformer = context.transformerChooser().apply(ref);

    PsiLocalVariable[] parmVars = helper.declareParameters();

    PsiLocalVariable thisVar = declareThis(context, callSubstitutor, block);

    addSynchronization(context, ref, block, originalStatements, thisVar);

    PsiLocalVariable resultVar = transformer.transformBody(methodCopy, ref, returnType);

    return new BlockData(block, thisVar, parmVars, resultVar);
  }

  private static @Nullable PsiLocalVariable declareThis(@NotNull InlineMethodContext context,
                                                        PsiSubstitutor callSubstitutor,
                                                        PsiCodeBlock block) {
    PsiMethod method = context.method();
    PsiClass containingClass = method.getContainingClass();
    if (method.hasModifierProperty(PsiModifier.STATIC) || containingClass == null || containingClass instanceof PsiImplicitClass) {
      return null;
    }
    PsiElementFactory factory = context.factory();
    PsiType thisType = GenericsUtil.getVariableTypeByExpressionType(factory.createType(containingClass, callSubstitutor));
    String thisVarName = new VariableNameGenerator(method.getFirstChild(), VariableKind.LOCAL_VARIABLE)
      .byType(thisType).byName("self").generate(true);
    PsiExpression initializer = factory.createExpressionFromText("null", null);
    PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(thisVarName, thisType, initializer);
    declaration = (PsiDeclarationStatement)block.addAfter(declaration, null);
    return (PsiLocalVariable)declaration.getDeclaredElements()[0];
  }

  private static void addSynchronization(@NotNull InlineMethodContext context,
                                         PsiReferenceExpression ref,
                                         PsiCodeBlock block,
                                         PsiStatement[] originalStatements,
                                         PsiLocalVariable thisVar) {
    PsiMethod method = context.method();
    PsiClass containingClass = method.getContainingClass();
    String lockName = null;
    if (thisVar != null) {
      lockName = thisVar.getName();
    }
    else if (method.hasModifierProperty(PsiModifier.STATIC) && containingClass != null) {
      lockName = containingClass.getQualifiedName() + ".class";
    }

    if (lockName != null && syncNeeded(method, ref)) {
      PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)context.factory().createStatementFromText("synchronized(" + lockName + "){}", block);
      synchronizedStatement = (PsiSynchronizedStatement)CodeStyleManager.getInstance(context.project()).reformat(synchronizedStatement);
      synchronizedStatement = (PsiSynchronizedStatement)block.add(synchronizedStatement);
      final PsiCodeBlock synchronizedBody = Objects.requireNonNull(synchronizedStatement.getBody());
      for (PsiStatement originalStatement : originalStatements) {
        synchronizedBody.add(originalStatement);
        originalStatement.delete();
      }
    }
  }

  private static void addThisInitializer(@NotNull InlineMethodContext context,
                                         PsiMethodCallExpression methodCall,
                                         PsiLocalVariable thisVar) {
    if (thisVar != null) {
      PsiElementFactory factory = context.factory();
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
          final PsiClass containingClass = context.method().getContainingClass();
          if (containingClass != null && parentClass.isInheritor(containingClass, true)) {
            String name = parentClass.getName();
            // We cannot have qualified this reference to an anonymous class, so we leave it unqualified
            // this might produce incorrect code in extremely rare cases
            // when we inline a superclass method in an anonymous class,
            // and the method body contains a nested class that refers to the outer one
            qualifier = factory.createExpressionFromText(name == null ? "this" : name + ".this", null);
          }
          else if (containingClass != null && parentClass.equals(containingClass)) {
            qualifier = factory.createExpressionFromText("this", null);
          }
          else {
            if (PsiTreeUtil.isAncestor(containingClass, parent, false)) {
              String name = containingClass.getName();
              if (name != null) {
                qualifier = factory.createExpressionFromText(name + ".this", null);
              }
              else { //?
                qualifier = factory.createExpressionFromText("this", null);
              }
            } else { // we are inside the inheritor
              do {
                parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
                if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                  final String childClassName = parentClass.getName();
                  qualifier = factory.createExpressionFromText(childClassName != null ? childClassName + ".this" : "this", null);
                  break;
                }
              }
              while (parentClass != null);
            }
          }
        }
        else {
          qualifier = factory.createExpressionFromText("this", null);
        }
      }
      else if (qualifier instanceof PsiSuperExpression) {
        qualifier = factory.createExpressionFromText("this", null);
      }
      else if (qualifier.getType() != null && !thisVar.getType().isAssignableFrom(qualifier.getType())) {
        PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(A)b", null);
        Objects.requireNonNull(cast.getOperand()).replace(qualifier);
        Objects.requireNonNull(cast.getCastType()).replace(factory.createTypeElement(thisVar.getType()));
        qualifier = cast;
      }
      Objects.requireNonNull(thisVar.getInitializer()).replace(qualifier);
    }
  }

  private static final Key<PsiReferenceExpression> MARK_KEY = Key.create("MarkForSurround");

  private static PsiReferenceExpression[] surroundWithCodeBlock(PsiReferenceExpression[] refs,
                                                                @NotNull List<CodeBlockSurrounder.SurroundResult> surroundResults) {
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
        surroundResults.add(surround);
      }
    }

    return StreamEx.of(refs).map(ref -> visitor.mapping.getOrDefault(ref, ref))
      .peek(ref -> ref.putCopyableUserData(MARK_KEY, null))
      .toArray(new PsiReferenceExpression[0]);
  }

  /**
   * Holds the values that stay the same during one "Inline Method" refactoring.
   * @param method - The method to be inlined.
   * @param reference - The reference to the {@link method} to be inlined. It is null if the inline was called on the method declaration.
   * @param transformerChooser - The provider of {@link InlineTransformer} for a given {@link PsiReference}.
   * @param inlineThisOnly - Whether to inline only this {@link reference} of {@link method}.
   * @param deleteTheDeclaration - Whether to delete the {@link method} declaration after inlining.
   */
  @ApiStatus.Internal
  public record InlineMethodContext(@NotNull PsiMethod method,
                                    @Nullable PsiReference reference,
                                    @NotNull Function<PsiReference, InlineTransformer> transformerChooser,
                                    boolean inlineThisOnly,
                                    boolean deleteTheDeclaration) {
    public @NotNull PsiElementFactory factory() {
      return JavaPsiFacade.getElementFactory(project());
    }

    public @NotNull Project project() {
      return method.getProject();
    }
  }

  private record BlockData(PsiCodeBlock block, PsiLocalVariable thisVar, PsiLocalVariable[] parmVars, PsiLocalVariable resultVar) {}

  static class OverrideAttributeUsageInfo extends UsageInfo {
    OverrideAttributeUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }
}
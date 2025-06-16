// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.TrackingEquivalenceChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public final class CodeInsightUtil extends CodeInsightFrontbackUtil {
  private static final Logger LOG = Logger.getInstance(CodeInsightUtil.class);

  public static <T extends PsiMember & PsiDocCommentOwner> void sortIdenticalShortNamedMembers(@NotNull T @NotNull [] members, @NotNull PsiReference context) {
    if (members.length <= 1) return;

    PsiElement leaf = context.getElement().getFirstChild(); // the same proximity weighers are used in completion, where the leafness is critical
    final Comparator<T> comparator = createSortIdenticalNamedMembersComparator(leaf);
    Arrays.sort(members, comparator);
  }

  public static <T extends PsiMember & PsiDocCommentOwner> Comparator<T> createSortIdenticalNamedMembersComparator(PsiElement place) {
    return Comparator
      .<T, Boolean>comparing(JavaCompletionUtil::isEffectivelyDeprecated)
      .thenComparing(CodeInsightUtil::isInnerClass)
      .thenComparing(new PsiProximityComparator(place))
      .thenComparing(CodeInsightUtil::compareQualifiedNames);
  }

  private static boolean isInnerClass(PsiMember o) {
    return o instanceof PsiClass && o.getContainingClass() != null;
  }

  private static int compareQualifiedNames(PsiMember o1, PsiMember o2) {
    String qname1 = o1 instanceof PsiClass ? ((PsiClass)o1).getQualifiedName() : null;
    String qname2 = o2 instanceof PsiClass ? ((PsiClass)o2).getQualifiedName() : null;
    if (qname1 == null || qname2 == null) return 0;
    return qname1.compareToIgnoreCase(qname2);
  }

  public static PsiExpression @NotNull [] findExpressionOccurrences(PsiElement scope, PsiExpression expr) {
    List<PsiExpression> array = new ArrayList<>();
    TrackingEquivalenceChecker equivalenceChecker = new TrackingEquivalenceChecker();
    addExpressionOccurrences(CommonJavaRefactoringUtil.unparenthesizeExpression(expr), array, scope, equivalenceChecker);
    if (expr.isPhysical()) {
      boolean found = false;
      for (PsiExpression psiExpression : array) {
        if (PsiTreeUtil.isAncestor(expr, psiExpression, false) || PsiTreeUtil.isAncestor(psiExpression, expr, false)) {
          found = true;
          break;
        }
      }
      if (!found) array.add(expr);
    }
    return array.toArray(PsiExpression.EMPTY_ARRAY);
  }

  private static void addExpressionOccurrences(PsiExpression expr, List<? super PsiExpression> array, PsiElement scope, EquivalenceChecker equivalenceChecker) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        PsiExpression unparenthesized = CommonJavaRefactoringUtil.unparenthesizeExpression((PsiExpression)child);
        if (equivalenceChecker.expressionsAreEquivalent(unparenthesized, expr)) {
          array.add((PsiExpression)child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child, equivalenceChecker);
    }
  }

  public static PsiExpression @NotNull [] findReferenceExpressions(PsiElement scope, PsiElement referee) {
    if (scope == null) return PsiExpression.EMPTY_ARRAY;
    List<PsiExpression> array = new ArrayList<>();
    addReferenceExpressions(array, scope, referee);
    return array.toArray(PsiExpression.EMPTY_ARRAY);
  }

  private static void addReferenceExpressions(List<? super PsiExpression> array, PsiElement scope, PsiElement referee) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiReferenceExpression) {
        PsiElement ref = ((PsiReferenceExpression)child).resolve();
        if (ref != null && PsiEquivalenceUtil.areElementsEquivalent(ref, referee)) {
          array.add((PsiExpression)child);
        }
      }
      addReferenceExpressions(array, child, referee);
    }
  }

  public static Editor positionCursorAtLBrace(final Project project, PsiFile targetFile, @NotNull PsiClass psiClass) {
    final PsiElement lBrace = psiClass.getLBrace();
    return positionCursor(project, targetFile, lBrace != null ? lBrace : psiClass);
  }

  public static @Nullable("no virtual file is associated with a targetFile") Editor positionCursor(@NotNull Project project, @NotNull PsiFile targetFile, @NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    LOG.assertTrue(range != null, element.getClass());
    int textOffset = range.getStartOffset();
    if (IntentionPreviewUtils.isPreviewElement(targetFile)) {
      Editor editor = IntentionPreviewUtils.getPreviewEditor();
      if (editor != null) {
        editor.getCaretModel().moveToOffset(textOffset);
      }
      return editor;
    }
    VirtualFile file = targetFile.getVirtualFile();
    if (file == null) {
      file = PsiUtilCore.getVirtualFile(element);
      if (file == null) return null;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, textOffset);
    descriptor.setScrollType(ScrollType.MAKE_VISIBLE); // avoid centering caret in editor if it's already visible
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean preparePsiElementsForWrite(PsiElement @NotNull ... elements) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(elements));
  }

  public static void processSubTypes(PsiType psiType,
                                     final PsiElement context,
                                     boolean getRawSubtypes,
                                     final @NotNull PrefixMatcher matcher,
                                     Consumer<? super PsiType> consumer) {
    int arrayDim = psiType.getArrayDimensions();

    psiType = psiType.getDeepComponentType();
    if (!(psiType instanceof PsiClassType)) return;

    PsiClassType baseType = JavaCompletionUtil.originalize((PsiClassType)psiType);
    PsiClassType.ClassResolveResult baseResult = baseType.resolveGenerics();
    PsiClass baseClass = baseResult.getElement();
    if(baseClass == null) return;

    GlobalSearchScope scope = context.getResolveScope();

    Object lock = ObjectUtils.sentinel("CodeInsightUtil.processSubTypes");
    Processor<PsiClass> inheritorProcessor = inheritor -> {
      PsiType toAdd = getSubTypeBySubClass(context, baseType, arrayDim, getRawSubtypes, baseClass, inheritor);
      if (toAdd != null) {
        synchronized (lock) {
          consumer.consume(toAdd);
        }
      }
      return true;
    };

    addContextTypeArguments(context, baseType, inheritorProcessor);

    if (baseClass.hasModifierProperty(PsiModifier.FINAL)) return;

    Set<PsiClass> imported = processImportedInheritors(context, baseClass, inheritorProcessor);

    if (matcher.getPrefix().length() > 2) {
      JBTreeTraverser<PsiClass> traverser = JBTreeTraverser.of(PsiClass::getInnerClasses);
      AllClassesGetter.processJavaClasses(matcher, context.getProject(), scope, psiClass -> {
        Iterable<PsiClass> inheritors = traverser.withRoot(psiClass).filter(c -> c.isInheritor(baseClass, true) && !imported.contains(c));
        return ContainerUtil.process(inheritors, inheritorProcessor);
      });
    }
    else {
      Query<PsiClass> baseQuery = ClassInheritorsSearch.search(baseClass, scope, true, true, false);
      Query<PsiClass> query = new FilteredQuery<>(baseQuery, psiClass ->
        !(psiClass instanceof PsiTypeParameter) &&
        psiClass.getName() != null &&
        ContainerUtil.exists(JavaCompletionUtil.getAllLookupStrings(psiClass), matcher::prefixMatches) &&
        !imported.contains(psiClass));
      query.allowParallelProcessing().forEach(inheritorProcessor);
    }
  }

  private static @NotNull Set<PsiClass> processImportedInheritors(PsiElement context, PsiClass baseClass, Processor<? super PsiClass> inheritorsProcessor) {
    Set<PsiClass> visited = new HashSet<>();

    context.getContainingFile().getOriginalFile().processDeclarations(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiClass && ((PsiClass)element).isInheritor(baseClass, true) && visited.add((PsiClass)element)) {
          return inheritorsProcessor.process((PsiClass)element);
        }
        return true;
      }
    }, ResolveState.initial(), null, context);
    return visited;
  }

  private static void addContextTypeArguments(PsiElement context, PsiClassType baseType, Processor<? super PsiClass> inheritorsProcessor) {
    Set<String> usedNames = new HashSet<>();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiElement each = context;
    while (true) {
      PsiTypeParameterListOwner typed = PsiTreeUtil.getParentOfType(each, PsiTypeParameterListOwner.class);
      if (typed == null) break;
      for (PsiTypeParameter parameter : typed.getTypeParameters()) {
        if (baseType.isAssignableFrom(factory.createType(parameter)) && usedNames.add(parameter.getName())) {
          inheritorsProcessor.process(CompletionUtil.getOriginalOrSelf(parameter));
        }
      }

      each = typed;
    }
  }

  @ApiStatus.Internal
  public static @Nullable PsiType getSubTypeBySubClass(@NotNull PsiElement context,
                                                       @NotNull PsiClassType baseType,
                                                       int arrayDim,
                                                       boolean raw,
                                                       @NotNull PsiClass baseClass,
                                                       @NotNull PsiClass inheritor) {
    Project project = baseClass.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (!PsiResolveHelper.getInstance(project).isAccessible(inheritor, context, null)) {
      return null;
    }

    if (inheritor.getQualifiedName() == null &&
        !baseClass.getManager().areElementsEquivalent(inheritor.getContainingFile(), context.getContainingFile().getOriginalFile())) {
      return null;
    }

    if (JavaCompletionUtil.isInExcludedPackage(inheritor, false)) return null;

    PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return null;

    List<PsiType> typeArgs = raw ? null : getExpectedTypeArgs(context, inheritor, Arrays.asList(inheritor.getTypeParameters()), baseType);
    PsiClassType inheritorType = typeArgs == null || typeArgs.contains(null)
                                 ? factory.createType(inheritor, factory.createRawSubstitutor(inheritor))
                                 : factory.createType(inheritor, typeArgs.toArray(PsiType.EMPTY_ARRAY));
    PsiType result = PsiTypesUtil.createArrayType(inheritorType, arrayDim);
    return baseType.isAssignableFrom(result) ? result : null;
  }

  public static @Unmodifiable @NotNull List<PsiType> getExpectedTypeArgs(PsiElement context,
                                                                         PsiTypeParameterListOwner paramOwner,
                                                                         Iterable<? extends PsiTypeParameter> typeParams, PsiClassType expectedType) {
    if (paramOwner instanceof PsiClass) {
      return GenericsUtil.getExpectedTypeArguments(context, (PsiClass)paramOwner, typeParams, expectedType);
    }

    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor((PsiMethod)paramOwner, expectedType);
    return ContainerUtil.map(typeParams, substitutor::substitute);
  }
}

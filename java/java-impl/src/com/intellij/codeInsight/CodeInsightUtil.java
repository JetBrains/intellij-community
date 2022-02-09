// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CodeInsightUtil {
  private static final Logger LOG = Logger.getInstance(CodeInsightUtil.class);

  @Nullable
  public static PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) return null;
    PsiExpression expression = findElementInRange(file, startOffset, endOffset, PsiExpression.class);
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, JavaLanguage.INSTANCE);
      if (element2 instanceof PsiJavaToken) {
        final PsiJavaToken token = (PsiJavaToken)element2;
        final IElementType tokenType = token.getTokenType();
        if (tokenType.equals(JavaTokenType.SEMICOLON) || element2.getParent() instanceof PsiErrorElement) {
          expression = findElementInRange(file, startOffset, element2.getTextRange().getStartOffset(), PsiExpression.class);
        }
      }
    }
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element = PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(endOffset));
      if (element != null) {
        element = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element.getLastChild());
        if (element != null) {
          final int newEndOffset = element.getTextRange().getEndOffset();
          if (newEndOffset < endOffset) {
            expression = findExpressionInRange(file, startOffset, newEndOffset);
          }
        }
      }
    }
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) return null;
    return expression;
  }

  public static <T extends PsiElement> T findElementInRange(PsiFile file, int startOffset, int endOffset, Class<T> klass) {
    return CodeInsightUtilCore.findElementInRange(file, startOffset, endOffset, klass, JavaLanguage.INSTANCE);
  }

  public static PsiElement @NotNull [] findStatementsInRange(@NotNull PsiFile file, int startOffset, int endOffset) {
    Language language = findJavaOrLikeLanguage(file);
    if (language == null) return PsiElement.EMPTY_ARRAY;
    FileViewProvider viewProvider = file.getViewProvider();
    PsiElement element1 = viewProvider.findElementAt(startOffset, language);
    PsiElement element2 = viewProvider.findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof PsiStatement) {
        if (!(element1 instanceof PsiComment)) {
          parent = parent.getParent();
        }
        break;
      }
      if (parent instanceof PsiCodeBlock) break;
      if (FileTypeUtils.isInServerPageFile(parent) && parent instanceof PsiFile) break;
      if (parent instanceof PsiCodeFragment) break;
      if (parent == null || parent instanceof PsiFile) return PsiElement.EMPTY_ARRAY;
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset()) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset() && !isAtTrailingComment(element1, element2, endOffset)) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (parent instanceof PsiCodeBlock &&
        element1 == ((PsiCodeBlock)parent).getLBrace() && element2 == ((PsiCodeBlock)parent).getRBrace()) {
      if (parent.getParent() instanceof PsiBlockStatement) {
        return new PsiElement[]{parent.getParent()};
      }
      PsiElement[] children = parent.getChildren();
      return getStatementsInRange(children, ((PsiCodeBlock)parent).getFirstBodyElement(), ((PsiCodeBlock)parent).getLastBodyElement());
    }

    PsiElement[] children = parent.getChildren();
    return getStatementsInRange(children, element1, element2);
  }

  private static boolean isAtTrailingComment(PsiElement element1, PsiElement element2, int offset) {
    if (element1 == element2 && element1 instanceof PsiExpressionStatement) {
      for (PsiElement child = element1.getLastChild(); child != null; child = child.getPrevSibling()) {
        if (PsiUtil.isJavaToken(child, JavaTokenType.SEMICOLON) && child.getTextRange().getEndOffset() == offset) {
          return false; // findExpressionInRange() counts this as an expression - don't interfere with it
        }
      }
    }
    PsiElement trailing = element2;
    while (trailing.getTextRange().contains(offset) && trailing.getLastChild() != null) {
      trailing = trailing.getLastChild();
    }
    while (trailing instanceof PsiComment || trailing instanceof PsiWhiteSpace) {
      PsiElement previous = trailing.getPrevSibling();
      if (trailing.getTextRange().contains(offset)) {
        return true;
      }
      trailing = previous;
    }
    return false;
  }

  private static PsiElement @NotNull [] getStatementsInRange(PsiElement[] children, PsiElement element1, PsiElement element2) {
    ArrayList<PsiElement> array = new ArrayList<>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PsiStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        return PsiElement.EMPTY_ARRAY;
      }
    }

    return PsiUtilCore.toPsiElementArray(array);
  }

  @Nullable
  public static Language findJavaOrLikeLanguage(@NotNull final PsiFile file) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    for (final Language language : languages) {
      if (language == JavaLanguage.INSTANCE) return language;
    }
    for (final Language language : languages) {
      if (language.isKindOf(JavaLanguage.INSTANCE)) return language;
    }
    return null;
  }

  public static <T extends PsiMember & PsiDocCommentOwner> void sortIdenticalShortNamedMembers(T[] members, @NotNull PsiReference context) {
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
    addExpressionOccurrences(CommonJavaRefactoringUtil.unparenthesizeExpression(expr), array, scope);
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

  private static void addExpressionOccurrences(PsiExpression expr, List<? super PsiExpression> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(CommonJavaRefactoringUtil.unparenthesizeExpression((PsiExpression)child), expr)) {
          array.add((PsiExpression)child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child);
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

  public static Editor positionCursor(final Project project, PsiFile targetFile, @NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    LOG.assertTrue(range != null, "element: " + element + "; valid: " + element.isValid());
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean preparePsiElementsForWrite(PsiElement @NotNull ... elements) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(elements));
  }

  public static void processSubTypes(PsiType psiType,
                                     final PsiElement context,
                                     boolean getRawSubtypes,
                                     @NotNull final PrefixMatcher matcher,
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

  @NotNull
  private static Set<PsiClass> processImportedInheritors(PsiElement context, PsiClass baseClass, Processor<? super PsiClass> inheritorsProcessor) {
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

  @Nullable
  @ApiStatus.Internal
  public static PsiType getSubTypeBySubClass(@NotNull PsiElement context,
                                             @NotNull PsiClassType baseType,
                                             int arrayDim,
                                             boolean raw,
                                             @NotNull PsiClass baseClass,
                                             @NotNull PsiClass inheritor) {
    Project project = baseClass.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (!PsiResolveHelper.SERVICE.getInstance(project).isAccessible(inheritor, context, null)) {
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

  @NotNull
  public static List<PsiType> getExpectedTypeArgs(PsiElement context,
                                                  PsiTypeParameterListOwner paramOwner,
                                                  Iterable<? extends PsiTypeParameter> typeParams, PsiClassType expectedType) {
    if (paramOwner instanceof PsiClass) {
      return GenericsUtil.getExpectedTypeArguments(context, (PsiClass)paramOwner, typeParams, expectedType);
    }

    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor((PsiMethod)paramOwner, expectedType);
    return ContainerUtil.map(typeParams, substitutor::substitute);
  }
}

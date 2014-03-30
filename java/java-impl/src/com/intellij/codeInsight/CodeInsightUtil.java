/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CodeInsightUtil {
  @Nullable
  public static PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
    if (!file.getViewProvider().getLanguages().contains(StdLanguages.JAVA)) return null;
    PsiExpression expression = findElementInRange(file, startOffset, endOffset, PsiExpression.class);
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, StdLanguages.JAVA);
      if (element2 instanceof PsiJavaToken) {
        final PsiJavaToken token = (PsiJavaToken)element2;
        final IElementType tokenType = token.getTokenType();
        if (tokenType.equals(JavaTokenType.SEMICOLON)) {
          expression = findElementInRange(file, startOffset, element2.getTextRange().getStartOffset(), PsiExpression.class);
        }
      }
    }
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element = PsiTreeUtil.skipSiblingsBackward(file.findElementAt(endOffset), PsiWhiteSpace.class);
      if (element != null) {
        element = PsiTreeUtil.skipSiblingsBackward(element.getLastChild(), PsiWhiteSpace.class, PsiComment.class);
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
    return CodeInsightUtilCore.findElementInRange(file, startOffset, endOffset, klass, StdLanguages.JAVA);
  }

  @NotNull
  public static PsiElement[] findStatementsInRange(@NotNull PsiFile file, int startOffset, int endOffset) {
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
        parent = parent.getParent();
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
    if (endOffset != element2.getTextRange().getEndOffset()) return PsiElement.EMPTY_ARRAY;

    if (parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement &&
        element1 == ((PsiCodeBlock)parent).getLBrace() && element2 == ((PsiCodeBlock)parent).getRBrace()) {
      return new PsiElement[]{parent.getParent()};
    }

/*
    if(parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiBlockStatement) {
      return new PsiElement[]{parent.getParent()};
    }
*/

    PsiElement[] children = parent.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
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
      if (language == StdLanguages.JAVA) return language;
    }
    for (final Language language : languages) {
      if (language.isKindOf(StdLanguages.JAVA)) return language;
    }
    return null;
  }

  public static void sortIdenticalShortNameClasses(PsiClass[] classes, @NotNull PsiReference context) {
    if (classes.length <= 1) return;

    PsiElement leaf = context.getElement().getFirstChild(); // the same proximity weighers are used in completion, where the leafness is critical
    Arrays.sort(classes, new PsiProximityComparator(leaf));
  }

  public static PsiExpression[] findExpressionOccurrences(PsiElement scope, PsiExpression expr) {
    List<PsiExpression> array = new ArrayList<PsiExpression>();
    addExpressionOccurrences(RefactoringUtil.unparenthesizeExpression(expr), array, scope);
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
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addExpressionOccurrences(PsiExpression expr, List<PsiExpression> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(RefactoringUtil.unparenthesizeExpression((PsiExpression)child), expr)) {
          array.add((PsiExpression)child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child);
    }
  }

  public static PsiExpression[] findReferenceExpressions(PsiElement scope, PsiElement referee) {
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    if (scope != null) {
      addReferenceExpressions(array, scope, referee);
    }
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addReferenceExpressions(ArrayList<PsiElement> array, PsiElement scope, PsiElement referee) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiReferenceExpression) {
        PsiElement ref = ((PsiReferenceExpression)child).resolve();
        if (ref != null && PsiEquivalenceUtil.areElementsEquivalent(ref, referee)) {
          array.add(child);
        }
      }
      addReferenceExpressions(array, child, referee);
    }
  }

  public static Editor positionCursor(final Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean preparePsiElementsForWrite(@NotNull PsiElement... elements) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(elements));
  }

  public static void processSubTypes(PsiType psiType,
                                     final PsiElement context,
                                     boolean getRawSubtypes,
                                     @NotNull final PrefixMatcher matcher,
                                     Consumer<PsiType> consumer) {
    int arrayDim = psiType.getArrayDimensions();

    psiType = psiType.getDeepComponentType();
    if (!(psiType instanceof PsiClassType)) return;

    final Condition<String> shortNameCondition = new Condition<String>() {
      @Override
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    final PsiClassType baseType = (PsiClassType)psiType;
    final PsiClassType.ClassResolveResult baseResult =
      ApplicationManager.getApplication().runReadAction(new Computable<PsiClassType.ClassResolveResult>() {
        @Override
        public PsiClassType.ClassResolveResult compute() {
          return JavaCompletionUtil.originalize(baseType).resolveGenerics();
        }
      });
    final PsiClass baseClass = baseResult.getElement();
    final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();
    if(baseClass == null) return;

    final GlobalSearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      @Override
      public GlobalSearchScope compute() {
        return context.getResolveScope();
      }
    });

    final Processor<PsiClass> inheritorsProcessor =
      createInheritorsProcessor(context, baseType, arrayDim, getRawSubtypes, consumer, baseClass, baseSubstitutor);
    if (matcher.getPrefix().length() > 2) {
      AllClassesGetter.processJavaClasses(matcher, context.getProject(), scope, new Processor<PsiClass>() {
        @Override
        public boolean process(PsiClass psiClass) {
          if (psiClass.isInheritor(baseClass, true)) {
            return inheritorsProcessor.process(psiClass);
          }
          return true;
        }
      });
    } else {
      final Query<PsiClass> baseQuery = ClassInheritorsSearch.search(
        new ClassInheritorsSearch.SearchParameters(baseClass, scope, true, false, false, shortNameCondition));
      final Query<PsiClass> query = new FilteredQuery<PsiClass>(baseQuery, new Condition<PsiClass>() {
        @Override
        public boolean value(final PsiClass psiClass) {
          return !(psiClass instanceof PsiTypeParameter);
        }
      });
      query.forEach(inheritorsProcessor);
    }

  }

  public static Processor<PsiClass> createInheritorsProcessor(final PsiElement context, final PsiClassType baseType,
                                                               final int arrayDim,
                                                               final boolean getRawSubtypes,
                                                               final Consumer<PsiType> result, @NotNull final PsiClass baseClass, final PsiSubstitutor baseSubstitutor) {
    final PsiManager manager = context.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();

    return new Processor<PsiClass>() {
      @Override
      public boolean process(final PsiClass inheritor) {
        ProgressManager.checkCanceled();

        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            if (!context.isValid() || !inheritor.isValid() || !facade.getResolveHelper().isAccessible(inheritor, context, null))
              return true;

            if (inheritor.getQualifiedName() == null &&
                !manager.areElementsEquivalent(inheritor.getContainingFile(), context.getContainingFile().getOriginalFile())) {
              return true;
            }

            if (JavaCompletionUtil.isInExcludedPackage(inheritor, false)) return true;

            PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
            if (superSubstitutor == null) return true;
            if (getRawSubtypes) {
              result.consume(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
              return true;
            }

            PsiSubstitutor inheritorSubstitutor = PsiSubstitutor.EMPTY;
            for (PsiTypeParameter inheritorParameter : PsiUtil.typeParametersIterable(inheritor)) {
              for (PsiTypeParameter baseParameter : PsiUtil.typeParametersIterable(baseClass)) {
                final PsiType substituted = superSubstitutor.substitute(baseParameter);
                PsiType arg = baseSubstitutor.substitute(baseParameter);
                if (arg instanceof PsiWildcardType) {
                  PsiType bound = ((PsiWildcardType)arg).getBound();
                  arg = bound != null ? bound : ((PsiWildcardType)arg).getExtendsBound();
                }
                PsiType substitution = resolveHelper.getSubstitutionForTypeParameter(inheritorParameter,
                                                                                     substituted,
                                                                                     arg,
                                                                                     true,
                                                                                     PsiUtil.getLanguageLevel(context));
                if (PsiType.NULL.equals(substitution) || substitution instanceof PsiWildcardType) continue;
                if (substitution == null) {
                  result.consume(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
                  return true;
                }
                inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
                break;
              }
            }

            PsiType toAdd = createType(inheritor, inheritorSubstitutor, arrayDim);
            if (baseType.isAssignableFrom(toAdd)) {
              result.consume(toAdd);
            }
            return true;
          }
        }).booleanValue();
      }
    };
  }

  private static PsiType createType(PsiClass cls,
                             PsiSubstitutor currentSubstitutor,
                             int arrayDim) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(cls.getProject()).getElementFactory();
    PsiType newType = elementFactory.createType(cls, currentSubstitutor);
    for(int i = 0; i < arrayDim; i++){
      newType = newType.createArrayType();
    }
    return newType;
  }
}

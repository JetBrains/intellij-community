/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.TailType;
import com.intellij.patterns.PsiJavaElementPattern;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.classes.ThisOrAnyInnerFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.getters.AllClassesGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaClassNameCompletionContributor extends CompletionContributor{
  @NonNls public static final String VARIABLE_NAME = "VARIABLE_NAME";
  @NonNls public static final String METHOD_NAME = "METHOD_NAME";
  @NonNls public static final String JAVA_LEGACY = "JAVA_LEGACY";
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_THROW_NEW = psiElement().afterLeaf(
      psiElement().withText(PsiKeyword.NEW).afterLeaf(PsiKeyword.THROW));
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(PsiKeyword.NEW);
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
      psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.SUPER, "&").withParent(
          psiElement(PsiReferenceList.class).withParent(PsiTypeParameter.class));
  private static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_METHOD_THROWS_CLAUSE = psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(
      PsiMethod.class).andNot(psiElement().inside(PsiCodeBlock.class)).andNot(psiElement().inside(PsiParameterList.class));

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(CompletionType.CLASS_NAME, psiElement()).withId(JAVA_LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        PsiElement insertedElement = parameters.getPosition();
        result.setPrefixMatcher(CompletionData.findPrefixStatic(insertedElement, context.getStartOffset()));

        AllClassesGetter getter = new AllClassesGetter(TrueFilter.INSTANCE);
        if (AFTER_NEW.accepts(insertedElement)) {
          ElementFilter filter;
          if (AFTER_THROW_NEW.accepts(insertedElement)) {
            filter = new AssignableFromFilter("java.lang.Throwable");
          } else {
            filter = TrueFilter.INSTANCE;
          }
          getter = new AllClassesGetterAfterNew(filter);
        }
        else if (IN_TYPE_PARAMETER.accepts(insertedElement)) {
          getter = new AllClassesGetter(new ExcludeDeclaredFilter(new ClassFilter(PsiTypeParameter.class)));
        }
        else if (INSIDE_METHOD_THROWS_CLAUSE.accepts(insertedElement)) {
          getter = new AllClassesGetter(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")));
        }

        getter.getClasses(insertedElement, context, result);
      }
    });

  }


  private static class AllClassesGetterAfterNew extends AllClassesGetter {
    private static final TailType PARENS_WITH_PARAMS = new TailType() {
      public int processTail(final Editor editor, final int tailOffset) {
        final int offset = editor.getCaretModel().getOffset();
        if (!editor.getDocument().getText().substring(offset).startsWith("()")) {
          editor.getDocument().insertString(offset, "()");
        }
        editor.getCaretModel().moveToOffset(offset + 1);
        return offset + 1;
      }
    };
    private static final TailType PARENS_NO_PARAMS = new TailType() {
      public int processTail(final Editor editor, final int tailOffset) {
        final int offset = editor.getCaretModel().getOffset();
        if (!editor.getDocument().getText().substring(offset).startsWith("()")) {
          editor.getDocument().insertString(offset, "()");
        }
        editor.getCaretModel().moveToOffset(offset + 2);
        return offset + 2;
      }
    };

    public AllClassesGetterAfterNew(final ElementFilter filter) {
      super(filter);
    }

    protected LookupItem<PsiClass> createLookupItem(final PsiClass psiClass) {
      return super.createLookupItem(psiClass).setTailType(hasParams(psiClass) ? PARENS_WITH_PARAMS : PARENS_NO_PARAMS);
    }

    private static boolean hasParams(PsiClass psiClass) {
      for (final PsiMethod method : psiClass.getConstructors()) {
        if (method.getParameterList().getParameters().length > 0) return true;
      }
      return false;
    }
  }
}
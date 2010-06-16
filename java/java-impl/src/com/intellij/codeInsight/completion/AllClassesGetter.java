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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 02.12.2003
 * Time: 16:49:25
 * To change this template use Options | File Templates.
 */
public class AllClassesGetter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.AllClassesGetter");
  private final ElementFilter myFilter;
  private static final InsertHandler<JavaPsiClassReferenceElement> INSERT_HANDLER = new InsertHandler<JavaPsiClassReferenceElement>() {

    private void _handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      final Editor editor = context.getEditor();
      final PsiClass psiClass = item.getObject();
      if (!psiClass.isValid()) return;

      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) return;

      if (endOffset == 0) return;

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(psiClass.getProject());
      final PsiFile file = context.getFile();
      if (file.findElementAt(endOffset - 1) == null) return;

      final OffsetKey key = OffsetKey.create("endOffset", false);
      context.getOffsetMap().addOffset(key, endOffset);
      ClassNameInsertHandler handler = ClassNameInsertHandler.EP_NAME.forLanguage(file.getLanguage());
      ClassNameInsertHandlerResult checkReference = ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE;
      if (handler != null) {
        checkReference = handler.handleInsert(context, item);
      }

      final int newOffset = context.getOffsetMap().getOffset(key);
      if (newOffset >= 0) {
        endOffset = newOffset;
      }
      else {
        LOG.error(endOffset + " became invalid: " + context.getOffsetMap() + "; inserting " + qname);
      }

      final RangeMarker toDelete = DefaultInsertHandler.insertSpace(endOffset, document);
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);

      boolean insertFqn=checkReference!=ClassNameInsertHandlerResult.REFERENCE_CORRECTED;
      if (checkReference == ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE && psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, DefaultInsertHandler.resolveReference(psiReference))) {
          insertFqn = false;
        }
        else if (psiClass.isValid()) {
          try {
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                for (final PsiReference reference : psiElement.getReferences()) {
                  if (psiManager.areElementsEquivalent(psiClass, DefaultInsertHandler.resolveReference(reference))) {
                    insertFqn = false;
                    endOffset = reference.getRangeInElement().getEndOffset() + reference.getElement().getTextRange().getStartOffset();
                    break;
                  }
                }
              }
            }
          }
          catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (toDelete.isValid()) {
        document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        if (insertFqn) {
          endOffset = toDelete.getStartOffset();
        }
      }

      if (insertFqn) {
        int i = endOffset - 1;
        while (i >= 0) {
          final char ch = document.getCharsSequence().charAt(i);
          if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
          i--;
        }
        document.replaceString(i + 1, endOffset, qname);
      }
    }

    public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      context.setAddCompletionChar(false);
      _handleInsert(context, item);
      item.getTailType().processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
    }

  };

  public AllClassesGetter(final ElementFilter filter) {
    myFilter = filter;
  }

  public void getClasses(final PsiElement context, final CompletionResultSet set, final int offset, final boolean filterByScope) {
    if (context == null || !context.isValid()) return;

    final boolean lookingForAnnotations = PsiJavaPatterns.psiElement().afterLeaf("@").accepts(context);
    getClasses(context, set, offset, filterByScope, lookingForAnnotations);
  }

  public void getClasses(final PsiElement context,
                         final CompletionResultSet set,
                         final int offset,
                         final boolean filterByScope,
                         final boolean lookingForAnnotations) {
    if (context == null || !context.isValid()) return;

    final String packagePrefix = getPackagePrefix(context, offset);

    final Set<String> qnames = new THashSet<String>();

    final GlobalSearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<GlobalSearchScope>() {
      public GlobalSearchScope compute() {
        return filterByScope ? context.getContainingFile().getResolveScope() : GlobalSearchScope.allScope(context.getProject());
      }
    });
    final PrefixMatcher prefixMatcher = set.getPrefixMatcher();

    AllClassesSearch.search(scope, context.getProject(), new Condition<String>() {
      public boolean value(String s) {
        return prefixMatcher.prefixMatches(s);
      }
    }).forEach(new Processor<PsiClass>() {
      public boolean process(PsiClass psiClass) {
        assert psiClass != null;
        if (isSuitable(context, packagePrefix, qnames, lookingForAnnotations, psiClass, filterByScope)) {
          set.addElement(createLookupItem(psiClass));
        }
        return true;
      }
    });
  }

  private static String getPackagePrefix(final PsiElement context, final int offset) {
    final String fileText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        ProgressManager.checkCanceled();
        return context.getContainingFile().getText();
      }
    });
    int i = offset - 1;
    while (i >= 0) {
      final char c = fileText.charAt(i);
      if (!Character.isJavaIdentifierPart(c) && c != '.') break;
      i--;
    }
    String prefix = fileText.substring(i + 1, offset);
    final int j = prefix.lastIndexOf('.');
    return j > 0 ? prefix.substring(0, j) : "";
  }

  private boolean isSuitable(@NotNull final PsiElement context, final String packagePrefix, final Set<String> qnames,
                             final boolean lookingForAnnotations,
                             @NotNull final PsiClass psiClass,
                             final boolean filterByScope) {
    //noinspection AutoUnboxing
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        ProgressManager.checkCanceled();

        if (!context.isValid() || !psiClass.isValid()) return false;

        if (lookingForAnnotations && !psiClass.isAnnotationType()) return false;

        if (JavaCompletionUtil.isInExcludedPackage(psiClass)) return false;

        final String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.startsWith(packagePrefix)) return false;

        if (!myFilter.isAcceptable(psiClass, context)) return false;

        if (!(psiClass instanceof PsiCompiledElement) || !filterByScope ||
            JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().isAccessible(psiClass, context, psiClass)) {
          return qnames.add(qualifiedName);
        }
        return false;

      }
    });
  }

  public static LookupElement createLookupItem(@NotNull final PsiClass psiClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<LookupElement>() {
      public LookupElement compute() {
        return new JavaPsiClassReferenceElement(psiClass).setInsertHandler(INSERT_HANDLER);
      }
    });
  }


  public interface ClassNameInsertHandler {
    LanguageExtension<ClassNameInsertHandler> EP_NAME =
      new LanguageExtension<ClassNameInsertHandler>("com.intellij.classNameInsertHandler");

    ClassNameInsertHandlerResult handleInsert(InsertionContext context, JavaPsiClassReferenceElement item);
  }

  public enum ClassNameInsertHandlerResult {
    INSERT_FQN, REFERENCE_CORRECTED, CHECK_FOR_CORRECT_REFERENCE
  }

}

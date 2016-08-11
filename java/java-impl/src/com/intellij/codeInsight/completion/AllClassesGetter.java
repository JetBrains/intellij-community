/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.AllClassesSearchExecutor;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
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
  public static final InsertHandler<JavaPsiClassReferenceElement> TRY_SHORTENING = new InsertHandler<JavaPsiClassReferenceElement>() {

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
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();

      final int newOffset = context.getOffsetMap().getOffset(key);
      if (newOffset >= 0) {
        endOffset = newOffset;
      }
      else {
        LOG.error(endOffset + " became invalid: " + context.getOffsetMap() + "; inserting " + qname);
      }

      final RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);

      boolean insertFqn = true;
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, JavaCompletionUtil.resolveReference(psiReference))) {
          insertFqn = false;
        }
        else if (psiClass.isValid()) {
          try {
            context.setTailOffset(psiReference.getRangeInElement().getEndOffset() + psiReference.getElement().getTextRange().getStartOffset());
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                for (final PsiReference reference : psiElement.getReferences()) {
                  if (psiManager.areElementsEquivalent(psiClass, JavaCompletionUtil.resolveReference(reference))) {
                    insertFqn = false;
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
        context.setTailOffset(toDelete.getStartOffset());
      }

      if (insertFqn) {
        INSERT_FQN.handleInsert(context, item);
      }
    }

    @Override
    public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      _handleInsert(context, item);
      item.getTailType().processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
    }

  };

  public static final InsertHandler<JavaPsiClassReferenceElement> INSERT_FQN = (context, item) -> {
    final String qName = item.getQualifiedName();
    if (qName != null) {
      int start = context.getTailOffset() - 1;
      while (start >= 0) {
        final char ch = context.getDocument().getCharsSequence().charAt(start);
        if (!Character.isJavaIdentifierPart(ch) && ch != '.') break;
        start--;
      }
      context.getDocument().replaceString(start + 1, context.getTailOffset(), qName);
      LOG.assertTrue(context.getTailOffset() >= 0);
    }
  };

  public static void processJavaClasses(@NotNull final CompletionParameters parameters,
                                        @NotNull final PrefixMatcher prefixMatcher,
                                        final boolean filterByScope,
                                        @NotNull final Consumer<PsiClass> consumer) {
    final PsiElement context = parameters.getPosition();
    final Project project = context.getProject();
    final GlobalSearchScope scope = filterByScope ? context.getContainingFile().getResolveScope() : GlobalSearchScope.allScope(project);

    processJavaClasses(prefixMatcher, project, scope, new LimitedAccessibleClassPreprocessor(parameters, filterByScope, consumer));
  }

  public static void processJavaClasses(@NotNull final PrefixMatcher prefixMatcher,
                                        @NotNull Project project,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiClass> processor) {
    final Set<String> names = new THashSet<>(10000);
    AllClassesSearchExecutor.processClassNames(project, scope, s -> {
      if (prefixMatcher.prefixMatches(s)) {
        names.add(s);
      }
    });
    LinkedHashSet<String> sorted = CompletionUtil.sortMatching(prefixMatcher, names);
    AllClassesSearchExecutor.processClassesByNames(project, scope, sorted, processor);
  }

  public static boolean isAcceptableInContext(@NotNull final PsiElement context,
                                              @NotNull final PsiClass psiClass,
                                              final boolean filterByScope, final boolean pkgContext) {
    ProgressManager.checkCanceled();

    if (JavaCompletionUtil.isInExcludedPackage(psiClass, false)) return false;

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) return false;

    if (!filterByScope && !(psiClass instanceof PsiCompiledElement)) return true;

    return JavaCompletionUtil.isSourceLevelAccessible(context, psiClass, pkgContext);
  }

  public static JavaPsiClassReferenceElement createLookupItem(@NotNull final PsiClass psiClass,
                                               final InsertHandler<JavaPsiClassReferenceElement> insertHandler) {
    final JavaPsiClassReferenceElement item = new JavaPsiClassReferenceElement(psiClass);
    item.setInsertHandler(insertHandler);
    return item;
  }

}

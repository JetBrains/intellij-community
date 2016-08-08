/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.FilterMixin;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/5/11
 * Time: 8:39 PM
 */
public class VcsContentAnnotationExceptionFilter implements Filter, FilterMixin {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationExceptionFilter");
  private final VcsContentAnnotationSettings mySettings;
  private final Map<VirtualFile,VcsRevisionNumber> myRevNumbersCache;
  private final ExceptionInfoCache myCache;

  public VcsContentAnnotationExceptionFilter(@NotNull GlobalSearchScope scope) {
    myProject = scope.getProject();
    mySettings = VcsContentAnnotationSettings.getInstance(myProject);
    myRevNumbersCache = new HashMap<>();
    myCache = new ExceptionInfoCache(scope);
  }

  private static class MyAdditionalHighlight extends AdditionalHighlight {
    private MyAdditionalHighlight(int start, int end) {
      super(start, end);
    }

    @NotNull
    @Override
    public TextAttributes getTextAttributes(@Nullable TextAttributes source) {
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      final TextAttributes changedColor = globalScheme.getAttributes(DiffColors.DIFF_MODIFIED);
      if (source == null) {
        TextAttributes attrs =
          globalScheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME).clone();
        attrs.setBackgroundColor(changedColor.getBackgroundColor());
        return attrs;
      }
      TextAttributes clone = source.clone();
      clone.setBackgroundColor(changedColor.getBackgroundColor());
      return clone;
    }
  }

  @Override
  public boolean shouldRunHeavy() {
    return mySettings.isShow();
  }

  @Override
  public void applyHeavyFilter(@NotNull final Document copiedFragment,
                               int startOffset,
                               int startLineNumber,
                               @NotNull Consumer<AdditionalHighlight> consumer) {
    VcsContentAnnotation vcsContentAnnotation = VcsContentAnnotationImpl.getInstance(myProject);
    final LocalChangesCorrector localChangesCorrector = new LocalChangesCorrector(myProject);
    Trinity<PsiClass, PsiFile, String> previousLineResult = null;

    for (int i = 0; i < copiedFragment.getLineCount(); i++) {
      final int lineStartOffset = copiedFragment.getLineStartOffset(i);
      final int lineEndOffset = copiedFragment.getLineEndOffset(i);
      final ExceptionWorker worker = new ExceptionWorker(myCache);
      final String[] lineText = new String[1];
      ApplicationManager.getApplication().runReadAction(() -> {
        lineText[0] = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
        worker.execute(lineText[0], lineEndOffset);
      });
      if (worker.getResult() != null) {
        VirtualFile vf = worker.getFile().getVirtualFile();
        if (vf.getFileSystem().isReadOnly()) continue;

        VcsRevisionNumber recentChangeRevision = myRevNumbersCache.get(vf);
        if (recentChangeRevision == null) {
          recentChangeRevision = vcsContentAnnotation.fileRecentlyChanged(vf);
          if (recentChangeRevision == null) {
            myRevNumbersCache.put(vf, VcsRevisionNumber.NULL);
          } else {
            myRevNumbersCache.put(vf, recentChangeRevision);
          }
        }
        if (VcsRevisionNumber.NULL.equals(recentChangeRevision)) {
          recentChangeRevision = null;
        }
        if (localChangesCorrector.isFileAlreadyIdentifiedAsChanged(vf) || ChangeListManager.isFileChanged(myProject, vf) ||
            recentChangeRevision != null) {
          final Document document = getDocumentForFile(worker);
          if (document == null) return;

          int startFileOffset = worker.getInfo().getThird().getStartOffset();
          int idx = lineText[0].indexOf(':', startFileOffset);
          int endIdx = idx == -1 ? worker.getInfo().getThird().getEndOffset() : idx;
          consumer.consume(new MyAdditionalHighlight(startOffset + lineStartOffset + startFileOffset + 1, startOffset + lineStartOffset + endIdx));

          if (worker.getPsiClass() != null) {
            // also check method
            final List<TextRange> ranges = findMethodRange(worker, document, previousLineResult);
            if (ranges != null) {
              boolean methodChanged = false;
              for (TextRange range : ranges) {
                if (localChangesCorrector.isRangeChangedLocally(vf, document, range)) {
                  methodChanged = true;
                  break;
                }
                final TextRange correctedRange = localChangesCorrector.getCorrectedRange(vf, document, range);
                if (vcsContentAnnotation.intervalRecentlyChanged(vf, correctedRange, recentChangeRevision)) {
                  methodChanged = true;
                  break;
                }
              }
              if (methodChanged) {
                consumer.consume(new MyAdditionalHighlight(startOffset + lineStartOffset + worker.getInfo().getSecond().getStartOffset(),
                                                           startOffset + lineStartOffset + worker.getInfo().getSecond().getEndOffset()));
              }
            }
          }
        }
      }
      previousLineResult = worker.getResult() == null ? null :
                           new Trinity<>(worker.getPsiClass(), worker.getFile(), worker.getMethod());
    }
  }

  @NotNull
  @Override
  public String getUpdateMessage() {
    return "Checking recent changes...";
  }

  private static class LocalChangesCorrector {
    private final Map<VirtualFile, UpToDateLineNumberProvider> myRecentlyChanged;
    private final Project myProject;

    private LocalChangesCorrector(final Project project) {
      myProject = project;
      myRecentlyChanged = new HashMap<>();
    }

    public boolean isFileAlreadyIdentifiedAsChanged(final VirtualFile vf) {
      return myRecentlyChanged.containsKey(vf);
    }
    
    public boolean isRangeChangedLocally(final VirtualFile vf, final Document document, final TextRange range) {
      final UpToDateLineNumberProvider provider = getProvider(vf, document);
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return provider.isRangeChanged(range.getStartOffset(), range.getEndOffset());
        }
      });
    }
    
    public TextRange getCorrectedRange(final VirtualFile vf, final Document document, final TextRange range) {
      final UpToDateLineNumberProvider provider = getProvider(vf, document);
      if (provider == null) return range;
      return ApplicationManager.getApplication().runReadAction(new Computable<TextRange>() {
        @Override
        public TextRange compute() {
          return new TextRange(provider.getLineNumber(range.getStartOffset()), provider.getLineNumber(range.getEndOffset()));
        }
      });
    }

    private UpToDateLineNumberProvider getProvider(VirtualFile vf, Document document) {
      UpToDateLineNumberProvider provider = myRecentlyChanged.get(vf);
      if (provider == null) {
        provider = new UpToDateLineNumberProviderImpl(document, myProject);
        myRecentlyChanged.put(vf, provider);
      }
      return provider;
    }
  }

  private static Document getDocumentForFile(final ExceptionWorker worker) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
      @Override
      public Document compute() {
        final Document document = FileDocumentManager.getInstance().getDocument(worker.getFile().getVirtualFile());
        if (document == null) {
          LOG.info("can not get document for file: " + worker.getFile().getVirtualFile());
          return null;
        }
        return document;
      }
    });
  }

  // line numbers
  private static List<TextRange> findMethodRange(final ExceptionWorker worker,
                                                 final Document document,
                                                 final Trinity<PsiClass, PsiFile, String> previousLineResult) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<TextRange>>() {
      @Override
      public List<TextRange> compute() {
        List<TextRange> ranges = getTextRangeForMethod(worker, previousLineResult);
        if (ranges == null) return null;
        final List<TextRange> result = new ArrayList<>();
        for (TextRange range : ranges) {
          result.add(new TextRange(document.getLineNumber(range.getStartOffset()),
                                       document.getLineNumber(range.getEndOffset())));
        }
        return result;
      }
    });
  }

  // null - check all
  @Nullable
  private static List<PsiMethod> selectMethod(final PsiMethod[] methods, final Trinity<PsiClass, PsiFile, String> previousLineResult) {
    if (previousLineResult == null || previousLineResult.getThird() == null) return null;

    final List<PsiMethod> result = new SmartList<>();
    for (final PsiMethod method : methods) {
      method.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitCallExpression(PsiCallExpression callExpression) {
          final PsiMethod resolved = callExpression.resolveMethod();
          if (resolved != null) {
            if (resolved.getName().equals(previousLineResult.getThird())) {
              result.add(method);
            }
          }
        }
      });
    }

    return result;
  }

  private static List<TextRange> getTextRangeForMethod(final ExceptionWorker worker, Trinity<PsiClass, PsiFile, String> previousLineResult) {
    String method = worker.getMethod();
    PsiClass psiClass = worker.getPsiClass();
    PsiMethod[] methods;
    if (method.contains("<init>")) {
      // constructor
      methods = psiClass.getConstructors();
    } else if (method.contains("$")) {
      // access$100
      return null;
    } else {
      methods = psiClass.findMethodsByName(method, false);
    }
    if (methods.length > 0) {
      if (methods.length == 1) {
        final TextRange range = methods[0].getTextRange();
        return Collections.singletonList(range);
      } else {
        List<PsiMethod> selectedMethods = selectMethod(methods, previousLineResult);
        final List<PsiMethod> toIterate = selectedMethods == null ? Arrays.asList(methods) : selectedMethods;
        final List<TextRange> result = new ArrayList<>();
        for (PsiMethod psiMethod : toIterate) {
          result.add(psiMethod.getTextRange());
        }
        return result;
      }
    }
    return null;
  }

  @Override
  public Result applyFilter(String line, int entireLength) {
    return null;
  }
}

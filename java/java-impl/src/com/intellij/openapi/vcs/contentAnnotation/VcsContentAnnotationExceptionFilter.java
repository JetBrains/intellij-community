// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.execution.filters.ExceptionInfoCache;
import com.intellij.execution.filters.ExceptionWorker;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.FilterMixin;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.FileStatus;
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

class VcsContentAnnotationExceptionFilter implements Filter, FilterMixin {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(VcsContentAnnotationExceptionFilter.class);
  private final VcsContentAnnotationSettings mySettings;
  private final Map<VirtualFile,VcsRevisionNumber> myRevNumbersCache = new HashMap<>();
  private final ExceptionInfoCache myCache;

  VcsContentAnnotationExceptionFilter(@NotNull GlobalSearchScope scope) {
    myProject = scope.getProject();
    mySettings = VcsContentAnnotationSettings.getInstance(myProject);
    myCache = new ExceptionInfoCache(scope);
  }

  private static final class MyAdditionalHighlight extends AdditionalHighlight {
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
                               @NotNull Consumer<? super AdditionalHighlight> consumer) {
    VcsContentAnnotation vcsContentAnnotation = VcsContentAnnotationImpl.getInstance(myProject);
    final LocalChangesCorrector localChangesCorrector = new LocalChangesCorrector(myProject);
    Trinity<PsiClass, PsiFile, String> previousLineResult = null;

    for (int i = 0; i < copiedFragment.getLineCount(); i++) {
      final int lineStartOffset = copiedFragment.getLineStartOffset(i);
      final int lineEndOffset = copiedFragment.getLineEndOffset(i);
      final ExceptionWorker worker = new ExceptionWorker(myCache);
      final String lineText = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
      if (ReadAction.compute(() -> DumbService.isDumb(myProject) ? null : worker.execute(lineText, lineEndOffset)) != null) {
        VirtualFile vf = worker.getFile().getVirtualFile();
        if (vf.getFileSystem().isReadOnly()) continue;

        VcsRevisionNumber recentChangeRevision = myRevNumbersCache.get(vf);
        if (recentChangeRevision == null) {
          recentChangeRevision = vcsContentAnnotation.fileRecentlyChanged(vf);
          if (recentChangeRevision == null) {
            myRevNumbersCache.put(vf, VcsRevisionNumber.NULL);
          }
          else {
            myRevNumbersCache.put(vf, recentChangeRevision);
          }
        }
        if (VcsRevisionNumber.NULL.equals(recentChangeRevision)) {
          recentChangeRevision = null;
        }

        FileStatus status = ChangeListManager.getInstance(myProject).getStatus(vf);
        boolean isFileChanged = FileStatus.NOT_CHANGED.equals(status) ||
                                FileStatus.UNKNOWN.equals(status) ||
                                FileStatus.IGNORED.equals(status);

        if (localChangesCorrector.isFileAlreadyIdentifiedAsChanged(vf) || isFileChanged || recentChangeRevision != null) {
          final Document document = getDocumentForFile(worker);
          if (document == null) return;

          int startFileOffset = worker.getInfo().fileLineRange.getStartOffset();
          int idx = lineText.indexOf(':', startFileOffset);
          int endIdx = idx == -1 ? worker.getInfo().fileLineRange.getEndOffset() : idx;
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
                consumer.consume(new MyAdditionalHighlight(startOffset + lineStartOffset + worker.getInfo().methodNameRange.getStartOffset(),
                                                           startOffset + lineStartOffset + worker.getInfo().methodNameRange.getEndOffset()));
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

  private static final class LocalChangesCorrector {
    private final Map<VirtualFile, UpToDateLineNumberProvider> myRecentlyChanged;
    private final Project myProject;

    private LocalChangesCorrector(final Project project) {
      myProject = project;
      myRecentlyChanged = new HashMap<>();
    }

    boolean isFileAlreadyIdentifiedAsChanged(final VirtualFile vf) {
      return myRecentlyChanged.containsKey(vf);
    }

    boolean isRangeChangedLocally(@NotNull VirtualFile vf, @NotNull Document document, @NotNull TextRange range) {
      final UpToDateLineNumberProvider provider = getProvider(vf, document);
      return ReadAction.compute(() -> provider.isRangeChanged(range.getStartOffset(), range.getEndOffset()));
    }

    TextRange getCorrectedRange(@NotNull VirtualFile vf, @NotNull Document document, @NotNull TextRange range) {
      final UpToDateLineNumberProvider provider = getProvider(vf, document);
      return ReadAction.compute(() -> new TextRange(provider.getLineNumber(range.getStartOffset()), provider.getLineNumber(range.getEndOffset())));
    }

    @NotNull
    private UpToDateLineNumberProvider getProvider(@NotNull VirtualFile vf, @NotNull Document document) {
      UpToDateLineNumberProvider provider = myRecentlyChanged.get(vf);
      if (provider == null) {
        provider = new UpToDateLineNumberProviderImpl(document, myProject);
        myRecentlyChanged.put(vf, provider);
      }
      return provider;
    }
  }

  private static Document getDocumentForFile(@NotNull ExceptionWorker worker) {
    return ReadAction.compute(() -> {
      final Document document = FileDocumentManager.getInstance().getDocument(worker.getFile().getVirtualFile());
      if (document == null) {
        LOG.info("can not get document for file: " + worker.getFile().getVirtualFile());
        return null;
      }
      return document;
    });
  }

  // line numbers
  private static List<TextRange> findMethodRange(final ExceptionWorker worker,
                                                 final Document document,
                                                 final Trinity<PsiClass, PsiFile, String> previousLineResult) {
    return ReadAction.compute(() -> {
      List<TextRange> ranges = getTextRangeForMethod(worker, previousLineResult);
      if (ranges == null) return null;
      final List<TextRange> result = new ArrayList<>();
      for (TextRange range : ranges) {
        result.add(new TextRange(document.getLineNumber(range.getStartOffset()),
                                 document.getLineNumber(range.getEndOffset())));
      }
      return result;
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
  public Result applyFilter(@NotNull String line, int entireLength) {
    return null;
  }
}

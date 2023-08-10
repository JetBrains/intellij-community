// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.execution.filters.*;
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
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.UpToDateLineNumberProviderImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.*;

class VcsContentAnnotationExceptionFilter implements Filter, FilterMixin {
  private final Project myProject;
  private static final Logger LOG = Logger.getInstance(VcsContentAnnotationExceptionFilter.class);
  private final VcsContentAnnotationSettings mySettings;
  private final Map<VirtualFile, VcsRevisionNumber> myRevNumbersCache = new HashMap<>();
  private final ExceptionInfoCache myCache;

  VcsContentAnnotationExceptionFilter(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    myProject = project;
    mySettings = VcsContentAnnotationSettings.getInstance(myProject);
    myCache = new ExceptionInfoCache(project, scope);
  }
  
  record LineResult(@Nullable UClass uClass, @Nullable PsiFile file, @Nullable String method) {}

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
    LineResult previousLineResult = null;

    for (int i = 0; i < copiedFragment.getLineCount(); i++) {
      final int lineStartOffset = copiedFragment.getLineStartOffset(i);
      final int lineEndOffset = copiedFragment.getLineEndOffset(i);
      final ExceptionLineParser worker = ExceptionLineParserFactory.getInstance().create(myCache);
      final String lineText = copiedFragment.getText(new TextRange(lineStartOffset, lineEndOffset));
      LineResult lineResult = ReadAction.compute(() -> {
        if (DumbService.isDumb(myProject)) return null;
        Result result = worker.execute(lineText, lineEndOffset);
        if (result == null) return null;
        return new LineResult(worker.getUClass(), worker.getFile(), worker.getMethod());
      });
      if (lineResult != null && lineResult.file() != null) {
        VirtualFile vf = lineResult.file().getVirtualFile();
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

          TextRange fileLinkRange = worker.getInfo().fileLineRange;
          int startFileLinkOffset = fileLinkRange.getStartOffset();
          int idx = lineText.indexOf(':', startFileLinkOffset);
          int endFileLinkOffset = idx == -1 ? fileLinkRange.getEndOffset() : idx;
          consumer.consume(new MyAdditionalHighlight(startOffset + lineStartOffset + startFileLinkOffset,
                                                     startOffset + lineStartOffset + endFileLinkOffset));

          if (lineResult.uClass() != null) {
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
                consumer.consume(
                  new MyAdditionalHighlight(startOffset + lineStartOffset + worker.getInfo().methodNameRange.getStartOffset(),
                                            startOffset + lineStartOffset + worker.getInfo().methodNameRange.getEndOffset()));
              }
            }
          }
        }
      }
      previousLineResult = worker.getResult() == null ? null : lineResult;
    }
  }

  @NotNull
  @Override
  public String getUpdateMessage() {
    return VcsBundle.message("checking.recent.changes");
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
      return ReadAction.compute(() -> new TextRange(provider.getLineNumber(range.getStartOffset()),
                                                    provider.getLineNumber(range.getEndOffset())));
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

  private static Document getDocumentForFile(@NotNull ExceptionLineParser worker) {
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
  private static List<TextRange> findMethodRange(final ExceptionLineParser worker,
                                                 final Document document,
                                                 final LineResult previousLineResult) {
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
  private static List<UMethod> selectMethod(List<UMethod> methods, final LineResult previousLineResult) {
    if (previousLineResult == null || previousLineResult.method() == null) return null;

    final List<UMethod> result = new SmartList<>();
    for (final UMethod method : methods) {
      PsiElement psi = method.getSourcePsi();
      if (psi == null) continue;
      psi.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          UCallExpression call = UastContextKt.toUElement(element, UCallExpression.class);
          if (call != null) {
            PsiMethod resolved = call.resolve();
            if (resolved != null && resolved.getName().equals(previousLineResult.method())) {
              result.add(method);
              stopWalking();
            }
          }
        }
      });
    }

    return result;
  }

  private static List<TextRange> getTextRangeForMethod(final ExceptionLineParser worker,
                                                       LineResult previousLineResult) {
    String method = worker.getMethod().replaceFirst("\\$lambda\\$\\d+$", "")
      .replaceFirst("^lambda\\$(.+)\\$\\d+$", "$1");
    UClass psiClass = worker.getUClass();
    if (psiClass == null) return null;
    List<UMethod> methods;
    if (method.contains("<init>")) {
      // constructor
      methods = ContainerUtil.filter(psiClass.getMethods(), m -> m.isConstructor());
    }
    else if (method.contains("$")) {
      // access$100
      return null;
    }
    else {
      methods = ContainerUtil.filter(psiClass.getMethods(), m -> method.equals(m.getName()));
    }
    if (!methods.isEmpty()) {
      if (methods.size() == 1) {
        PsiElement psi = methods.get(0).getSourcePsi();
        if (psi == null) return null;
        final TextRange range = psi.getTextRange();
        return Collections.singletonList(range);
      }
      else {
        List<UMethod> selectedMethods = selectMethod(methods, previousLineResult);
        final List<UMethod> toIterate = selectedMethods == null ? methods : selectedMethods;
        final List<TextRange> result = new ArrayList<>();
        for (UMethod psiMethod : toIterate) {
          PsiElement psi = psiMethod.getSourcePsi();
          if (psi != null) {
            result.add(psi.getTextRange());
          }
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

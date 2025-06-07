// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.Language;
import com.intellij.lang.parameterInfo.LanguageParameterInfo;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.codeInsight.hint.ParameterInfoTaskRunnerUtil.runTask;

public final class ShowParameterInfoHandler implements CodeInsightActionHandler {
  private static final ParameterInfoHandler[] EMPTY_HANDLERS = new ParameterInfoHandler[0];
  private final boolean myRequestFocus;

  public ShowParameterInfoHandler() {
    this(false);
  }

  public ShowParameterInfoHandler(boolean requestFocus) {
    myRequestFocus = requestFocus;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    invoke(project, editor, psiFile, -1, null, myRequestFocus);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static void invoke(final Project project, final Editor editor, PsiFile file,
                            int lbraceOffset, PsiElement highlightedElement, boolean requestFocus) {
    invoke(project, editor, file, lbraceOffset, highlightedElement, requestFocus, false,
           CodeInsightBundle.message("parameter.info.progress.title"));
  }

  /**
   * @param progressTitle null means no loading panel should be shown
   */
  @ApiStatus.Internal
  public static void invoke(final Project project, final Editor editor, PsiFile file,
                            int lbraceOffset, PsiElement highlightedElement,
                            boolean requestFocus, boolean singleParameterHint,
                            @Nullable @NlsContexts.ProgressTitle String progressTitle) {
    final int initialOffset = editor.getCaretModel().getOffset();

    runTask(project,
            ReadAction.nonBlocking(() -> {
              final int offset = editor.getCaretModel().getOffset();
              final int fileLength = file.getTextLength();
              if (fileLength == 0) return null;

              // file.findElementAt(file.getTextLength()) returns null but we may need to show parameter info at EOF offset (for example in SQL)
              final int offsetForLangDetection = offset > 0 && offset == fileLength ? offset - 1 : offset;
              final Language language = PsiUtilCore.getLanguageAtOffset(file, offsetForLangDetection);

              final ShowParameterInfoContext context = new ShowParameterInfoContext(
                editor,
                project,
                file,
                offset,
                lbraceOffset,
                requestFocus,
                singleParameterHint
              );

              context.setHighlightedElement(highlightedElement);
              context.setRequestFocus(requestFocus);

              final ParameterInfoHandler<PsiElement, Object>[] handlers =
                getHandlers(project, language, file.getViewProvider().getBaseLanguage());


              return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
                for (ParameterInfoHandler<PsiElement, Object> handler : handlers) {
                  PsiElement element = handler.findElementForParameterInfo(context);
                  if (element != null) {
                    return (Runnable)() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
                      if (element.isValid()) {
                        handler.showParameterInfo(element, context);
                      }
                    });
                  }
                }
                return null;
              });
            })
              .withDocumentsCommitted(project)
              .expireWhen(() -> editor.getCaretModel().getOffset() != initialOffset)
              .coalesceBy(ShowParameterInfoHandler.class, editor),
            continuation -> {
              if (continuation != null) {
                continuation.run();
              }
            },
            progressTitle,
            editor);
  }

  public static ParameterInfoHandler @NotNull [] getHandlers(Project project, final Language @NotNull ... languages) {
    Set<ParameterInfoHandler> handlers = new LinkedHashSet<>();
    DumbService dumbService = DumbService.getInstance(project);
    for (final Language language : languages) {
      handlers.addAll(dumbService.filterByDumbAwareness(LanguageParameterInfo.INSTANCE.allForLanguage(language)));
    }
    return handlers.isEmpty() ? EMPTY_HANDLERS : handlers.toArray(new ParameterInfoHandler[0]);
  }
}


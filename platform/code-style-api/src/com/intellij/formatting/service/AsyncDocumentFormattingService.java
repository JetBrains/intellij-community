// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public abstract class AsyncDocumentFormattingService extends AbstractDocumentFormattingService {
  private final static Logger LOG = Logger.getInstance(AsyncDocumentFormattingService.class);

  @Override
  public final synchronized void formatDocument(@NotNull Document document,
                                                @NotNull List<TextRange> formattingRanges,
                                                @NotNull FormattingContext formattingContext,
                                                boolean canChangeWhiteSpaceOnly) {
    if (prepare(formattingContext)) {
      ApplicationManager.getApplication().executeOnPooledThread(
        () -> startFormat(document.getText(),
                          formattingContext,
                          canChangeWhiteSpaceOnly,
                          new MyCallback(formattingContext.getProject(), document)));
    }
  }

  protected abstract boolean prepare(@NotNull FormattingContext formattingContext);

  protected abstract void startFormat(@NotNull String documentText,
                                      @NotNull FormattingContext formattingContext,
                                      boolean canChangeWhiteSpaceOnly,
                                      @NotNull Callback callback);

  @SuppressWarnings("unused")
  protected void mergeChanges(@NotNull Document document, @NotNull String updatedText) {}

  public interface Callback {
    void onTextReady(@NotNull String updatedText);
    void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message);
  }

  protected abstract @NotNull String getNotificationGroupId();

  private class MyCallback implements Callback {
    private final Project  myProject;
    private final Document myDocument;
    private final long     myInitialModificationStamp;

    private MyCallback(@NotNull Project project, @NotNull Document document) {
      myProject = project;
      myDocument = document;
      myInitialModificationStamp = document.getModificationStamp();
    }

    @Override
    public void onTextReady(@NotNull final String updatedText) {
      ApplicationManager.getApplication().invokeLater(() ->{
        CommandProcessor.getInstance().runUndoTransparentAction(() -> {
          try {
            WriteAction.run((ThrowableRunnable<Throwable>)() -> {
              if (myDocument.getModificationStamp() > myInitialModificationStamp) {
                mergeChanges(myDocument, updatedText);
              }
              else {
                myDocument.setText(updatedText);
              }
            });
          }
          catch (Throwable throwable) {
            LOG.error(throwable);
          }
        });
      });
    }

    @Override
    public void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message) {
      FormattingNotificationService.getInstance(myProject).reportError(getNotificationGroupId(), title, message);
    }
  }
}

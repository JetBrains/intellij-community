// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.Objects;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public class AsyncRendering {
  private static final Key<LookupElementPresentation> LAST_COMPUTED_PRESENTATION = Key.create("LAST_COMPUTED_PRESENTATION");
  private static final Key<CancellablePromise<?>> LAST_COMPUTATION = Key.create("LAST_COMPUTATION");
  private static final Executor ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ExpensiveRendering");
  private final LookupImpl myLookup;

  AsyncRendering(LookupImpl lookup) {
    myLookup = lookup;
  }

  @NotNull
  LookupElementPresentation getLastComputed(@NotNull LookupElement element) {
    return Objects.requireNonNull(element.getUserData(LAST_COMPUTED_PRESENTATION));
  }

  static void rememberPresentation(LookupElement element, LookupElementPresentation presentation) {
    element.putUserData(LAST_COMPUTED_PRESENTATION, presentation);
  }

  void scheduleRendering(@NotNull LookupElement element, @NotNull LookupElementRenderer<?> renderer) {
    synchronized (LAST_COMPUTATION) {
      cancelRendering(element);

      Ref<CancellablePromise<?>> promiseRef = Ref.create();
      CancellablePromise<Void> promise = ReadAction
        .nonBlocking(() -> {
          if (element.isValid()) {
            renderInBackground(element, renderer);
          }
          synchronized (LAST_COMPUTATION) {
            element.replace(LAST_COMPUTATION, promiseRef.get(), null);
          }
        })
        .expireWith(myLookup)
        .submit(ourExecutor);
      element.putUserData(LAST_COMPUTATION, promise);
      promiseRef.set(promise);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void renderInBackground(LookupElement element, LookupElementRenderer renderer) {
    LookupElementPresentation presentation = new LookupElementPresentation();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      renderer.renderElement(element, presentation);
    });

    presentation.freeze();
    rememberPresentation(element, presentation);
    myLookup.myCellRenderer.scheduleUpdateLookupWidthFromVisibleItems();
  }

  public static void cancelRendering(@NotNull LookupElement item) {
    synchronized (LAST_COMPUTATION) {
      CancellablePromise<?> promise = item.getUserData(LAST_COMPUTATION);
      if (promise != null) {
        promise.cancel();
        item.putUserData(LAST_COMPUTATION, null);
      }
    }
  }

}

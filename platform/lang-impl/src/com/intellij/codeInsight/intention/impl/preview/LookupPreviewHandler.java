// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A helper class to install intention preview to a {@link LookupImpl}.
 *
 * @param <T> type of the elements in the list, for which the preview is supported.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public final class LookupPreviewHandler<T> implements LookupListener {
  private final IntentionPreviewPopupUpdateProcessor myProcessor;
  private final LookupImpl myLookup;
  private final Class<T> myClass;
  private final Map<T, Integer> counterHolder = new HashMap<>();
  private final AtomicInteger counterValue = new AtomicInteger(0);
  private final AtomicBoolean shown = new AtomicBoolean(false);
  private final IntentionPreviewComponentHolder myPopup;

  /**
   * Construct and install the helper to the listPopup.
   *
   * @param project          current project
   * @param lookup           list popup to install the preview to
   * @param allowedClass     class of the elements in the list, for which the preview is supported
   * @param previewGenerator a function to generate {@link IntentionPreviewInfo} from the item. Executed in background read action.
   */
  public LookupPreviewHandler(@NotNull Project project,
                              @NotNull LookupImpl lookup,
                              @NotNull Class<T> allowedClass,
                              @NotNull Function<? super T, ? extends @NotNull IntentionPreviewInfo> previewGenerator) {
    myLookup = lookup;
    myClass = allowedClass;
    myProcessor = new IntentionPreviewPopupUpdateProcessor(project, obj -> previewGenerator.apply(myClass.cast(obj)));
    myPopup = new IntentionPreviewComponentHolder() {
      @Override
      public @NotNull JComponent component() {
        return myLookup.getComponent();
      }

      @Override
      public boolean isDisposed() {
        return myLookup.isLookupDisposed();
      }

      @Override
      public void dispose() {
        Disposer.dispose(myLookup);
      }
    };
    registerShowPreviewAction();
    registerListeners();
  }

  private void registerListeners() {
    myLookup.addLookupListener(this);
  }

  @Override
  public void itemSelected(@NotNull LookupEvent event) {
    myProcessor.hide();
  }

  @Override
  public void lookupCanceled(@NotNull LookupEvent event) {
    myProcessor.hide();
  }

  @Override
  public void currentItemChanged(@NotNull LookupEvent event) {
    Lookup lookup = event.getLookup();
    if (lookup instanceof LookupImpl impl) {
      update(impl);
    }
  }

  private void update(@NotNull LookupImpl list) {
    Object selectedItem = list.getCurrentItem();
    T item = ObjectUtils.tryCast(selectedItem, myClass);
    if (item != null) {
      update(item);
    }
  }

  /**
   * Show the preview initially. Should be called when a popup is shown.
   */
  public void showInitially() {
    if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
      ApplicationManager.getApplication().invokeLater(this::showPreview);
    }
  }

  @RequiresEdt
  private void update(T action) {
    if (!shown.get()) {
      return;
    }
    Integer index = counterHolder.computeIfAbsent(action, k -> counterValue.getAndIncrement());
    myProcessor.setup(myPopup, index);
    myProcessor.updatePopup(action);
  }

  @RequiresEdt
  private void registerShowPreviewAction() {
    //todo add some ads
  }

  private void showPreview() {
    shown.set(true);
    myProcessor.show();
    LookupElement item = myLookup.getCurrentItem();
    T targetValue = ObjectUtils.tryCast(item, myClass);
    if (targetValue != null) {
      update(targetValue);
    }
  }

  public void close() {
    myProcessor.hide();
    myLookup.removeLookupListener(this);
  }
}

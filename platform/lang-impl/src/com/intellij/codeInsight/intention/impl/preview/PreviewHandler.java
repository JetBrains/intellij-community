// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Function;

/**
 * A helper class to install intention preview to a {@link ListPopup}.
 * 
 * @param <T> type of the elements in the list, for which the preview is supported.
 */
@ApiStatus.Internal
public final class PreviewHandler<T> {
  private final IntentionPreviewPopupUpdateProcessor myProcessor;
  private final ListPopup myListPopup;
  private final Class<T> myClass;
  private final IntentionPreviewComponentHolder myPopup;

  /**
   * Construct and install the helper to the listPopup.
   * 
   * @param project current project
   * @param listPopup list popup to install the preview to
   * @param allowedClass class of the elements in the list, for which the preview is supported
   * @param previewGenerator a function to generate {@link IntentionPreviewInfo} from the item. Executed in background read action.
   */
  public PreviewHandler(@NotNull Project project,
                        @NotNull ListPopup listPopup,
                        @NotNull Class<T> allowedClass,
                        @NotNull Function<? super T, ? extends @NotNull IntentionPreviewInfo> previewGenerator) {
    myListPopup = listPopup;
    myClass = allowedClass;
    myProcessor = new IntentionPreviewPopupUpdateProcessor(project, obj -> previewGenerator.apply(myClass.cast(obj)));
    myPopup = new IntentionPreviewComponentHolder() {
      @Override
      public @NotNull JComponent jComponent() {
        return listPopup.getContent();
      }

      @Override
      public boolean isDisposed() {
        return listPopup.isDisposed();
      }

      @Override
      public void dispose() {
        Disposer.dispose(listPopup);
      }
    };
    Disposer.register(listPopup, myPopup);
    registerShowPreviewAction();
    registerListeners();
  }

  private void registerListeners() {
    if (!(myListPopup instanceof ListPopupImpl listPopupImpl)) {
      return;
    }
    JList<?> list = listPopupImpl.getList();
    list.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
          showPreview();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
          myProcessor.hide();
        }
      }
    });
    myListPopup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myProcessor.hide();
      }
    });
    list.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        update(list);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        update(list);
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
        update(list);
      }
    });
    myListPopup.addListSelectionListener(e -> {
      update(list);
    });
  }

  private void update(@NotNull JList<?> list) {
    Object selectedItem = list.getSelectedValue();
    T item = ObjectUtils.tryCast(selectedItem, myClass);
    if (item != null) {
      update(item);
    }
  }

  /**
   * Show the preview initially. Should be called when popup is shown.
   */
  public void showInitially() {
    if (EditorSettingsExternalizable.getInstance().isShowIntentionPreview()) {
      ApplicationManager.getApplication().invokeLater(this::showPreview);
    }
  }

  @RequiresEdt
  private void update(T action) {
    if (myListPopup instanceof ListPopupImpl listPopup) {
      myProcessor.setup(myPopup, listPopup.getOriginalSelectedIndex());
      myProcessor.updatePopup(action);
    }
  }

  @RequiresEdt
  private void registerShowPreviewAction() {
    if (!(myListPopup instanceof WizardPopup wizardPopup)) return;
    KeyStroke keyStroke = KeymapUtil.getKeyStroke(IntentionPreviewPopupUpdateProcessor.Companion.getShortcutSet());
    Action action = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e1) {
        maybeShowPreview();
      }
    };
    wizardPopup.registerAction("showIntentionPreview", keyStroke, action);
    advertisePopup();
  }

  private void advertisePopup() {
    if (!myListPopup.isDisposed()) {
      String shortcutText = IntentionPreviewPopupUpdateProcessor.Companion.getShortcutText();
      myListPopup.setAdText(CodeInsightBundle.message("intention.preview.adv.toggle.text", shortcutText), SwingConstants.LEFT);
    }
  }

  private void maybeShowPreview() {
    boolean shouldShow = !myProcessor.isShown();
    EditorSettingsExternalizable.getInstance().setShowIntentionPreview(shouldShow);
    if (shouldShow) {
      myProcessor.activate();
      showPreview();
    }
    else {
      myProcessor.hide();
    }
  }

  private void showPreview() {
    myProcessor.show();
    if (myListPopup instanceof ListPopupImpl listPopup) {
      JList<?> list = listPopup.getList();
      T targetValue = ObjectUtils.tryCast(list.getSelectedValue(), myClass);
      if (targetValue != null) {
        update(targetValue);
      }
    }
  }
}

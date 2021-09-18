// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.ex.TooltipAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.WidthBasedLayout;
import com.intellij.ui.popup.AbstractPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.editor.EditorMouseHoverPopupManager.LOG;
import static com.intellij.openapi.editor.EditorMouseHoverPopupManager.validatePopupSize;

final class HighlightHoverInfo {

  private static final Key<Boolean> DISABLE_BINDING = Key.create("EditorMouseHoverPopupManager.disable.binding");
  private static final TooltipGroup EDITOR_INFO_GROUP = new TooltipGroup("EDITOR_INFO_GROUP", 0);

  private final @NotNull HighlightInfo highlightInfo;
  private final @Nullable TooltipAction tooltipAction;

  private HighlightHoverInfo(@NotNull HighlightInfo highlightInfo, @Nullable TooltipAction tooltipAction) {
    this.highlightInfo = highlightInfo;
    this.tooltipAction = tooltipAction;
  }

  @Nullable JComponent createHighlightInfoComponent(
    @NotNull Editor editor,
    boolean highlightActions,
    @NotNull PopupBridge popupBridge,
    boolean requestFocus
  ) {
    ErrorStripTooltipRendererProvider provider = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider();
    TooltipRenderer tooltipRenderer = provider.calcTooltipRenderer(Objects.requireNonNull(highlightInfo.getToolTip()), tooltipAction, -1);
    if (!(tooltipRenderer instanceof LineTooltipRenderer)) return null;
    return createHighlightInfoComponent(editor, (LineTooltipRenderer)tooltipRenderer, highlightActions, popupBridge, requestFocus);
  }

  private static @Nullable JComponent createHighlightInfoComponent(
    @NotNull Editor editor,
    @NotNull LineTooltipRenderer renderer,
    boolean highlightActions,
    @NotNull PopupBridge popupBridge,
    boolean requestFocus
  ) {
    Ref<WrapperPanel> wrapperPanelRef = new Ref<>();
    Ref<LightweightHint> mockHintRef = new Ref<>();
    HintHint hintHint = new HintHint().setAwtTooltip(true).setRequestFocus(requestFocus);
    LightweightHint hint = renderer.createHint(editor, new Point(), false, EDITOR_INFO_GROUP, hintHint, highlightActions, false, expand -> {
      LineTooltipRenderer newRenderer = renderer.createRenderer(renderer.getText(), expand ? 1 : 0);
      JComponent newComponent = createHighlightInfoComponent(editor, newRenderer, highlightActions, popupBridge, requestFocus);
      AbstractPopup popup = popupBridge.getPopup();
      WrapperPanel wrapper = wrapperPanelRef.get();
      if (newComponent != null && popup != null && wrapper != null) {
        LightweightHint mockHint = mockHintRef.get();
        if (mockHint != null) closeHintIgnoreBinding(mockHint);
        wrapper.setContent(newComponent);
        validatePopupSize(popup);
      }
    });
    if (hint == null) return null;
    mockHintRef.set(hint);
    bindHintHiding(hint, popupBridge);
    JComponent component = hint.getComponent();
    LOG.assertTrue(component instanceof WidthBasedLayout, "Unexpected type of tooltip component: " + component.getClass());
    WrapperPanel wrapper = new WrapperPanel(component);
    wrapperPanelRef.set(wrapper);
    // emulating LightweightHint+IdeTooltipManager+BalloonImpl - they use the same background
    wrapper.setBackground(hintHint.getTextBackground());
    wrapper.setOpaque(true);
    return wrapper;
  }

  private static void bindHintHiding(@NotNull LightweightHint hint, @NotNull PopupBridge popupBridge) {
    AtomicBoolean inProcess = new AtomicBoolean();
    hint.addHintListener(e -> {
      if (hint.getUserData(DISABLE_BINDING) == null && inProcess.compareAndSet(false, true)) {
        try {
          AbstractPopup popup = popupBridge.getPopup();
          if (popup != null) {
            popup.cancel();
          }
        }
        finally {
          inProcess.set(false);
        }
      }
    });
    popupBridge.performOnCancel(() -> {
      if (hint.getUserData(DISABLE_BINDING) == null && inProcess.compareAndSet(false, true)) {
        try {
          hint.hide();
        }
        finally {
          inProcess.set(false);
        }
      }
    });
  }

  private static void closeHintIgnoreBinding(@NotNull LightweightHint hint) {
    hint.putUserData(DISABLE_BINDING, Boolean.TRUE);
    hint.hide();
  }

  @NotNull HighlightHoverInfo override(@NotNull TooltipAction tooltipAction) {
    return new HighlightHoverInfo(this.highlightInfo, tooltipAction);
  }

  static @Nullable HighlightHoverInfo highlightHoverInfo(@NotNull Editor editor, @Nullable HighlightInfo info) {
    if (info == null || info.getToolTip() == null) {
      return null;
    }
    try {
      TooltipAction tooltipAction = ReadAction
        .nonBlocking(() -> TooltipActionProvider.calcTooltipAction(info, editor))
        .executeSynchronously();
      return new HighlightHoverInfo(info, tooltipAction);
    }
    catch (IndexNotReadyException ignored) {
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return new HighlightHoverInfo(info, null);
  }
}

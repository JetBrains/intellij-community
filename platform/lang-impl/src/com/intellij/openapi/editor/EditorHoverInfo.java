// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider;
import com.intellij.openapi.editor.ex.TooltipAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.editor.EditorMouseHoverPopupManager.LOG;

@Internal
public final class EditorHoverInfo {

  private static final Key<Boolean> DISABLE_BINDING = Key.create("EditorMouseHoverPopupManager.disable.binding");
  private static final TooltipGroup EDITOR_INFO_GROUP = new TooltipGroup("EDITOR_INFO_GROUP", 0);

  private final HighlightInfo highlightInfo;
  private final TooltipAction tooltipAction;

  private final @Nls String quickDocMessage;
  private final WeakReference<PsiElement> quickDocElement;
  private final DocumentationProvider docProvider;

  public EditorHoverInfo(HighlightInfo highlightInfo,
                         TooltipAction tooltipAction,
                         @Nls String quickDocMessage,
                         PsiElement quickDocElement,
                         @Nullable DocumentationProvider provider) {
    assert highlightInfo != null || quickDocMessage != null;
    this.docProvider = provider;
    this.highlightInfo = highlightInfo;
    this.tooltipAction = tooltipAction;
    this.quickDocMessage = quickDocMessage;
    this.quickDocElement = new WeakReference<>(quickDocElement);
  }

  public JComponent createComponent(Editor editor, PopupBridge popupBridge, boolean requestFocus) {
    boolean quickDocShownInPopup = quickDocMessage != null &&
                                   ToolWindowManager.getInstance(Objects.requireNonNull(editor.getProject()))
                                     .getToolWindow(ToolWindowId.DOCUMENTATION) == null;
    JComponent c1 = createHighlightInfoComponent(editor, !quickDocShownInPopup, popupBridge, requestFocus);
    DocumentationComponent c2 = createQuickDocComponent(editor, c1 != null, popupBridge);
    assert quickDocShownInPopup == (c2 != null);
    if (c1 == null && c2 == null) return null;
    JPanel p = new JPanel(new CombinedPopupLayout(c1, c2));
    p.setBorder(null);
    if (c1 != null) p.add(c1);
    if (c2 != null) p.add(c2);
    return p;
  }

  private JComponent createHighlightInfoComponent(Editor editor,
                                                  boolean highlightActions,
                                                  PopupBridge popupBridge,
                                                  boolean requestFocus) {
    if (highlightInfo == null) return null;
    ErrorStripTooltipRendererProvider provider = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider();
    TooltipRenderer tooltipRenderer = provider.calcTooltipRenderer(Objects.requireNonNull(highlightInfo.getToolTip()), tooltipAction, -1);
    if (!(tooltipRenderer instanceof LineTooltipRenderer)) return null;
    return createHighlightInfoComponent(editor, (LineTooltipRenderer)tooltipRenderer, highlightActions, popupBridge, requestFocus);
  }

  private static JComponent createHighlightInfoComponent(Editor editor,
                                                         LineTooltipRenderer renderer,
                                                         boolean highlightActions,
                                                         PopupBridge popupBridge,
                                                         boolean requestFocus) {
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
        EditorMouseHoverPopupManager.validatePopupSize(popup);
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

  private static void bindHintHiding(LightweightHint hint, PopupBridge popupBridge) {
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

  private static void closeHintIgnoreBinding(LightweightHint hint) {
    hint.putUserData(DISABLE_BINDING, Boolean.TRUE);
    hint.hide();
  }

  @Nullable
  private DocumentationComponent createQuickDocComponent(Editor editor, boolean deEmphasize, PopupBridge popupBridge) {
    if (quickDocMessage == null) return null;
    PsiElement element = quickDocElement.get();
    Project project = Objects.requireNonNull(editor.getProject());
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
    if (toolWindow != null) {
      if (element != null) {
        documentationManager.showJavaDocInfo(editor, element, extractOriginalElement(element), null, quickDocMessage, true, false);
        documentationManager.setAllowContentUpdateFromContext(false);
      }
      return null;
    }
    final class MyDocComponent extends DocumentationComponent {
      private MyDocComponent() {
        super(documentationManager, false);
        if (deEmphasize) {
          setBackground(UIUtil.getToolTipActionBackground());
        }
      }

      @Override
      protected void showHint() {
        AbstractPopup popup = popupBridge.getPopup();
        if (popup != null) {
          EditorMouseHoverPopupManager.validatePopupSize(popup);
        }
      }
    }
    DocumentationComponent component = new MyDocComponent();
    if (deEmphasize) {
      component.setBorder(IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.TOP));
    }
    component.setData(element, quickDocMessage, null, null, docProvider);
    component.setToolwindowCallback(() -> {
      PsiElement docElement = component.getElement();
      if (docElement != null) {
        documentationManager.createToolWindow(docElement, extractOriginalElement(docElement));
        ToolWindow createdToolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
        if (createdToolWindow != null) {
          createdToolWindow.setAutoHide(false);
        }
      }
      AbstractPopup popup = popupBridge.getPopup();
      if (popup != null) {
        popup.cancel();
      }
    });
    popupBridge.performWhenAvailable(component::setHint);
    EditorUtil.disposeWithEditor(editor, component);
    popupBridge.performOnCancel(() -> Disposer.dispose(component));
    return component;
  }

  public EditorHoverInfo withQuickDocMessage(@Nls String quickDocMessage) {
    return new EditorHoverInfo(highlightInfo, tooltipAction, quickDocMessage, quickDocElement.get(), docProvider);
  }

  public EditorHoverInfo withQuickDocElement(PsiElement element) {
    return new EditorHoverInfo(highlightInfo, tooltipAction, quickDocMessage, element, docProvider);
  }

  public EditorHoverInfo withTooltip(TooltipAction tooltipAction) {
    return new EditorHoverInfo(highlightInfo, tooltipAction, quickDocMessage, quickDocElement.get(), docProvider);
  }

  private static PsiElement extractOriginalElement(PsiElement element) {
    if (element == null) {
      return null;
    }
    SmartPsiElementPointer<?> originalElementPointer = element.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY);
    return originalElementPointer == null ? null : originalElementPointer.getElement();
  }
}

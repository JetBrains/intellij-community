// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.ex.TooltipAction;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.Objects;

@Internal
public final class EditorHoverInfo {

  private final @Nullable HighlightHoverInfo highlightHoverInfo;

  private final @Nls String quickDocMessage;
  private final WeakReference<PsiElement> quickDocElement;
  private final DocumentationProvider docProvider;

  public EditorHoverInfo(@Nullable HighlightHoverInfo highlightHoverInfo,
                         @Nls String quickDocMessage,
                         PsiElement quickDocElement,
                         @Nullable DocumentationProvider provider) {
    assert highlightHoverInfo != null || quickDocMessage != null;
    this.highlightHoverInfo = highlightHoverInfo;
    this.docProvider = provider;
    this.quickDocMessage = quickDocMessage;
    this.quickDocElement = new WeakReference<>(quickDocElement);
  }

  public JComponent createComponent(Editor editor, PopupBridge popupBridge, boolean requestFocus) {
    boolean quickDocShownInPopup = quickDocMessage != null &&
                                   ToolWindowManager.getInstance(Objects.requireNonNull(editor.getProject()))
                                     .getToolWindow(ToolWindowId.DOCUMENTATION) == null;
    JComponent c1 = highlightHoverInfo == null
                    ? null
                    : highlightHoverInfo.createHighlightInfoComponent(editor, !quickDocShownInPopup, popupBridge, requestFocus);
    DocumentationComponent c2 = createQuickDocComponent(editor, c1 != null, popupBridge);
    assert quickDocShownInPopup == (c2 != null);
    if (c1 == null && c2 == null) return null;
    JPanel p = new JPanel(new CombinedPopupLayout(c1, c2));
    p.setBorder(null);
    if (c1 != null) p.add(c1);
    if (c2 != null) p.add(c2);
    return p;
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
    return new EditorHoverInfo(highlightHoverInfo, quickDocMessage, quickDocElement.get(), docProvider);
  }

  public EditorHoverInfo withQuickDocElement(PsiElement element) {
    return new EditorHoverInfo(highlightHoverInfo, quickDocMessage, element, docProvider);
  }

  public EditorHoverInfo withTooltip(TooltipAction tooltipAction) {
    if (highlightHoverInfo == null) {
      return this;
    }
    return new EditorHoverInfo(highlightHoverInfo.override(tooltipAction), quickDocMessage, quickDocElement.get(), docProvider);
  }

  private static PsiElement extractOriginalElement(PsiElement element) {
    if (element == null) {
      return null;
    }
    SmartPsiElementPointer<?> originalElementPointer = element.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY);
    return originalElementPointer == null ? null : originalElementPointer.getElement();
  }
}

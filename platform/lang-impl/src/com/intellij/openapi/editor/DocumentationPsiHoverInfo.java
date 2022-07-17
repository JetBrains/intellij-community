// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.documentation.DocumentationProvider;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * @deprecated Unused in v2 implementation.
 */
@Deprecated
@Internal
public final class DocumentationPsiHoverInfo implements DocumentationHoverInfo {

  private final @Nls @NotNull String quickDocMessage;
  private final WeakReference<PsiElement> quickDocElement;
  public final DocumentationProvider docProvider;

  public DocumentationPsiHoverInfo(
    @Nls @NotNull String quickDocMessage,
    PsiElement quickDocElement,
    @Nullable DocumentationProvider docProvider
  ) {
    this.quickDocMessage = quickDocMessage;
    this.quickDocElement = new WeakReference<>(quickDocElement);
    this.docProvider = docProvider;
  }

  @Override
  public boolean showInPopup(@NotNull Project project) {
    return ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION) == null;
  }

  @Override
  public @Nullable JComponent createQuickDocComponent(@NotNull Editor editor, boolean deEmphasize, @NotNull PopupBridge popupBridge) {
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

  private static PsiElement extractOriginalElement(PsiElement element) {
    if (element == null) {
      return null;
    }
    SmartPsiElementPointer<?> originalElementPointer = element.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY);
    return originalElementPointer == null ? null : originalElementPointer.getElement();
  }
}

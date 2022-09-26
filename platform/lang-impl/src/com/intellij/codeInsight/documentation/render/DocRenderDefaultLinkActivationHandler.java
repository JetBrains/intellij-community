// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.QuickDocUtil;
import com.intellij.ide.BrowserUtil;
import com.intellij.lang.documentation.InlineDocumentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.ui.popup.PopupFactoryImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.geom.Rectangle2D;
import java.util.Locale;

import static com.intellij.codeInsight.documentation.QuickDocUtil.isDocumentationV2Enabled;
import static com.intellij.lang.documentation.ide.impl.DocumentationManager.instance;

public class DocRenderDefaultLinkActivationHandler implements DocRenderLinkActivationHandler {
  final DocRenderer myRenderer;

  DocRenderDefaultLinkActivationHandler(DocRenderer renderer) {
    myRenderer = renderer;
  }

  @Override
  public void activateLink(HyperlinkEvent event, @NotNull DocRenderer renderer) {
    Element element = event.getSourceElement();
    if (!(myRenderer.getData() instanceof DocRenderItem)) return;
    DocRenderItem item = (DocRenderItem)myRenderer.getData();
    if (element == null) return;

    Rectangle2D location = null;
    try {
      location = ((JEditorPane)event.getSource()).modelToView2D(element.getStartOffset());
    }
    catch (BadLocationException ignored) {
    }
    if (location == null) return;

    String url = event.getDescription();
    if (isDocumentationV2Enabled()) {
      activateLinkV2(url, location);
      return;
    }

    InlineDocumentation documentation = item.getInlineDocumentation();
    if (documentation == null) return;

    PsiElement context = ((PsiCommentInlineDocumentation)documentation).getContext();
    if (DocRenderLinkActivationHandler.isGotoDeclarationEvent()) {
      navigateToDeclaration(context, url);
    }
    else {
      showDocumentation(item.getEditor(), context, url, location);
    }
  }

  private void activateLinkV2(@NotNull String url, @NotNull Rectangle2D location) {
    DocRenderItem item = (DocRenderItem)myRenderer.getData();
    Editor editor = item.getEditor();
    Project project = editor.getProject();
    if (project == null) {
      return;
    }
    if (DocRenderLinkActivationHandler.isGotoDeclarationEvent()) {
      instance(project).navigateInlineLink(
        url, item::getInlineDocumentationTarget
      );
    }
    else {
      instance(project).activateInlineLink(
        url, item::getInlineDocumentationTarget,
        editor, DocRenderLinkActivationHandler.popupPosition(location, myRenderer)
      );
    }
  }

  private static void navigateToDeclaration(@NotNull PsiElement context, @NotNull String linkUrl) {
    PsiElement targetElement = DocumentationManager.getInstance(context.getProject()).getTargetElement(context, linkUrl);
    if (targetElement instanceof Navigatable) {
      ((Navigatable)targetElement).navigate(true);
    }
  }

  /**
   * @deprecated Unused in v2 implementation.
   */
  @Deprecated
  private void showDocumentation(@NotNull Editor editor,
                                 @NotNull PsiElement context,
                                 @NotNull String linkUrl,
                                 @NotNull Rectangle2D linkLocationWithinInlay) {
    if (isExternalLink(linkUrl)) {
      BrowserUtil.open(linkUrl);
      return;
    }
    Project project = context.getProject();
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    if (QuickDocUtil.getActiveDocComponent(project) == null) {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, DocRenderLinkActivationHandler.popupPosition(linkLocationWithinInlay, myRenderer));
      documentationManager.showJavaDocInfo(editor, context, context, () -> {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, null);
      }, "", false, true);
    }
    DocumentationComponent component = QuickDocUtil.getActiveDocComponent(project);
    if (component != null) {
      if (!documentationManager.hasActiveDockedDocWindow()) {
        component.startWait();
      }
      documentationManager.navigateByLink(component, context, linkUrl);
    }
    if (documentationManager.getDocInfoHint() == null) {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, null);
    }
    if (documentationManager.hasActiveDockedDocWindow()) {
      Disposable disposable = Disposer.newDisposable();
      editor.getCaretModel().addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
          Disposer.dispose(disposable);
        }
      }, disposable);
      documentationManager.muteAutoUpdateTill(disposable);
    }
  }

  private static boolean isExternalLink(@NotNull String linkUrl) {
    String l = linkUrl.toLowerCase(Locale.ROOT);
    return l.startsWith("http://") || l.startsWith("https://");
  }
}

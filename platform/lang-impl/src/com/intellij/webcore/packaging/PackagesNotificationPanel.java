// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webcore.packaging;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;


public class PackagesNotificationPanel {
  private final JEditorPane myHtmlViewer;
  private final Map<String, Runnable> myLinkHandlers = new HashMap<>();
  private @NlsContexts.DialogTitle String myErrorTitle;
  private PackageManagementService.ErrorDescription myErrorDescription;

  public PackagesNotificationPanel() {
    this(PackagesNotificationPanel::showError);
  }

  public PackagesNotificationPanel(@NotNull BiConsumer<? super String, ? super PackageManagementService.ErrorDescription> showErrorFunction) {
    myHtmlViewer = SwingHelper.createHtmlViewer(true, null, null, null);
    myHtmlViewer.setVisible(false);
    myHtmlViewer.setOpaque(true);
    myHtmlViewer.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        final Runnable handler = myLinkHandlers.get(e.getDescription());
        if (handler != null) {
          handler.run();
        }
        else if (myErrorTitle != null && myErrorDescription != null) {
          showErrorFunction.accept(myErrorTitle, myErrorDescription);
        }
      }
    });
  }

  public static void showError(@NotNull @NlsContexts.DialogTitle String title,
                               @NotNull PackageManagementService.ErrorDescription description) {
    final PackagingErrorDialog dialog = new PackagingErrorDialog(title, description);
    dialog.show();
  }

  public void showResult(String packageName, @Nullable PackageManagementService.ErrorDescription errorDescription) {
    if (errorDescription == null) {
      String message = IdeBundle.message("package.installed.successfully");
      if (packageName != null) {
        message = IdeBundle.message("package.0.installed.successfully", packageName);
      }
      showSuccess(message);
    }
    else {
      String title = IdeBundle.message("failed.to.install.packages.dialog.title");
      if (packageName != null) {
        title = IdeBundle.message("failed.to.install.package.dialog.title", packageName);
      }
      String text = IdeBundle.message("install.package.failure", packageName);
      showError(text, title, errorDescription);
    }
  }

  public void addLinkHandler(String key, Runnable handler) {
    myLinkHandlers.put(key, handler);
  }

  public void removeAllLinkHandlers() {
    myLinkHandlers.clear();
  }

  public JComponent getComponent() {
    return myHtmlViewer;
  }

  public void showSuccess(@NotificationContent String text) {
    showContent(text, MessageType.INFO.getPopupBackground());
  }

  private void showContent(@NotNull @NotificationContent String text, @NotNull Color background) {
    String htmlText = text.startsWith("<html>") ? text : UIUtil.toHtml(text);
    myHtmlViewer.setText(htmlText);
    myHtmlViewer.setBackground(background);
    setVisibleEditorPane(true);
    myErrorTitle = null;
    myErrorDescription = null;
  }

  public void showError(@NotificationContent String text,
                        @Nullable @NlsContexts.DialogTitle String detailsTitle,
                        PackageManagementService.ErrorDescription errorDescription) {
    showContent(text, MessageType.ERROR.getPopupBackground());
    myErrorTitle = detailsTitle;
    myErrorDescription = errorDescription;
  }

  public void showWarning(@NotificationContent String text) {
    showContent(text, MessageType.WARNING.getPopupBackground());
  }

  public void hide() {
    setVisibleEditorPane(false);
  }

  private void setVisibleEditorPane(boolean visible) {
    boolean oldVisible = myHtmlViewer.isVisible();
    myHtmlViewer.setVisible(visible);
    if (oldVisible != visible) {
      myHtmlViewer.revalidate();
      myHtmlViewer.repaint();
    }
  }

  public boolean hasLinkHandler(String key) {
    return myLinkHandlers.containsKey(key);
  }

  public void removeLinkHandler(String key) {
    myLinkHandlers.remove(key);
  }
}

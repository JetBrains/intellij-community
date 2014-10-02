package com.intellij.webcore.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class PackagesNotificationPanel {
  private final Project myProject;
  private final JEditorPane myHtmlViewer;
  private final Map<String, Runnable> myLinkHandlers = new HashMap<String, Runnable>();
  private String myErrorTitle;
  private PackageManagementService.ErrorDescription myErrorDescription;

  public PackagesNotificationPanel(@NotNull Project project) {
    myProject = project;
    myHtmlViewer = SwingHelper.createHtmlViewer(true, null, null, null);
    myHtmlViewer.setVisible(false);
    myHtmlViewer.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final Runnable handler = myLinkHandlers.get(e.getDescription());
        if (handler != null) {
          handler.run();
        }
        else if (myErrorTitle != null && myErrorDescription != null) {
          showError(myProject, myErrorTitle, myErrorDescription);
        }
      }
    });
  }

  public static void showError(@NotNull Project project, @NotNull String title,
                               @NotNull PackageManagementService.ErrorDescription description) {
    doShowError(title, description, new DialogBuilder(project));
  }

  public static void showError(@NotNull Component owner, @NotNull String title,
                               @NotNull PackageManagementService.ErrorDescription description) {
    doShowError(title, description, new DialogBuilder(owner));
  }

  private static void doShowError(@NotNull String title, @NotNull PackageManagementService.ErrorDescription description,
                                  @NotNull DialogBuilder builder) {
    if (description.getCommand() != null || description.getOutput() != null || description.getSolution() != null) {
      final PackagingErrorDialog dialog = new PackagingErrorDialog(title, description);
      dialog.show();
    }
    else {
      builder.setTitle(title);
      final JTextArea textArea = new JTextArea();
      textArea.setEditable(false);
      textArea.setText(description.getMessage());
      textArea.setWrapStyleWord(false);
      textArea.setLineWrap(true);
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      final JPanel panel = new JPanel(new BorderLayout(10, 0));
      panel.setPreferredSize(new Dimension(600, 400));
      panel.add(scrollPane, BorderLayout.CENTER);
      panel.add(new JBLabel("Details:", Messages.getErrorIcon(), SwingConstants.LEFT), BorderLayout.NORTH);
      builder.setCenterPanel(panel);
      builder.setButtonsAlignment(SwingConstants.CENTER);
      builder.addOkAction();
      builder.show();
    }
  }

  public void showResult(String packageName, @Nullable PackageManagementService.ErrorDescription errorDescription) {
    if (errorDescription == null) {
      String message = "Package installed successfully";
      if (packageName != null) {
        message = "Package '" + packageName + "' installed successfully";
      }
      showSuccess(message);
    }
    else {
      String title = "Failed to install packages";
      if (packageName != null) {
        title = "Failed to install package '" + packageName + "'";
      }
      String firstLine = "Error occurred when installing package '" + packageName + "'. ";
      showError(firstLine + "<a href=\"xxx\">Details...</a>", title, errorDescription);
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

  public void showSuccess(String text) {
    showContent(text, MessageType.INFO.getPopupBackground());
  }

  private void showContent(@NotNull String text, @NotNull Color background) {
    String htmlText = text.startsWith("<html>") ? text : UIUtil.toHtml(text);
    myHtmlViewer.setText(htmlText);
    myHtmlViewer.setBackground(background);
    setVisibleEditorPane(true);
    myErrorTitle = null;
    myErrorDescription = null;
  }

  public void showError(String text, final String detailsTitle, final PackageManagementService.ErrorDescription errorDescription) {
    showContent(text, MessageType.ERROR.getPopupBackground());
    myErrorTitle = detailsTitle;
    myErrorDescription = errorDescription;
  }

  public void showWarning(String text) {
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

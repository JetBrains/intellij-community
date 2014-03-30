package com.intellij.webcore.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
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
  private final JEditorPane myEditorPane = new JEditorPane();
  private final Project myProject;
  private final Map<String, Runnable> myLinkHandlers = new HashMap<String, Runnable>();
  private String myErrorTitle;
  private String myErrorDescription;

  public PackagesNotificationPanel(Project project) {
    myProject = project;
    myEditorPane.setBackground(UIManager.getColor("ArrowButton.background"));
    myEditorPane.setContentType("text/html");
    myEditorPane.setEditable(false);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
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

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String description) {
    doShowError(title, description, new DialogBuilder(project));
  }

  public static void showError(@NotNull Component owner, @NotNull String title, @NotNull String description) {
    doShowError(title, description, new DialogBuilder(owner));
  }

  private static void doShowError(String title, String description, DialogBuilder builder) {
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setText(description);
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

  public void showResult(String packageName, @Nullable String errorDescription) {
    if (StringUtil.isEmpty(errorDescription)) {
      showSuccess("Package successfully installed.");
    }
    else {
      String title = "Install packages failed";
      final String firstLine = title + ": Error occurred when installing package " + packageName + ". ";
      showError(firstLine + "<a href=\"xxx\">Details...</a>",
                title,
                firstLine + errorDescription);
    }
  }

  public void addLinkHandler(String key, Runnable handler) {
    myLinkHandlers.put(key, handler);
  }

  public JComponent getComponent() {
    return myEditorPane;
  }

  public void showSuccess(String text) {
    showContent(text, MessageType.INFO.getPopupBackground());
  }

  private void showContent(String text, final Color background) {
    myEditorPane.removeAll();
    myEditorPane.setText(UIUtil.toHtml(text));
    myEditorPane.setBackground(background);
    myEditorPane.setVisible(true);
    myErrorTitle = null;
    myErrorDescription = null;
  }

  public void showError(String text, final String detailsTitle, final String detailsDescription) {
    showContent(text, MessageType.ERROR.getPopupBackground());
    myErrorTitle = detailsTitle;
    myErrorDescription = detailsDescription;
  }

  public void showWarning(String text) {
    showContent(text, MessageType.WARNING.getPopupBackground());
  }

  public void hide() {
    myEditorPane.setVisible(false);
  }

  public boolean hasLinkHandler(String key) {
    return myLinkHandlers.containsKey(key);
  }
}

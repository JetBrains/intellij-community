package com.intellij.database.run.ui;

import com.intellij.CommonBundle;
import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.TextCopyProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HintHint;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;

public final class ErrorNotificationPanel extends JPanel {

  private final JEditorPane myMessagePane;
  private final Map<String, Runnable> myActions;
  private final CopyProvider myCopyProvider;
  private final MessageType myType;

  private ErrorNotificationPanel(@NlsContexts.NotificationContent @NotNull String htmlErrorMessage,
                                 @NotNull Map<String, Runnable> actions,
                                 @NotNull MessageType type) {
    super(new BorderLayout());
    myActions = actions;
    myType = type;

    setBorder(JBUI.Borders.empty(0, 4));

    myMessagePane = IdeTooltipManager.initPane(htmlErrorMessage, new HintHint()
      .setAwtTooltip(false)
      .setTextFg(getForeground())
      .setTextBg(getBackground())
      .setBorderColor(getBackground())
      .setBorderInsets(JBInsets.emptyInsets()), null);
    myMessagePane.setBorder(null);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        performAction(e.getDescription());
      }
    });
    myCopyProvider = new TextCopyProvider() {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public @Nullable Collection<String> getTextLinesToCopy() {
        String text = myMessagePane.getSelectedText();
        return StringUtil.isEmpty(text) ? null : Collections.singleton(text);
      }
    };
    add(UiDataProvider.wrapComponent(myMessagePane, sink -> {
      sink.set(PlatformDataKeys.COPY_PROVIDER, this.myCopyProvider);
    }), BorderLayout.CENTER);

    new DumbAwareAction(DataGridBundle.message("action.close.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        performAction(DataGridBundle.message("action.close.text"));
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_ESCAPE), this);
  }

  private void performAction(String actionName) {
    Runnable action = myActions.get(actionName);
    if (action != null) {
      action.run();
    }
  }

  @Override
  public @NotNull Color getBackground() {
    return getType().getPopupBackground();
  }

  @Override
  public @NotNull Color getForeground() {
    return getType().getTitleForeground();
  }

  private @NotNull MessageType getType() {
    return ObjectUtils.chooseNotNull(myType, MessageType.ERROR);
  }

  @Override
  public @NotNull Dimension getMinimumSize() {
    return JBUI.emptySize();
  }

  public JComponent getContent() {
    return myMessagePane;
  }

  public static @NotNull Builder create(@Nullable @NlsContexts.NotificationContent String message, @Nullable Throwable error, @NotNull JComponent baseComponent) {
    return new Builder(message, error, baseComponent);
  }

  public static final class Builder {
    private final boolean myLongMessage;
    private final Throwable myError;
    private final @NlsContexts.NotificationContent String myMessage;
    private final JComponent myBaseComponent;

    private final Map<String, Runnable> myActions = new LinkedHashMap<>();
    private final List<Consumer<Disposable>> myShowHideHandlers = new ArrayList<>();
    private final StringBuilder myHtmlBuilder = new StringBuilder();

    private MessageType myType = MessageType.ERROR;

    private Builder(@NlsContexts.NotificationContent @Nullable String message, @Nullable Throwable error, @NotNull JComponent baseComponent) {
      myError = error;
      myMessage = message;
      myBaseComponent = baseComponent;

      String errorMessage = message == null ? error == null ? null : getNormalizedMessage(error) : getNormalized(message);
      Font font = IdeTooltipManager.getInstance().getTextFont(true);
      FontMetrics fm = baseComponent.getFontMetrics(font);
      myLongMessage = SwingUtilities.computeStringWidth(fm, errorMessage) > baseComponent.getWidth() * 3 / 4;
      if (errorMessage != null) {
        errorMessage = StringUtil.escapeXmlEntities(errorMessage).replace("\n", "<br>");
      }

      myHtmlBuilder.append("<html><head><style type=\"text/css\">a:link {text-decoration:none;}</style></head><body>");
      myHtmlBuilder.append("<font face=\"verdana\"><table width=\"100%\"><tr valign=\"top\"><td>");
      myHtmlBuilder.append(errorMessage);
      myHtmlBuilder.append("</td>");
    }

    public @NotNull Builder messageType(@NotNull MessageType type) {
      myType = type;
      return this;
    }

    public @NotNull Builder addIconLink(String command, @NlsContexts.Tooltip String tooltipText, @NotNull Icon realIcon, @Nullable Runnable action) {
      String iconPath = GridUtil.getIconPath(realIcon);

      startActionColumn();
      myHtmlBuilder.append("<a href=\"")
        .append(command).append("\"><icon alt=\"").append(tooltipText).append("\"")
        .append("\" src=\"");
      myHtmlBuilder.append(iconPath).append("\"></a>");
      endActionColumn();

      if (action != null) {
        myActions.put(command, action);
      }

      return this;
    }

    public @NotNull Builder addSpace() {
      startActionColumn();
      endActionColumn();
      return this;
    }

    public @NotNull Builder addLink(@NonNls @NotNull String command, @NlsActions.ActionText @NotNull String linkHtml, @NotNull Runnable action) {
      startActionColumn();
      int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(command);
      @NlsSafe
      String fixedCommand = mnemonicIndex < 0 ? command : command.substring(0, mnemonicIndex) + command.substring(mnemonicIndex + 1);
      ContainerUtil.addIfNotNull(myShowHideHandlers, createMnemonicActionIfNeeded(fixedCommand, mnemonicIndex, action, myBaseComponent));
      myHtmlBuilder.append("<a style=\"text-decoration:none;\" href=\"").append(fixedCommand).append("\">").append(linkHtml).append("</a>");
      endActionColumn();
      myActions.put(fixedCommand, action);
      return this;
    }

    public @NotNull Builder addDetailsButton() {
      final String message = myError == null ? myMessage : myError.getStackTrace().length > 0 ? ExceptionUtil.getThrowableText(myError, "com.intellij.") : myError.getMessage();
      if (StringUtil.contains(myHtmlBuilder, message)) return this;
      return addLink("details", DataGridBundle.message("action.details.text"), () -> Messages.showIdeaMessageDialog(null, message,
                                                                                                                    DataGridBundle.message("dialog.title.query.error"),
                                                                                                                    new String[]{CommonBundle.getOkButtonText()}, 0, Messages.getErrorIcon(), null));
    }

    public @NotNull Builder addCloseButton(Runnable action) {
      return addIconLink(DataGridBundle.message("action.close.text"), DataGridBundle.message("tooltip.close.esc"), AllIcons.Actions.Close, action);
    }

    public @NotNull ErrorNotificationPanel build() {
      myHtmlBuilder.append("</tr></table></font></body>");
      ErrorNotificationPanel result = new ErrorNotificationPanel(myHtmlBuilder.toString(), myActions, myType); //NON-NLS
      registerShowHideHandlers(result);
      return result;
    }

    private void startActionColumn() {
      myHtmlBuilder.append("<td width=\"1%\" align=\"right\" valign=\"")
        .append(myLongMessage ? "top" : "middle")
        .append("\" nowrap><div style='margin:0px 2px 0px 2px'>");
    }

    private void endActionColumn() {
      myHtmlBuilder.append("</div></td>");
    }

    private static @NlsContexts.NotificationContent @NotNull String getNormalizedMessage(@NotNull Throwable error) {
      String sourceMessage = StringUtil.notNullize(error.getMessage(),
                                                   DataGridBundle.message("notification.content.unknown.problem.occurred.see.details"));
      // In some cases source message contains stacktrace inside. Let's chop it
      int divPos = sourceMessage.indexOf("\n\tat ");
      if (divPos != -1) {
        sourceMessage = sourceMessage.substring(0, divPos);
      }
      return getNormalized(sourceMessage);
    }

    private static @NlsContexts.NotificationContent @NotNull String getNormalized(@NlsContexts.NotificationContent @NotNull String sourceMessage) {
      int lineLimit = StringUtil.lineColToOffset(sourceMessage, 5, 0);
      int charLimit = 1024;
      int limit = lineLimit == -1 || lineLimit > charLimit ? charLimit : lineLimit;
      return StringUtil.trimLog(sourceMessage, limit + 1);
    }

    private void registerShowHideHandlers(@NotNull JComponent component) {
      if (myShowHideHandlers.isEmpty()) return;

      component.addHierarchyListener(new HierarchyListener() {
        private Disposable myShownDisposable;
        @Override
        public void hierarchyChanged(HierarchyEvent e) {
          Component c = e.getComponent();
          if (c == null || (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) <= 0) return;

          if (c.isShowing()) {
            myShownDisposable = Disposer.newDisposable();
            for (Consumer<Disposable> handler : myShowHideHandlers) {
              handler.consume(myShownDisposable);
            }
            return;
          }

          if (myShownDisposable != null) Disposer.dispose(myShownDisposable);
          myShownDisposable = null;
        }
      });
    }

    private static @Nullable Consumer<Disposable> createMnemonicActionIfNeeded(final @NlsActions.ActionText String command,
                                                                               final int mnemonicIndex,
                                                                               final Runnable runnable,
                                                                               final JComponent component) {
      if (mnemonicIndex < 0) return null;
      return (parentDisposable) -> {
        DumbAwareAction a = new DumbAwareAction(command) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            runnable.run();
          }
        };
        int modifiers = SystemInfo.isMac && !Registry.is("ide.mac.alt.mnemonic.without.ctrl") ?
                        InputEvent.ALT_MASK | InputEvent.CTRL_MASK : InputEvent.ALT_MASK;
        KeyStroke keyStroke = KeyStroke.getKeyStroke(Character.toUpperCase(command.charAt(mnemonicIndex)), modifiers);
        a.registerCustomShortcutSet(new CustomShortcutSet(keyStroke), component, parentDisposable);
      };
    }
  }
}

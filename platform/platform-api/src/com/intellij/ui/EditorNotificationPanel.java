// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.intention.*;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
import com.intellij.icons.ExpUiIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicPanelUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
public class EditorNotificationPanel extends JPanel implements IntentionActionProvider, Weighted {

  private static final Supplier<EditorColorsScheme> GLOBAL_SCHEME_SUPPLIER = () -> EditorColorsManager.getInstance().getGlobalScheme();
  private static final Consumer<Class<?>> VOID_CONSUMER = __ -> {
  };

  protected final JLabel myLabel = new JLabel();
  protected final JLabel myGearLabel = new JLabel();
  protected final JPanel myLinksPanel = new NonOpaquePanel(new HorizontalLayout(16));

  private JPanel myLastPanel;
  private InplaceButton myCloseButton;
  private Runnable myCloseAction;

  private static Icon CLOSE_ICON;

  private final @NotNull Supplier<? extends EditorColorsScheme> mySchemeSupplier;
  protected final @Nullable Color myBackgroundColor;
  protected final @NotNull ColorKey myBackgroundColorKey;

  private @NotNull Consumer<? super Class<?>> myClassConsumer = VOID_CONSUMER;

  public EditorNotificationPanel(@Nullable Color backgroundColor) {
    this(null, backgroundColor);
  }

  public EditorNotificationPanel(@Nullable Color backgroundColor, @NotNull Status status) {
    this((FileEditor)null, backgroundColor, null, status);
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor,
                                 @Nullable Color backgroundColor) {
    this(fileEditor, backgroundColor, null);
  }

  public EditorNotificationPanel(@NotNull ColorKey backgroundColorKey) {
    this((FileEditor)null, null, backgroundColorKey);
  }

  public EditorNotificationPanel(@NotNull ColorKey backgroundColorKey, @NotNull Status status) {
    this((FileEditor)null, null, backgroundColorKey, status);
  }

  /**
   * Uses LookAndFeel <code>Label.foreground</code> color for the label and
   * <code>JBUI.CurrentTheme.Link.Foreground.ENABLED</code> for links foreground.
   */
  public EditorNotificationPanel() {
    this((FileEditor)null);
  }

  /**
   * If fileEditor is a <code>TextEditor</code> based then use the editor colors scheme for foreground colors:
   * EditorColorsScheme default foreground (<code>EditorColorsScheme.getDefaultForeground()</code> for the label and
   * <code>CodeInsightColors.HYPERLINK_ATTRIBUTES</code>'s foreground color for links foreground.
   * Most often this component is created from <code>EditorNotifications.Provider.createNotificationPanel</code> methods where
   * <code>FileEditor</code> is available. So this constructor is preferred over the default one.
   *
   * @param fileEditor is editor instance. null is equivalent to default constructor.
   */
  public EditorNotificationPanel(@Nullable FileEditor fileEditor) {
    this(fileEditor, null, null);
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor,
                                 @Nullable Color backgroundColor,
                                 @Nullable ColorKey backgroundColorKey) {
    this(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null,
         backgroundColor,
         backgroundColorKey);
  }

  public EditorNotificationPanel(@Nullable Editor editor,
                                 @Nullable Color backgroundColor,
                                 @Nullable ColorKey backgroundColorKey) {
    super(new BorderLayout());

    mySchemeSupplier = editor != null ? () -> editor.getColorsScheme() : GLOBAL_SCHEME_SUPPLIER;
    myBackgroundColor = backgroundColor;
    myBackgroundColorKey = backgroundColorKey != null ? backgroundColorKey : EditorColors.NOTIFICATION_BACKGROUND;
    putClientProperty(FileEditorManager.SEPARATOR_COLOR, JBUI.CurrentTheme.Editor.BORDER_COLOR);

    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, myLabel);
    panel.add(BorderLayout.EAST, myLinksPanel);
    panel.setMinimumSize(new Dimension(0, 0));

    Wrapper gearWrapper = new Wrapper(myGearLabel);
    gearWrapper.setBorder(new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return myGearLabel.getIcon() == null ? super.getBorderInsets(c) : new JBInsets(0, 5, 0, 0);
      }
    });

    add(BorderLayout.CENTER, panel);
    add(BorderLayout.EAST, gearWrapper);
    JBInsets defaultInsets = ExperimentalUI.isNewUI() ? JBInsets.create(9, 16) : JBInsets.create(5, 10);
    setBorder(JBUI.Borders.empty(JBUI.CurrentTheme.Editor.Notification.borderInsets(defaultInsets)));
    setOpaque(true);

    myLabel.setForeground(mySchemeSupplier.get().getDefaultForeground());
  }

  public EditorNotificationPanel(@NotNull Status status) {
    this((Editor)null, null, null, status);
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor, @NotNull Status status) {
    this(fileEditor, null, null, status);
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor,
                                 @Nullable Color backgroundColor,
                                 @Nullable ColorKey backgroundColorKey,
                                 @NotNull Status status) {
    this(fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null, backgroundColor, backgroundColorKey, status);
  }

  public EditorNotificationPanel(@Nullable Editor editor,
                                 @Nullable Color backgroundColor,
                                 @Nullable ColorKey backgroundColorKey,
                                 @NotNull Status status) {
    this(editor, ExperimentalUI.isNewUI() ? status.background : backgroundColor, backgroundColorKey);

    if (!ExperimentalUI.isNewUI()) {
      return;
    }

    myLabel.setIconTextGap(JBUI.scale(8));
    myLabel.setIcon(new Icon() {
      @Override
      public void paintIcon(Component component, Graphics graphics, int x, int y) {
        if (!StringUtil.isEmpty(myLabel.getText())) {
          status.icon.paintIcon(component, graphics, x, y);
        }
      }

      @Override
      public int getIconWidth() {
        return status.icon.getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return status.icon.getIconHeight();
      }
    });
    myLabel.setForeground(JBUI.CurrentTheme.Banner.FOREGROUND);
    myLabel.setBorder(JBUI.Borders.emptyRight(20));

    setBorder(JBUI.Borders.empty(JBUI.CurrentTheme.Editor.Notification.borderInsets()));

    putClientProperty(FileEditorManager.SEPARATOR_BORDER, new SideBorder(status.border, SideBorder.TOP | SideBorder.BOTTOM));

    Container parent = myGearLabel.getParent();
    parent.remove(myGearLabel);
    remove(parent);

    myLastPanel = new NonOpaquePanel(new HorizontalLayout(16)) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (myGearLabel.getIcon() == null && (myCloseButton == null || !myCloseButton.isVisible())) {
          size.width = 0;
        }
        return size;
      }
    };
    myLastPanel.setBorder(new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return myGearLabel.getIcon() == null && (myCloseButton == null || !myCloseButton.isVisible())
               ? super.getBorderInsets(c) : new JBInsets(0, 16, 0, 0);
      }
    });
    myLastPanel.add(myGearLabel);
    add(BorderLayout.EAST, myLastPanel);
  }

  @Override
  public void updateUI() {
    setUI(new BasicPanelUI() {
      @Override protected void installDefaults(JPanel p) {}
    });
  }

  @ApiStatus.Internal
  public @Nullable ColorKey getBackgroundColorKey() {
    return myBackgroundColor == null ? myBackgroundColorKey : null;
  }

  @ApiStatus.Internal
  public @Nullable Color getOverriddenBackgroundColor() {
    return myBackgroundColor;
  }

  @Override
  public Color getBackground() {
    return ObjectUtils.notNull(getOverriddenBackgroundColor(),
             ObjectUtils.notNull(mySchemeSupplier.get().getColor(getBackgroundColorKey()), getFallbackBackgroundColor()));
  }

  @ApiStatus.Internal
  public @NotNull Color getFallbackBackgroundColor() {
    return UIUtil.getToolTipBackground();
  }

  @ApiStatus.Internal
  public void setClassConsumer(@NotNull Consumer<? super Class<?>> classConsumer) {
    myClassConsumer = classConsumer;
  }

  public static Color getToolbarBackground() {
    return UIUtil.getPanelBackground();
  }

  public void setText(@NotNull @Label String text) {
    myLabel.setText(text);
  }

  public EditorNotificationPanel text(@NotNull @Label String text) {
    myLabel.setText(text);
    return this;
  }

  public @NotNull @Label String getText() {
    return myLabel.getText();
  }

  public EditorNotificationPanel icon(@NotNull Icon icon) {
    myLabel.setIcon(icon);
    return this;
  }

  public EditorNotificationPanel noIcon() {
    myLabel.setIcon(null);
    return this;
  }

  public final @NotNull HyperlinkLabel createActionLabel(@NotNull @LinkLabel String text,
                                                         @NotNull @NonNls String actionId) {
    return createActionLabel(text, actionId, true);
  }

  public final @NotNull HyperlinkLabel createActionLabel(@NotNull @LinkLabel String text,
                                                         @NotNull @NonNls String actionId,
                                                         boolean showInIntentionMenu) {
    return createActionLabel(text, () -> executeAction(actionId), showInIntentionMenu);
  }

  public final @NotNull HyperlinkLabel createActionLabel(@NotNull @LinkLabel String text,
                                                         @NotNull Runnable action) {
    return createActionLabel(text, action, true);
  }

  private static @NotNull Icon getCloseIcon() {
    if (CLOSE_ICON == null) {
      CLOSE_ICON = ExpUiIcons.General.Close;
    }
    return CLOSE_ICON;
  }

  public final @NotNull InplaceButton setCloseAction(@NotNull Runnable action) {
    myCloseAction = action;
    if (myCloseButton == null) {
      myCloseButton = new InplaceButton(IdeBundle.message("editor.banner.close.tooltip"), getCloseIcon(), e -> {
        myCloseAction.run();
      });
      myCloseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      if (myLastPanel == null) {
        myLinksPanel.add(myCloseButton);
      }
      else {
        myLastPanel.add(myCloseButton);
      }
    }
    return myCloseButton;
  }

  public interface ActionHandler {
    /**
     * Invoked when an action-link click from the notification panel
     */
    void handlePanelActionClick(@NotNull EditorNotificationPanel panel,
                                @NotNull HyperlinkEvent event);

    /**
     * Invoked when an action is executed as
     * an editor <i>intention action</i> from the related editor
     */
    void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile psiFile);
  }

  public @NotNull HyperlinkLabel createActionLabel(@NotNull @LinkLabel String text,
                                                   @NotNull Runnable action,
                                                   boolean showInIntentionMenu) {
    return new ActionHyperlinkLabel(text,
                                    withLogNotifications(action),
                                    showInIntentionMenu);
  }

  public @NotNull HyperlinkLabel createActionLabel(@NotNull @LinkLabel String text,
                                                   @NotNull ActionHandler handler,
                                                   boolean showInIntentionMenu) {
    return new ActionHyperlinkLabel(text,
                                    withNotifications(handler),
                                    showInIntentionMenu);
  }

  public void clear() {
    myLabel.setText("");
    myLinksPanel.removeAll();
  }

  protected void executeAction(@NonNls String actionId) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    DataContext dataContext = DataManager.getInstance().getDataContext(this);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, getActionPlace(), dataContext);
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }

  protected @NotNull String getActionPlace() {
    return ActionPlaces.UNKNOWN;
  }

  @Override
  public @Nullable IntentionActionWithOptions getIntentionAction() {
    MyIntentionAction action = new MyIntentionAction();
    return action.myOptions.isEmpty() ? null : action;
  }

  @Override
  public double getWeight() {
    return 0;
  }

  protected @Nullable @IntentionName String getIntentionActionText() {
    return null;
  }

  protected @NotNull PriorityAction.Priority getIntentionActionPriority() {
    return PriorityAction.Priority.NORMAL;
  }

  protected @NotNull @Nls String getIntentionActionFamilyName() {
    return IdeBundle.message("intention.family.editor.notification");
  }

  private void logNotificationActionInvocation(@NotNull Object handlerClass) {
    myClassConsumer.accept(handlerClass.getClass());
  }

  private @NotNull EditorNotificationPanel.ActionHandler withLogNotifications(@NotNull Runnable action) {
    return new ActionHandler() {
      @Override
      public void handlePanelActionClick(@NotNull EditorNotificationPanel panel,
                                         @NotNull HyperlinkEvent e) {
        logNotificationActionInvocation(action);
        action.run();
      }

      @Override
      public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile file) {
        logNotificationActionInvocation(action);
        action.run();
      }
    };
  }

  private @NotNull EditorNotificationPanel.ActionHandler withNotifications(@NotNull EditorNotificationPanel.ActionHandler handler) {
    return new ActionHandler() {
      @Override
      public void handlePanelActionClick(@NotNull EditorNotificationPanel panel,
                                         @NotNull HyperlinkEvent e) {
        logNotificationActionInvocation(handler);
        handler.handlePanelActionClick(panel, e);
      }

      @Override
      public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile file) {
        logNotificationActionInvocation(handler);
        handler.handleQuickFixClick(editor, file);
      }
    };
  }

  private final class ActionHyperlinkLabel extends HyperlinkLabel {
    private JLabel mySizeLabel;
    private final @NotNull ActionHandler myHandler;
    private final boolean myShowInIntentionMenu;

    private ActionHyperlinkLabel(@NotNull @LinkLabel String text,
                                 @NotNull EditorNotificationPanel.ActionHandler handler,
                                 boolean showInIntentionMenu) {
      super(text, EditorNotificationPanel.this.getBackground());
      myHandler = handler;
      myShowInIntentionMenu = showInIntentionMenu;

      myLinksPanel.add(this);

      addHyperlinkListener(new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
          myHandler.handlePanelActionClick(EditorNotificationPanel.this, e);
        }
      });
    }

    void handleIntentionActionClick(Editor editor, PsiFile file) {
      if (editor == null || file == null) return;
      myHandler.handleQuickFixClick(editor, file);
    }

    @Override
    protected int getStringWidth(@Nls String text, FontMetrics fm) {
      if (mySizeLabel == null) {
        mySizeLabel = new JLabel();
      }
      mySizeLabel.setText(text);
      mySizeLabel.setFont(fm.getFont());
      return mySizeLabel.getPreferredSize().width;
    }
  }

  private final class MyIntentionAction implements IntentionActionWithOptions, Iconable, PriorityAction {
    private final List<IntentionAction> myOptions = new ArrayList<>();

    private MyIntentionAction() {
      for (Component component : myLinksPanel.getComponents()) {
        if (component instanceof HyperlinkLabel) {
          if (component instanceof ActionHyperlinkLabel && !((ActionHyperlinkLabel)component).myShowInIntentionMenu) {
            continue;
          }
          myOptions.add(new MyLinkOption(((HyperlinkLabel)component)));
        }
      }
      if (myGearLabel.getIcon() != null) {
        myOptions.add(new MySettingsOption(myGearLabel));
      }
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myOptions.get(0).invoke(project, editor, file);
    }

    @Override
    public boolean startInWriteAction() {
      return myOptions.get(0).startInWriteAction();
    }

    @Override
    public @NotNull List<IntentionAction> getOptions() {
      return myOptions.isEmpty() ? Collections.emptyList() : myOptions.subList(1, myOptions.size());
    }

    @Override
    public @NotNull String getText() {
      String textOverride = getIntentionActionText();
      if (textOverride != null) {
        return textOverride;
      }

      if (!myOptions.isEmpty()) {
        return myOptions.get(0).getText();
      }
      String text = myLabel.getText();
      return StringUtil.isEmpty(text) ? EditorBundle.message("editor.notification.default.action.name")
                                      : StringUtil.shortenTextWithEllipsis(text, 50, 0);
    }

    @Override
    public @NotNull Priority getPriority() {
      return getIntentionActionPriority();
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return getIntentionActionFamilyName();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return AllIcons.Actions.IntentionBulb;
    }
  }

  private static final class MyLinkOption implements IntentionAction {
    private final HyperlinkLabel myLabel;

    private MyLinkOption(HyperlinkLabel label) {
      myLabel = label;
    }

    @Override
    public @Nls @NotNull String getText() {
      return myLabel.getText();
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return IdeBundle.message("intention.family.editor.notification.option");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      if (myLabel instanceof ActionHyperlinkLabel) {
        ((ActionHyperlinkLabel)myLabel).handleIntentionActionClick(editor, file);
      } else {
        myLabel.doClick();
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static final class MySettingsOption implements IntentionAction, Iconable, LowPriorityAction {
    private final JLabel myLabel;

    private MySettingsOption(JLabel label) {
      myLabel = label;
    }

    @Override
    public @Nls @NotNull String getText() {
      return EditorBundle.message("editor.notification.settings.option.name");
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return IdeBundle.message("intention.family.editor.notification.settings");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myLabel.dispatchEvent(new MouseEvent(myLabel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 0, 0, 1, false));
      myLabel.dispatchEvent(new MouseEvent(myLabel, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 0, 0, 1, false));
      myLabel.dispatchEvent(new MouseEvent(myLabel, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public Icon getIcon(@IconFlags int flags) {
      return myLabel.getIcon();
    }
  }

  public enum Status {
    Info(JBUI.CurrentTheme.Banner.INFO_BACKGROUND, JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR, AllIcons.General.BalloonInformation),
    Success(JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND, JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR, AllIcons.Debugger.ThreadStates.Idle),
    Warning(JBUI.CurrentTheme.Banner.WARNING_BACKGROUND, JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR, AllIcons.General.BalloonWarning),
    Error(JBUI.CurrentTheme.Banner.ERROR_BACKGROUND, JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR, AllIcons.General.BalloonError);

    final Color background;
    final Color border;
    final Icon icon;

    Status(@NotNull Color background, @NotNull Color border, @NotNull Icon icon) {
      this.background = background;
      this.border = border;
      this.icon = icon;
    }
  }
}

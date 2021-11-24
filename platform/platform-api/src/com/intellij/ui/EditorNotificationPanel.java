// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.codeInsight.intention.*;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.basic.BasicPanelUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Dmitry Avdeev
 */
public class EditorNotificationPanel extends JPanel implements IntentionActionProvider, Weighted {
  protected final JLabel myLabel = new JLabel();
  protected final JLabel myGearLabel = new JLabel();
  protected final JPanel myLinksPanel = new NonOpaquePanel(new HorizontalLayout(16));
  protected Color myBackgroundColor;
  protected @NotNull ColorKey myBackgroundColorKey = EditorColors.NOTIFICATION_BACKGROUND;
  @Nullable private Key<?> myProviderKey;
  private Project myProject;
  private final @NotNull Supplier<? extends EditorColorsScheme> mySchemeSupplier;

  protected static final Supplier<EditorColorsScheme> GLOBAL_SCHEME_SUPPLIER = () -> EditorColorsManager.getInstance().getGlobalScheme();

  public EditorNotificationPanel(@Nullable Color backgroundColor) {
    this();
    myBackgroundColor = backgroundColor;
  }

  public EditorNotificationPanel(@NotNull ColorKey backgroundColorKey) {
    this();
    myBackgroundColorKey = backgroundColorKey;
  }

  /**
   * Uses LookAndFeel <code>Label.foreground</code> color for the label and
   * <code>JBUI.CurrentTheme.Link.Foreground.ENABLED</code> for links foreground.
   */
  public EditorNotificationPanel() {
    this(GLOBAL_SCHEME_SUPPLIER);
  }

  /**
   * If fileEditor is a <code>TextEditor</code> based then use the editor colors scheme for foreground colors:
   * EditorColorsScheme default foreground (<code>EditorColorsScheme.getDefaultForeground()</code> for the label and
   * <code>CodeInsightColors.HYPERLINK_ATTRIBUTES</code>'s foreground color for links foreground.
   *
   * Most often this component is created from <code>EditorNotifications.Provider.createNotificationPanel</code> methods where
   * <code>FileEditor</code> is available. So this constructor is preferred over the default one.
   *
   * @param fileEditor is editor instance. null is equivalent to default constructor.
   */
  public EditorNotificationPanel(@Nullable FileEditor fileEditor) {
    this(fileEditorSupplier(fileEditor));
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor,
                                 @NotNull Color backgroundColor) {
    this(fileEditorSupplier(fileEditor));
    myBackgroundColor = backgroundColor;
  }

  public EditorNotificationPanel(@Nullable FileEditor fileEditor,
                                 @NotNull ColorKey backgroundColorKey) {
    this(fileEditorSupplier(fileEditor));
    myBackgroundColorKey = backgroundColorKey;
  }

  public EditorNotificationPanel(@NotNull Supplier<? extends EditorColorsScheme> schemeSupplier) {
    super(new BorderLayout());

    JPanel panel = new NonOpaquePanel(new BorderLayout());
    panel.add(BorderLayout.CENTER, myLabel);
    panel.add(BorderLayout.EAST, myLinksPanel);
    panel.setBorder(JBUI.Borders.empty(5, 0, 5, 5));
    panel.setMinimumSize(new Dimension(0, 0));

    add(BorderLayout.CENTER, panel);
    add(BorderLayout.EAST, myGearLabel);
    setBorder(JBUI.Borders.empty(0, 10));
    setOpaque(true);

    mySchemeSupplier = schemeSupplier;
    myLabel.setForeground(mySchemeSupplier.get().getDefaultForeground());
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

  public void setProject(Project project) {
    myProject = project;
  }

  public void setProviderKey(@Nullable Key<?> key) {
    myProviderKey = key;
  }

  public static Color getToolbarBackground() {
    return UIUtil.getPanelBackground();
  }

  public void setText(@LinkLabel String text) {
    myLabel.setText(text);
  }

  public EditorNotificationPanel text(@NotNull @NlsContexts.Label String text) {
    myLabel.setText(text);
    return this;
  }

  @NotNull
  public String getText() {
    return myLabel.getText();
  }

  public EditorNotificationPanel icon(@NotNull Icon icon) {
    myLabel.setIcon(icon);
    return this;
  }

  @NotNull
  public HyperlinkLabel createActionLabel(@LinkLabel String text, @NonNls final String actionId) {
    return createActionLabel(text, actionId, true);
  }

  @NotNull
  public HyperlinkLabel createActionLabel(@LinkLabel String text,
                                          @NonNls final String actionId,
                                          boolean showInIntentionMenu) {
    return createActionLabel(text, () -> executeAction(actionId), showInIntentionMenu);
  }

  @NotNull
  public HyperlinkLabel createActionLabel(@LinkLabel String text, @NotNull Runnable action) {
    return createActionLabel(text, action, true);
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

  @NotNull
  public HyperlinkLabel createActionLabel(@LinkLabel String text,
                                          @NotNull final Runnable action,
                                          boolean showInIntentionMenu) {
    return createActionLabelImpl(text, withLogNotifications(action), showInIntentionMenu);
  }

  @NotNull
  public HyperlinkLabel createActionLabel(@LinkLabel String text,
                                          final ActionHandler handler,
                                          boolean showInIntentionMenu) {
    return createActionLabelImpl(text, withNotifications(handler), showInIntentionMenu);
  }

  @NotNull
  private HyperlinkLabel createActionLabelImpl(@LinkLabel String text,
                                               final ActionHandler handler,
                                               boolean showInIntentionMenu) {
    ActionHyperlinkLabel label = new ActionHyperlinkLabel(this, text, getBackground(), showInIntentionMenu, handler);
    myLinksPanel.add(label);
    return label;
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

  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.UNKNOWN;
  }

  @Nullable
  @Override
  public IntentionActionWithOptions getIntentionAction() {
    MyIntentionAction action = new MyIntentionAction();
    return action.myOptions.isEmpty() ? null : action;
  }

  @Override
  public double getWeight() {
    return 0;
  }

  @Nullable
  protected @IntentionName String getIntentionActionText() {
    return null;
  }

  @NotNull
  protected PriorityAction.Priority getIntentionActionPriority() {
    return PriorityAction.Priority.NORMAL;
  }

  @NotNull
  @Nls
  protected String getIntentionActionFamilyName() {
    return IdeBundle.message("intention.family.editor.notification");
  }

  private static Supplier<EditorColorsScheme> fileEditorSupplier(@Nullable FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      return () -> editor.getColorsScheme();
    }
    else {
      return GLOBAL_SCHEME_SUPPLIER;
    }
  }

  private void logNotificationActionInvocation(@NotNull Object handlerClass) {
    if (myProject != null) {
      EditorNotifications.getInstance(myProject).logNotificationActionInvocation(myProviderKey, handlerClass.getClass());
    }
  }

  @NotNull
  private EditorNotificationPanel.ActionHandler withLogNotifications(@NotNull Runnable action) {
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

  @NotNull
  private EditorNotificationPanel.ActionHandler withNotifications(@NotNull EditorNotificationPanel.ActionHandler handler) {
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

  private static final class ActionHyperlinkLabel extends HyperlinkLabel {
    private final boolean myShowInIntentionMenu;
    private final ActionHandler myHandler;

    private ActionHyperlinkLabel(@NotNull EditorNotificationPanel notificationPanel,
                                 @LinkLabel String text,
                                 Color background,
                                 boolean showInIntentionMenu,
                                 @NotNull EditorNotificationPanel.ActionHandler handler) {
      super(text, background);
      myShowInIntentionMenu = showInIntentionMenu;
      myHandler = handler;

      addHyperlinkListener(new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          if (e == null) return;
          myHandler.handlePanelActionClick(notificationPanel, e);
        }
      });
    }

    void handleIntentionActionClick(Editor editor, PsiFile file) {
      if (editor == null || file == null) return;
      myHandler.handleQuickFixClick(editor, file);
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

    @NotNull
    @Override
    public List<IntentionAction> getOptions() {
      return myOptions.isEmpty() ? Collections.emptyList() : myOptions.subList(1, myOptions.size());
    }

    @NotNull
    @Override
    public String getText() {
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

    @NotNull
    @Override
    public Priority getPriority() {
      return getIntentionActionPriority();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
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

    @Nls
    @NotNull
    @Override
    public String getText() {
      return myLabel.getText();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
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

    @Nls
    @NotNull
    @Override
    public String getText() {
      return EditorBundle.message("editor.notification.settings.option.name");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
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
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.*;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer;
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.items.RunAnythingCommandItem;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.execution.actions.RunConfigurationsComboBoxAction.EMPTY_ICON;
import static com.intellij.ide.actions.GotoActionAction.performAction;
import static com.intellij.ide.actions.runAnything.RunAnythingAction.SHIFT_IS_PRESSED;
import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static com.intellij.ui.SimpleTextAttributes.STYLE_SEARCH_MATCH;

public class RunAnythingUtil {
  public static final Logger LOG = Logger.getInstance(RunAnythingUtil.class);
  static final String SHIFT_SHORTCUT_TEXT = KeymapUtil.getShortcutText(KeyboardShortcut.fromString(("SHIFT")));
  public static final String AD_ACTION_TEXT = IdeBundle.message("run.anything.ad.run.action.with.default.settings", SHIFT_SHORTCUT_TEXT);
  public static final String AD_DEBUG_TEXT = IdeBundle.message("run.anything.ad.run.with.debug", SHIFT_SHORTCUT_TEXT);
  public static final String AD_CONTEXT_TEXT =
    IdeBundle.message("run.anything.ad.run.in.context", KeymapUtil.getShortcutText(KeyboardShortcut.fromString("pressed ALT")));
  public static final String AD_DELETE_COMMAND_TEXT =
    IdeBundle.message("run.anything.ad.command.delete", KeymapUtil.getShortcutText(KeyboardShortcut.fromString("shift BACK_SPACE")));
  private static final Key<Collection<Pair<String, String>>> RUN_ANYTHING_WRAPPED_COMMANDS = Key.create("RUN_ANYTHING_WRAPPED_COMMANDS");
  private static final Border RENDERER_TITLE_BORDER = JBUI.Borders.emptyTop(3);
  private static final String DEBUGGER_FEATURE_USAGE = RunAnythingAction.RUN_ANYTHING + " - " + "DEBUGGER";

  static Font getTitleFont() {
    return UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL));
  }

  static JComponent createTitle(String titleText) {
    JLabel titleLabel = new JLabel(titleText);
    titleLabel.setFont(getTitleFont());
    titleLabel.setForeground(UIUtil.getLabelDisabledForeground());
    SeparatorComponent separatorComponent =
      new SeparatorComponent(titleLabel.getPreferredSize().height / 2, new JBColor(Gray._220, Gray._80), null);

    return JBUI.Panels.simplePanel(5, 10)
                      .addToCenter(separatorComponent)
                      .addToLeft(titleLabel)
                      .withBorder(RENDERER_TITLE_BORDER)
                      .withBackground(UIUtil.getListBackground());
  }

  public static Color defaultActionForeground(boolean isSelected, @Nullable Presentation presentation) {
    if (presentation != null && (!presentation.isEnabled() || !presentation.isVisible())) return UIUtil.getInactiveTextColor();
    if (isSelected) return UIUtil.getListSelectionForeground();
    return UIUtil.getListForeground();
  }

  static String getSettingText(OptionDescription value) {
    String hit = value.getHit();
    if (hit == null) {
      hit = value.getOption();
    }
    hit = StringUtil.unescapeXml(hit);
    if (hit.length() > 60) {
      hit = hit.substring(0, 60) + "...";
    }
    hit = hit.replace("  ", " "); //avoid extra spaces from mnemonics and xml conversion
    String text = hit.trim();
    text = StringUtil.trimEnd(text, ":");
    return text;
  }

  static int getPopupMaxWidth() {
    return PropertiesComponent.getInstance().getInt("run.anything.max.popup.width", JBUI.scale(600));
  }

  static void initTooltip(JComponent label) {
    label.setToolTipText("<html><body>Press <b>" + getShortcut() + "</b> to execute any command</body></html>");
  }

  @Nullable
  static String getInitialTextForNavigation(@Nullable Editor editor) {
    if (editor != null) {
      final String selectedText = editor.getSelectionModel().getSelectedText();
      if (selectedText != null && !selectedText.contains("\n")) {
        return selectedText;
      }
    }
    return null;
  }

  static void adjustPopup(JBPopup balloon, JBPopup popup) {
    final Dimension d = PopupPositionManager.PositionAdjuster.getPopupSize(popup);
    final JComponent myRelativeTo = balloon.getContent();
    Point myRelativeOnScreen = myRelativeTo.getLocationOnScreen();
    Rectangle screen = ScreenUtil.getScreenRectangle(myRelativeOnScreen);
    Rectangle popupRect = null;
    Rectangle r = new Rectangle(myRelativeOnScreen.x, myRelativeOnScreen.y + myRelativeTo.getHeight(), d.width, d.height);

    if (screen.contains(r)) {
      popupRect = r;
    }

    if (popupRect != null) {
      Point location = new Point(r.x, r.y);
      if (!location.equals(popup.getLocationOnScreen())) {
        popup.setLocation(location);
      }
    }
    else {
      if (r.y + d.height > screen.y + screen.height) {
        r.height = screen.y + screen.height - r.y - 2;
      }
      if (r.width > screen.width) {
        r.width = screen.width - 50;
      }
      if (r.x + r.width > screen.x + screen.width) {
        r.x = screen.x + screen.width - r.width - 2;
      }

      popup.setSize(r.getSize());
      popup.setLocation(r.getLocation());
    }
  }

  static void jumpNextGroup(boolean forward, JBList list) {
    final int index = list.getSelectedIndex();
    final RunAnythingSearchListModel model = RunAnythingAction.getSearchingModel(list);
    if (model != null && index >= 0) {
      final int newIndex = forward ? model.next(index) : model.prev(index);
      list.setSelectedIndex(newIndex);
      int more = model.next(newIndex) - 1;
      if (more < newIndex) {
        more = list.getItemsCount() - 1;
      }
      ScrollingUtil.ensureIndexIsVisible(list, more, forward ? 1 : -1);
      ScrollingUtil.ensureIndexIsVisible(list, newIndex, forward ? 1 : -1);
    }
  }

  static void appendWithColoredMatches(SimpleColoredComponent nameComponent,
                                       @NotNull String name,
                                       @NotNull String pattern,
                                       Color fg,
                                       boolean selected) {
    SimpleTextAttributes plain = new SimpleTextAttributes(STYLE_PLAIN, fg);
    SimpleTextAttributes highlighted = new SimpleTextAttributes(null, fg, null, STYLE_SEARCH_MATCH);
    List<TextRange> fragments = ContainerUtil.newArrayList();
    if (selected) {
      int matchStart = StringUtil.indexOfIgnoreCase(name, pattern, 0);
      if (matchStart >= 0) {
        //fragments.add(TextRange.from(matchStart, pattern.length()));
        //fragments.add(TextRange.from(matchStart, pattern.length()));
      }
    }
    SpeedSearchUtil.appendColoredFragments(nameComponent, name, fragments, plain, highlighted);
  }

  @NotNull
  static JLabel createIconLabel(@Nullable Icon icon, boolean disabled) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon == null) return new JLabel(layeredIcon);

    int width = icon.getIconWidth();
    int height = icon.getIconHeight();
    int emptyIconWidth = EMPTY_ICON.getIconWidth();
    int emptyIconHeight = EMPTY_ICON.getIconHeight();
    if (width <= emptyIconWidth && height <= emptyIconHeight) {
      layeredIcon.setIcon(disabled && IconLoader.isGoodSize(icon) ? IconLoader.getDisabledIcon(icon) : icon, 1,
                          (emptyIconWidth - width) / 2,
                          (emptyIconHeight - height) / 2);
    }

    return new JLabel(layeredIcon);
  }

  public static Component createActionCellRendererComponent(@NotNull AnAction value, boolean isSelected, @NotNull String text) {
    boolean showIcon = UISettings.getInstance().getShowIconsInMenus();
    //boolean showIcon = true;
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(2));
    panel.setOpaque(true);
    Color bg = UIUtil.getListBackground(isSelected);
    panel.setBackground(bg);

    SimpleColoredComponent nameComponent = new SimpleColoredComponent();
    nameComponent.setBackground(bg);
    panel.add(nameComponent, BorderLayout.CENTER);

    Color groupFg = isSelected ? UIUtil.getListSelectionForeground() : UIUtil.getLabelDisabledForeground();

    Border eastBorder = JBUI.Borders.emptyRight(2);
    Presentation presentation = value.getTemplatePresentation();
    String description = value.getTemplatePresentation().getDescription();
    description = StringUtil.shortenTextWithEllipsis(description, 50, 0);
    Presentation actionPresentation = value.getTemplatePresentation();
    Color fg = defaultActionForeground(isSelected, actionPresentation);
    boolean disabled = !actionPresentation.isEnabled() || !actionPresentation.isVisible();

    if (disabled) {
      groupFg = UIUtil.getLabelDisabledForeground();
    }

    if (showIcon) {
      Icon icon = presentation.getIcon();
      panel.add(createIconLabel(icon, disabled), BorderLayout.WEST);
    }
    appendWithColoredMatches(nameComponent, StringUtil.notNullize(text), "", fg, isSelected);
    panel.setToolTipText(presentation.getDescription());

    Shortcut[] shortcuts = getActiveKeymapShortcuts(ActionManager.getInstance().getId(value)).getShortcuts();
    String shortcutText = KeymapUtil.getPreferredShortcutText(
      shortcuts);
    if (StringUtil.isNotEmpty(shortcutText)) {
      nameComponent.append(" " + shortcutText,
                           new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER | SimpleTextAttributes.STYLE_BOLD,
                                                    UIUtil.isUnderDarcula() ? groupFg : ColorUtil.shift(groupFg, 1.3)));
    }

    JLabel groupLabel = new JLabel(description);
    groupLabel.setBackground(bg);
    groupLabel.setBorder(eastBorder);
    groupLabel.setForeground(groupFg);
    panel.add(groupLabel, BorderLayout.EAST);
    return panel;
  }

  static Component createRunConfigurationCellRendererComponent(ChooseRunConfigurationPopup.ItemWrapper value, boolean isSelected) {
    boolean showIcon = UISettings.getInstance().getShowIconsInMenus();
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(2));
    panel.setOpaque(true);
    Color bg = UIUtil.getListBackground(isSelected);
    panel.setBackground(bg);

    SimpleColoredComponent nameComponent = new SimpleColoredComponent();
    nameComponent.setBackground(bg);
    panel.add(nameComponent, BorderLayout.CENTER);

    Border eastBorder = JBUI.Borders.emptyRight(2);
    Object runConfigurationSettings = value.getValue();
    String description = value.toString();

    Icon icon = value.getIcon();
    String toolTipText = "";
    if (runConfigurationSettings instanceof RunnerAndConfigurationSettings) {
      description = ((RunnerAndConfigurationSettings)runConfigurationSettings).getType().getConfigurationTypeDescription();
      icon = ((RunnerAndConfigurationSettings)runConfigurationSettings).getType().getIcon();
    }
    description = StringUtil.shortenTextWithEllipsis(description, 50, 0);
    Color fg = defaultActionForeground(isSelected, null);
    Color groupFg = UIUtil.getLabelDisabledForeground();

    if (showIcon) {
      panel.add(createIconLabel(icon, false), BorderLayout.WEST);
    }
    appendWithColoredMatches(nameComponent, StringUtil.notNullize(value.getText()), "", fg, isSelected);
    //todo
    panel.setToolTipText(value.getText());

    JLabel groupLabel = new JLabel(description);
    groupLabel.setBackground(bg);
    groupLabel.setBorder(eastBorder);
    groupLabel.setForeground(groupFg);
    groupLabel.setToolTipText(toolTipText);
    panel.add(groupLabel, BorderLayout.EAST);

    return panel;
  }

  public static Component createUndefinedCommandCellRendererComponent(@NotNull RunAnythingCommandItem value, boolean isSelected) {
    boolean showIcon = UISettings.getInstance().getShowIconsInMenus();
    //boolean showIcon = true;
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(JBUI.Borders.empty(2));
    panel.setOpaque(true);
    Color bg = UIUtil.getListBackground(isSelected);
    panel.setBackground(bg);

    SimpleColoredComponent nameComponent = new SimpleColoredComponent();
    nameComponent.setBackground(bg);
    panel.add(nameComponent, BorderLayout.CENTER);

    Icon icon = value.getIcon();

    Color fg = defaultActionForeground(isSelected, null);

    if (showIcon) {
      panel.add(createIconLabel(icon, false), BorderLayout.WEST);
    }
    String presentationText = StringUtil.shortenTextWithEllipsis(value.getText(), 50, 0);

    appendWithColoredMatches(nameComponent, presentationText, "", fg, isSelected);
    //todo
    panel.setToolTipText(value.getText());

    return panel;
  }

  static void runOrCreateRunConfiguration(@NotNull DataContext dataContext,
                                          @NotNull String pattern,
                                          @NotNull VirtualFile workDirectory) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (pattern.isEmpty()) return;

    if (runMatchedConfiguration(dataContext, Objects.requireNonNull(project), pattern, workDirectory)) return;

    if (runMatchedActivity(dataContext, pattern)) return;

    runCommand(workDirectory, StringUtil.trim(pattern), RunAnythingAction.getExecutor(), dataContext);
  }

  private static boolean runMatchedActivity(@NotNull DataContext dataContext, @NotNull String pattern) {
    RunAnythingActivityProvider activityProvider = RunAnythingActivityProvider.findMatchedProvider(dataContext, StringUtil.trim(pattern));
    return activityProvider != null && activityProvider.runActivity(dataContext, pattern);
  }

  private static boolean runMatchedConfiguration(@NotNull DataContext dataContext,
                                                 @NotNull Project project,
                                                 @NotNull String pattern,
                                                 @NotNull VirtualFile workDirectory) {
    RunAnythingRunConfigurationProvider provider = RunAnythingRunConfigurationProvider.findMatchedProvider(project, pattern, workDirectory);
    if (provider != null) {
      triggerDebuggerStatistics(dataContext);
      runMatchedConfiguration(dataContext, RunAnythingAction.getExecutor(), project, provider.createConfiguration(project, pattern, workDirectory));
      return true;
    }
    return false;
  }


  private static void runMatchedConfiguration(@NotNull DataContext dataContext,
                                              @NotNull Executor executor,
                                              @NotNull Project project,
                                              @NotNull RunnerAndConfigurationSettings settings) {
    RunManagerEx.getInstanceEx(project).setTemporaryConfiguration(settings);
    RunManager.getInstance(project).setSelectedConfiguration(settings);

    triggerDebuggerStatistics(dataContext);
    ExecutionUtil.runConfiguration(settings, executor);
  }

  public static void performRunAnythingAction(@NotNull Object element,
                                              @Nullable final Project project,
                                              @Nullable Component component,
                                              @Nullable AnActionEvent e) {
    ApplicationManager.getApplication()
                      .invokeLater(
                        () -> IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> performAction(element, component, e)));
  }

  private static String getShortcut() {
    Shortcut[] shortcuts = getActiveKeymapShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID).getShortcuts();
    if (shortcuts.length == 0) {
      return "Double" + (SystemInfo.isMac ? FontUtil.thinSpace() + MacKeymapUtil.CONTROL : " Ctrl");
    }
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  static void triggerExecCategoryStatistics(@NotNull Project project, int index) {
    for (int i = index; i >= 0; i--) {
      String title = RunAnythingGroup.getTitle(i);
      if (title != null) {
        RunAnythingUsageCollector.Companion.trigger(project, RunAnythingAction.RUN_ANYTHING + " - execution - " + title);
        break;
      }
    }
  }

  public static void triggerDebuggerStatistics(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project != null);

    if (SHIFT_IS_PRESSED.get()) RunAnythingUsageCollector.Companion.trigger(project, DEBUGGER_FEATURE_USAGE);
  }

  static void triggerMoreStatistics(@NotNull Project project, @NotNull RunAnythingGroup group) {
    RunAnythingUsageCollector.Companion.trigger(project, RunAnythingAction.RUN_ANYTHING + " - more - " + group.getTitle());
  }

  @NotNull
  public static Collection<Pair<String, String>> getOrCreateWrappedCommands(@NotNull Project project) {
    Collection<Pair<String, String>> list = project.getUserData(RUN_ANYTHING_WRAPPED_COMMANDS);
    if (list == null) {
      list = ContainerUtil.newArrayList();
      project.putUserData(RUN_ANYTHING_WRAPPED_COMMANDS, list);
    }
    return list;
  }

  public static void runCommand(@NotNull VirtualFile workDirectory,
                                @NotNull String commandString,
                                @NotNull Executor executor,
                                @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project != null);

    Collection<String> commands = RunAnythingCache.getInstance(project).getState().getCommands();
    commands.remove(commandString);
    commands.add(commandString);

    dataContext = RunAnythingCommandCustomizer.customizeContext(dataContext);

    GeneralCommandLine initialCommandLine =
      new GeneralCommandLine(commandString).withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    GeneralCommandLine commandLine = RunAnythingCommandCustomizer.customizeCommandLine(dataContext, workDirectory, initialCommandLine);
    try {
      ExecutionEnvironmentBuilder.create(project, executor, new RunAnythingRunProfile(commandLine, commandString))
                                 .dataContext(dataContext)
                                 .buildAndExecute();
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      Messages.showInfoMessage(project, e.getMessage(), IdeBundle.message("run.anything.console.error.title"));
    }
  }
}
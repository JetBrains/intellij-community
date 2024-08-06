// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.LightEditActionFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class RecentLocationsAction extends DumbAwareAction implements LightEditCompatible {
  public static final @NonNls String RECENT_LOCATIONS_ACTION_ID = "RecentLocations";
  private static final String LOCATION_SETTINGS_KEY = "recent.locations.popup";
  private static int getDefaultWidth() { return JBUIScale.scale(700); }
  private static int getDefaultHeight() { return JBUIScale.scale(530); }
  private static int getMinimumWidth() { return JBUIScale.scale(600); }
  private static int getMinimumHeight() { return JBUIScale.scale(450); }

  static final class Holder {
    private static final Color SHORTCUT_FOREGROUND_COLOR = UIUtil.getContextHelpForeground();
    public static final String SHORTCUT_HEX_COLOR = String.format("#%02x%02x%02x",
                                                                  SHORTCUT_FOREGROUND_COLOR.getRed(),
                                                                  SHORTCUT_FOREGROUND_COLOR.getGreen(),
                                                                  SHORTCUT_FOREGROUND_COLOR.getBlue());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(RECENT_LOCATIONS_ACTION_ID);
    Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    showPopup(project, false);
  }

  public static void showPopup(@NotNull Project project, boolean showChanged) {
    showPopup(project, showChanged,
              IdeBundle.message("recent.locations.popup.title"),
              IdeBundle.message("recent.locations.changed.locations"),
              IdeBundle.message("recent.locations.popup.empty.text"), null, null);
  }

  public static void showPopup(@NotNull Project project, boolean showChanged,
                               @NotNull @NlsContexts.PopupTitle String title1,
                               @NotNull @NlsContexts.PopupTitle String title2,
                               @NotNull @NlsContexts.StatusText String emptyText,
                               @Nullable Function<? super Boolean, ? extends List<IdeDocumentHistoryImpl.PlaceInfo>> supplier,
                               @Nullable Consumer<? super List<IdeDocumentHistoryImpl.PlaceInfo>> remover) {
    RecentLocationsDataModel model = new RecentLocationsDataModel(project,
                                                                  supplier == null ? null : supplier::apply,
                                                                  remover == null ? null : infos -> {
                                                                    remover.accept(infos);
                                                                    return Unit.INSTANCE;
                                                                  });
    JBList<RecentLocationItem> list = new JBList<>(JBList.createDefaultListModel(model.getPlaces(showChanged)));
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list,
                                                                      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    ShortcutSet showChangedOnlyShortcutSet = KeymapUtil.getActiveKeymapShortcuts(RECENT_LOCATIONS_ACTION_ID);
    JBCheckBox checkBox = createCheckbox(showChangedOnlyShortcutSet, showChanged);
    RecentLocationsRenderer renderer = new RecentLocationsRenderer(project, model, checkBox);

    list.setCellRenderer(renderer);
    list.setEmptyText(emptyText);
    list.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    if (list.getModel().getSize() > 0) list.setSelectedIndex(0);
    ScrollingUtil.installActions(list);

    SimpleColoredComponent title = new SimpleColoredComponent();
    title.setOpaque(false);
    Runnable titleUpdater = () -> {
      title.clear();
      title.append(checkBox.isSelected() ? title2 : title1, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      title.append("  (" + list.getModel().getSize() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    };
    titleUpdater.run();

    JComponent listWithFilter = ListWithFilter.wrap(list, scrollPane, renderer::getSpeedSearchText, true);
    //noinspection unchecked
    ((ListWithFilter<Object>)listWithFilter).setAutoPackHeight(false);
    listWithFilter.setBorder(BorderFactory.createEmptyBorder());

    JPanel topPanel = createHeaderPanel(title, checkBox);
    JPanel mainPanel = createMainPanel(listWithFilter, topPanel);

    Color borderColor = SystemInfo.isMac && LafManager.getInstance().getCurrentUIThemeLookAndFeel().isDark()
                        ? topPanel.getBackground()
                        : null;

    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(mainPanel, list)
      .setProject(project)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setResizable(true)
      .setMovable(true)
      .setBorderColor(borderColor)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setMinSize(new Dimension(getMinimumWidth(), getMinimumHeight()))
      .setLocateWithinScreenBounds(false)
      .createPopup();
    Disposer.register(popup, renderer);

    LightEditActionFactory.create(event -> {
      checkBox.setSelected(!checkBox.isSelected());
      updateItems(model, list, checkBox, popup);
      titleUpdater.run();
    }).registerCustomShortcutSet(showChangedOnlyShortcutSet, list, popup);

    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateItems(model, list, checkBox, popup);
        titleUpdater.run();
      }
    });

    if (DimensionService.getInstance().getSize(LOCATION_SETTINGS_KEY, project) == null) {
      popup.setSize(new Dimension(getDefaultWidth(), getDefaultHeight()));
    }

    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        int clickCount = event.getClickCount();
        if (clickCount > 1 && clickCount % 2 == 0) {
          event.consume();
          final int i = list.locationToIndex(event.getPoint());
          if (i != -1) {
            list.setSelectedIndex(i);
            navigateToSelected(project, list, popup);
          }
        }
      }
    });

    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        int clickCount = event.getClickCount();
        if (clickCount > 1 && clickCount % 2 == 0) {
          event.consume();
          navigateToSelected(project, list, popup);
        }
      }
    });

    LightEditActionFactory.create(e -> navigateToSelected(project, list, popup))
      .registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), list, popup);

    LightEditActionFactory.create(e -> {
        removeItems(project, list, model, checkBox.isSelected());
        titleUpdater.run();
      })
      .registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), list, popup);

    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);

    list.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        if (!(e.getOppositeComponent() instanceof JCheckBox)) {
          popup.cancel();
        }
      }
    });

    showPopup(project, popup);
  }

  private static void updateItems(@NotNull RecentLocationsDataModel data,
                                  @NotNull JBList<RecentLocationItem> list,
                                  @NotNull JBCheckBox checkBox,
                                  @NotNull JBPopup popup) {
    updateModel(list, data, checkBox.isSelected());
    FocusManagerImpl.getInstance().requestFocus(list, false);
    popup.pack(false, false);
  }

  private static @NotNull JBCheckBox createCheckbox(@NotNull ShortcutSet checkboxShortcutSet, boolean showChanged) {
    //noinspection HardCodedStringLiteral
    String text = "<html>"
                  + IdeBundle.message("recent.locations.title.text")
                  + " <font color=\"" + Holder.SHORTCUT_HEX_COLOR + "\">"
                  + KeymapUtil.getShortcutsText(checkboxShortcutSet.getShortcuts()) + "</font>"
                  + "</html>";
    JBCheckBox checkBox = new JBCheckBox(text);
    checkBox.setBorder(JBUI.Borders.empty());
    checkBox.setOpaque(false);
    checkBox.setSelected(showChanged);

    return checkBox;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void showPopup(@NotNull Project project, @NotNull JBPopup popup) {
    Point savedLocation = DimensionService.getInstance().getLocation(LOCATION_SETTINGS_KEY, project);
    Window recentFocusedWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (savedLocation != null && recentFocusedWindow != null) {
      popup.showInScreenCoordinates(recentFocusedWindow, savedLocation);
    }
    else {
      popup.showCenteredInCurrentWindow(project);
    }
  }

  private static void updateModel(@NotNull JBList<RecentLocationItem> list,
                                  @NotNull RecentLocationsDataModel data,
                                  boolean changed) {
    NameFilteringListModel<RecentLocationItem> model = (NameFilteringListModel<RecentLocationItem>)list.getModel();
    DefaultListModel<RecentLocationItem> originalModel = (DefaultListModel<RecentLocationItem>)model.getOriginalModel();

    originalModel.removeAllElements();
    originalModel.addAll(data.getPlaces(changed));
    if (list.getModel().getSize() > 0) list.setSelectedIndex(0);

    SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(list);
    if (speedSearch instanceof SpeedSearch) ((SpeedSearch)speedSearch).reset();
  }

  private static @NotNull JPanel createMainPanel(@NotNull JComponent listPanel, @NotNull JPanel topPanel) {
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(topPanel, BorderLayout.NORTH);
    mainPanel.add(listPanel, BorderLayout.CENTER);
    return mainPanel;
  }

  private static @NotNull JPanel createHeaderPanel(@NotNull JComponent title, @NotNull JComponent checkbox) {
    JPanel topPanel = new CaptionPanel();
    topPanel.add(title, BorderLayout.WEST);
    topPanel.add(checkbox, BorderLayout.EAST);

    Dimension size = topPanel.getPreferredSize();
    size.height = JBUIScale.scale(29);
    topPanel.setPreferredSize(size);
    topPanel.setBorder(JBUI.Borders.empty(5, 8));

    new WindowMoveListener(topPanel).installTo(topPanel);

    return topPanel;
  }

  private static void removeItems(@NotNull Project project,
                                  @NotNull JBList<RecentLocationItem> list,
                                  @NotNull RecentLocationsDataModel data,
                                  boolean showChanged) {
    List<RecentLocationItem> selectedValue = list.getSelectedValuesList();
    if (selectedValue.isEmpty()) {
      return;
    }
    int index = list.getSelectedIndex();
    data.removeItems(project, showChanged, selectedValue);
    updateModel(list, data, showChanged);
    if (list.getModel().getSize() > 0) {
      ScrollingUtil.selectItem(list, index < list.getModel().getSize() ? index : index - 1);
    }
  }

  private static void navigateToSelected(@NotNull Project project,
                                         @NotNull JBList<RecentLocationItem> list,
                                         @NotNull JBPopup popup) {
    ContainerUtil.reverse(list.getSelectedValuesList()).forEach(
      item -> IdeDocumentHistory.getInstance(project).gotoPlaceInfo(item.info, true));

    popup.closeOk(null);
  }

  static String getEmptyFileText() {
    return IdeBundle.message("recent.locations.popup.empty.file.text");
  }
}

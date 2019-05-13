// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.highlighter.LightHighlighterClient;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.actionSystem.ex.CustomComponentAction.COMPONENT_KEY;
import static com.intellij.ui.speedSearch.SpeedSearchSupply.ENTERED_PREFIX_PROPERTY_NAME;

public class RecentLocationsAction extends AnAction {
  private static final String LOCATION_SETTINGS_KEY = "recent.locations.popup";
  private static final String SHOW_RECENT_CHANGED_LOCATIONS = "SHOW_RECENT_CHANGED_LOCATIONS";
  private static final int DEFAULT_POPUP_WIDTH = JBUI.scale(700);
  private static final Color SHORTCUT_FOREGROUND_COLOR = UIUtil.getContextHelpForeground();
  private static final String SHORTCUT_HEX_COLOR = String.format("#%02x%02x%02x",
                                                                 SHORTCUT_FOREGROUND_COLOR.getRed(),
                                                                 SHORTCUT_FOREGROUND_COLOR.getGreen(),
                                                                 SHORTCUT_FOREGROUND_COLOR.getBlue());

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.locations");
    Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    boolean showChanged = showChanged(project);

    final Ref<List<RecentLocationItem>> changedPlaces = Ref.create();
    final Ref<List<RecentLocationItem>> navigationPlaces = Ref.create();
    Collection<Editor> editorsToRelease = ContainerUtil.newArrayList();

    JBList<RecentLocationItem> list = new JBList<>(JBList.createDefaultListModel(
      cacheAndGetItems(project, showChanged, changedPlaces, navigationPlaces, editorsToRelease)));
    ListWithFilter<RecentLocationItem> listWithFilter = createListWithFilter(project, list);
    listWithFilter.setBorder(BorderFactory.createEmptyBorder());

    final SpeedSearch speedSearch = listWithFilter.getSpeedSearch();
    speedSearch.addChangeListener(
      evt -> {
        if (evt.getPropertyName().equals(ENTERED_PREFIX_PROPERTY_NAME)) {
          if (StringUtil.isEmpty(speedSearch.getFilter())) {
            editorsToRelease.forEach(editor -> clearSelectionInEditor(editor));
          }
        }
      });

    RecentLocationsRenderer renderer = new RecentLocationsRenderer(project, speedSearch);
    list.setCellRenderer(renderer);
    list.setEmptyText(IdeBundle.message("recent.locations.popup.empty.text"));
    list.setBackground(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    ScrollingUtil.installActions(list);
    ScrollingUtil.ensureSelectionExists(list);

    JLabel title = createTitle(showChanged);
    JPanel topPanel = createHeaderPanel(title, createCheckbox(project, listWithFilter, e, showChanged));
    JPanel mainPanel = createMainPanel(listWithFilter, topPanel);

    Ref<Boolean> navigationRef = Ref.create(false);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(mainPanel, list)
      .setProject(project)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelCallback(() -> {
        if (speedSearch.isHoldingFilter() && !navigationRef.get()) {
          speedSearch.reset();
          return false;
        }
        return true;
      })
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setMinSize(new Dimension(DEFAULT_POPUP_WIDTH, JBUI.scale(100)))
      .createPopup();

    project.getMessageBus().connect(popup).subscribe(ShowRecentChangedLocationListener.TOPIC, new ShowRecentChangedLocationListener() {
      @Override
      public void showChangedLocation(boolean state) {
        updateModel(project, listWithFilter, editorsToRelease, state, changedPlaces, navigationPlaces);

        updateTitleText(title, state);

        popup.pack(false, true);
      }
    });

    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        editorsToRelease.forEach(editor -> EditorFactory.getInstance().releaseEditor(editor));
      }
    });

    initSearchActions(project, list, popup, navigationRef);

    popup.setSize(new Dimension(DEFAULT_POPUP_WIDTH, JBUI.scale(mainPanel.getPreferredSize().height)));

    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);

    list.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        popup.cancel();
      }
    });

    showPopup(project, popup);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }

  @NotNull
  static String getBreadcrumbs(@NotNull Project project, @NotNull PlaceInfo info) {
    Collection<Iterable<? extends Crumb>> breadcrumbs =
      RecentLocationManager.getInstance(project).getBreadcrumbs(info, showChanged(project));
    if (breadcrumbs.isEmpty()) {
      return info.getFile().getName();
    }

    Iterable<? extends Crumb> crumbs = ContainerUtil.getFirstItem(breadcrumbs);

    if (crumbs == null) {
      return info.getFile().getName();
    }

    String breadcrumbsText = StringUtil.join(ContainerUtil.map(crumbs, crumb -> crumb.getText()), " > ");

    return StringUtil.shortenTextWithEllipsis(breadcrumbsText, 50, 0);
  }

  static void clearSelectionInEditor(@NotNull Editor editor) {
    editor.getSelectionModel().removeSelection(true);
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

  @NotNull
  private static List<RecentLocationItem> cacheAndGetItems(@NotNull Project project,
                                                           boolean showChanged,
                                                           @NotNull Ref<List<RecentLocationItem>> changedPlaces,
                                                           @NotNull Ref<List<RecentLocationItem>> navigationPlaces,
                                                           @NotNull Collection<Editor> editorsToRelease) {
    List<RecentLocationItem> items = getPlaceLinePairs(project, showChanged);
    Ref<List<RecentLocationItem>> places = showChanged ? changedPlaces : navigationPlaces;
    if (places.get() == null) {
      places.set(items);
    }

    editorsToRelease.addAll(ContainerUtil.map(items, item -> item.getEditor()));

    return items;
  }

  private static void updateModel(@NotNull Project project,
                                  @NotNull ListWithFilter<RecentLocationItem> listWithFilter,
                                  @NotNull Collection<Editor> editorsToRelease,
                                  boolean changed,
                                  @NotNull Ref<List<RecentLocationItem>> changedPlaces,
                                  @NotNull Ref<List<RecentLocationItem>> navigationPlaces) {
    NameFilteringListModel<RecentLocationItem> model = (NameFilteringListModel<RecentLocationItem>)listWithFilter.getList().getModel();
    DefaultListModel<RecentLocationItem> originalModel = (DefaultListModel<RecentLocationItem>)model.getOriginalModel();

    List<RecentLocationItem> items = cacheAndGetItems(project, changed, changedPlaces, navigationPlaces, editorsToRelease);

    originalModel.removeAllElements();
    items.forEach(item -> originalModel.addElement(item));

    listWithFilter.getSpeedSearch().reset();
  }

  @NotNull
  private static JPanel createMainPanel(@NotNull ListWithFilter listWithFilter, @NotNull JPanel topPanel) {
    JPanel mainPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    mainPanel.add(topPanel);
    mainPanel.add(listWithFilter);
    mainPanel.setBorder(BorderFactory.createEmptyBorder());
    return mainPanel;
  }

  @NotNull
  private static JPanel createHeaderPanel(@NotNull JLabel title, @NotNull JComponent checkbox) {
    JPanel topPanel = new NonOpaquePanel(new BorderLayout());
    topPanel.add(title, BorderLayout.WEST);
    topPanel.add(checkbox, BorderLayout.EAST);
    topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    topPanel.setBackground(JBUI.CurrentTheme.Popup.headerBackground(true));

    WindowMoveListener moveListener = new WindowMoveListener(topPanel);
    topPanel.addMouseListener(moveListener);
    topPanel.addMouseMotionListener(moveListener);

    return topPanel;
  }

  @NotNull
  private static JComponent createCheckbox(@NotNull Project project,
                                           @NotNull ListWithFilter listWithFilter,
                                           @NotNull AnActionEvent e,
                                           boolean changed) {
    CheckboxAction action = new RecentLocationsCheckboxAction(project);
    CustomShortcutSet set = CustomShortcutSet.fromString(SystemInfo.isMac ? "meta L" : "control L");
    action.registerCustomShortcutSet(set, listWithFilter);
    action.getTemplatePresentation().setText("<html>" +
                                             IdeBundle.message("recent.locations.title.text") +
                                             " <font color=\"" + SHORTCUT_HEX_COLOR + "\">" +
                                             KeymapUtil.getShortcutsText(set.getShortcuts()) + "</font>" +
                                             "</html>");

    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, e.getDataContext());
    JComponent checkbox = action.createCustomComponent(action.getTemplatePresentation());
    action.getTemplatePresentation().putClientProperty(COMPONENT_KEY, checkbox);
    action.setSelected(event, changed);
    checkbox.setBorder(BorderFactory.createEmptyBorder());

    return checkbox;
  }

  @NotNull
  private static JLabel createTitle(boolean showChanged) {
    JBLabel title = new JBLabel();
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    updateTitleText(title, showChanged);
    return title;
  }

  private static void updateTitleText(@NotNull JLabel title, boolean showChanged) {
    title.setText(showChanged
                  ? IdeBundle.message("recent.locations.changed.locations")
                  : IdeBundle.message("recent.locations.popup.title"));
  }

  @NotNull
  private static ListWithFilter<RecentLocationItem> createListWithFilter(@NotNull Project project,
                                                                         @NotNull JBList<RecentLocationItem> list) {
    final JScrollPane pane = ScrollPaneFactory
      .createScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    pane.setBorder(BorderFactory.createEmptyBorder());

    return (ListWithFilter<RecentLocationItem>)ListWithFilter.wrap(list, pane, getNamer(project), true);
  }

  @NotNull
  private static Function<RecentLocationItem, String> getNamer(@NotNull Project project) {
    return value -> {
      String breadcrumb = getBreadcrumbs(project, value.getInfo());
      EditorEx editor = value.getEditor();

      return breadcrumb + " " + value.getInfo().getFile().getName() + " " + editor.getDocument().getText();
    };
  }

  private static List<PlaceInfo> getPlaces(@NotNull Project project, boolean showChanged) {
    List<PlaceInfo> infos = showChanged
                            ? ContainerUtil.reverse(IdeDocumentHistory.getInstance(project).getChangePlaces())
                            : ContainerUtil.reverse(IdeDocumentHistory.getInstance(project).getBackPlaces());

    ArrayList<PlaceInfo> infosCopy = ContainerUtil.newArrayList();

    for (PlaceInfo info : infos) {
      if (infosCopy.stream().noneMatch(info1 -> IdeDocumentHistoryImpl.isSame(info, info1))) {
        infosCopy.add(info);
      }
    }

    return infosCopy;
  }

  @NotNull
  private static List<RecentLocationItem> getPlaceLinePairs(@NotNull Project project, boolean changed) {
    List<PlaceInfo> places = getPlaces(project, changed);
    if (places.isEmpty()) {
      return ContainerUtil.emptyList();
    }

    List<RecentLocationItem> caretsList = ContainerUtil.newArrayList();
    for (PlaceInfo placeInfo : places) {
      EditorEx editor = createEditor(project, placeInfo);
      if (editor != null) {
        caretsList.add(new RecentLocationItem(editor, placeInfo));
      }
    }

    return caretsList;
  }

  @Nullable
  private static EditorEx createEditor(@NotNull Project project, @NotNull PlaceInfo placeInfo) {
    RangeMarker positionOffset = RecentLocationManager.getInstance(project).getPositionOffset(placeInfo, showChanged(project));
    if (positionOffset == null || !positionOffset.isValid()) {
      return null;
    }
    assert positionOffset.getStartOffset() == positionOffset.getEndOffset();

    Document fileDocument = positionOffset.getDocument();
    int lineNumber = fileDocument.getLineNumber(positionOffset.getStartOffset());
    TextRange actualTextRange = getTrimmedRange(fileDocument, lineNumber);
    if (actualTextRange.isEmpty()) {
      return null;
    }

    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(fileDocument.getText(actualTextRange));
    EditorEx editor = (EditorEx)editorFactory.createEditor(editorDocument, project);

    EditorGutterComponentEx gutterComponentEx = editor.getGutterComponentEx();
    int linesShift = fileDocument.getLineNumber(actualTextRange.getStartOffset());
    gutterComponentEx.setLineNumberConvertor(index -> index + linesShift);
    gutterComponentEx.setPaintBackground(false);
    JScrollPane scrollPane = editor.getScrollPane();
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

    fillEditorSettings(editor.getSettings());
    setHighlighting(project, editor, fileDocument, placeInfo, actualTextRange);

    return editor;
  }

  @NotNull
  private static TextRange getTrimmedRange(@NotNull Document document, int lineNumber) {
    TextRange range = getLinesRange(document, lineNumber);
    String text = document.getText(TextRange.create(range.getStartOffset(), range.getEndOffset()));

    int newLinesBefore = StringUtil.countNewLines(Objects.requireNonNull(StringUtil.substringBefore(text, StringUtil.trimLeading(text))));
    int newLinesAfter = StringUtil.countNewLines(Objects.requireNonNull(StringUtil.substringAfter(text, StringUtil.trimTrailing(text))));

    int firstLine = document.getLineNumber(range.getStartOffset());
    int firstLineAdjusted = firstLine + newLinesBefore;

    int lastLine = document.getLineNumber(range.getEndOffset());
    int lastLineAdjusted = lastLine - newLinesAfter;

    int startOffset = document.getLineStartOffset(firstLineAdjusted);
    int endOffset = document.getLineEndOffset(lastLineAdjusted);

    return TextRange.create(startOffset, endOffset);
  }

  private static void initSearchActions(@NotNull Project project,
                                        @NotNull JBList<RecentLocationItem> list,
                                        @NotNull JBPopup popup,
                                        @NotNull Ref<Boolean> navigationRef) {
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        int clickCount = event.getClickCount();
        if (clickCount > 1 && clickCount % 2 == 0) {
          event.consume();
          navigateToSelected(project, list, popup, navigationRef);
        }
      }
    });

    DumbAwareAction.create(e -> navigateToSelected(project, list, popup, navigationRef))
      .registerCustomShortcutSet(CustomShortcutSet.fromString("ENTER"), list, null);
  }

  private static void navigateToSelected(@NotNull Project project,
                                         @NotNull JBList<RecentLocationItem> list,
                                         @NotNull JBPopup popup,
                                         @NotNull Ref<Boolean> navigationRef) {
    IdeDocumentHistory.getInstance(project).gotoPlaceInfo(list.getSelectedValue().getInfo());

    navigationRef.set(true);
    popup.closeOk(null);
  }

  private static boolean showChanged(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(SHOW_RECENT_CHANGED_LOCATIONS, false);
  }

  @NotNull
  private static TextRange getLinesRange(@NotNull Document document, int line) {
    int lineCount = document.getLineCount();
    if (lineCount == 0) {
      return TextRange.EMPTY_RANGE;
    }

    int beforeAfterLinesCount = Registry.intValue("recent.locations.lines.before.and.after", 2);

    int before = Math.min(beforeAfterLinesCount, line);
    int after = Math.min(beforeAfterLinesCount, lineCount - line);

    int linesBefore = before + beforeAfterLinesCount - after;
    int linesAfter = after + beforeAfterLinesCount - before;

    int startLine = Math.max(line - linesBefore, 0);
    int endLine = Math.min(line + linesAfter, lineCount - 1);

    int startOffset = document.getLineStartOffset(startLine);
    int endOffset = document.getLineEndOffset(endLine);

    return startOffset <= endOffset
           ? TextRange.create(startOffset, endOffset)
           : TextRange.create(DocumentUtil.getLineTextRange(document, line));
  }

  private static void setHighlighting(@NotNull Project project,
                                      @NotNull EditorEx editor,
                                      @NotNull Document document,
                                      @NotNull PlaceInfo placeInfo,
                                      @NotNull TextRange textRange) {
    EditorColorsScheme colorsScheme = setupColorScheme(project, editor, placeInfo);

    applySyntaxHighlighting(project, editor, document, colorsScheme, textRange, placeInfo);
    applyHighlightingPasses(project, editor, document, colorsScheme, textRange);
  }

  @NotNull
  private static EditorColorsScheme setupColorScheme(@NotNull Project project, @NotNull EditorEx editor, @NotNull PlaceInfo placeInfo) {
    EditorColorsScheme colorsScheme = RecentLocationManager.getInstance(project).getColorScheme(placeInfo, showChanged(project));
    if (colorsScheme == null) {
      colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    }
    editor.setColorsScheme(colorsScheme);
    return colorsScheme;
  }

  private static void applySyntaxHighlighting(@NotNull Project project,
                                              @NotNull EditorEx editor,
                                              @NotNull Document document,
                                              @NotNull EditorColorsScheme colorsScheme,
                                              @NotNull TextRange textRange,
                                              @NotNull PlaceInfo placeInfo) {
    EditorHighlighter editorHighlighter =
      EditorHighlighterFactory.getInstance().createEditorHighlighter(placeInfo.getFile(), colorsScheme, project);
    editorHighlighter.setEditor(new LightHighlighterClient(document, project));
    editorHighlighter.setText(document.getText(TextRange.create(0, textRange.getEndOffset())));
    int startOffset = textRange.getStartOffset();
    HighlighterIterator iterator = editorHighlighter.createIterator(startOffset);

    while (!iterator.atEnd() && iterator.getEnd() <= textRange.getEndOffset()) {
      if (iterator.getStart() >= startOffset) {
        editor.getMarkupModel().addRangeHighlighter(iterator.getStart() - startOffset,
                                                    iterator.getEnd() - startOffset,
                                                    HighlighterLayer.SYNTAX - 1,
                                                    iterator.getTextAttributes(),
                                                    HighlighterTargetArea.EXACT_RANGE);
      }

      iterator.advance();
    }
  }

  private static void applyHighlightingPasses(@NotNull Project project,
                                              @NotNull EditorEx editor,
                                              @NotNull Document document,
                                              @NotNull EditorColorsScheme colorsScheme,
                                              @NotNull TextRange rangeMarker) {
    int startOffset = rangeMarker.getStartOffset();
    int endOffset = rangeMarker.getEndOffset();
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, startOffset, endOffset, info -> {
      if (info.startOffset < startOffset || info.endOffset > endOffset) {
        return true;
      }

      editor.getMarkupModel().addRangeHighlighter(
        info.getActualStartOffset() - rangeMarker.getStartOffset(), info.getActualEndOffset() - rangeMarker.getStartOffset(),
        HighlighterLayer.SYNTAX,
        colorsScheme.getAttributes(info.forcedTextAttributesKey),
        HighlighterTargetArea.EXACT_RANGE);

      return true;
    });
  }

  private static void fillEditorSettings(@NotNull EditorSettings settings) {
    settings.setLineNumbersShown(true);
    settings.setCaretRowShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
    settings.setRightMarginShown(false);
  }

  private static class RecentLocationsCheckboxAction extends CheckboxAction {
    @NotNull private final Project myProject;

    private RecentLocationsCheckboxAction(@NotNull Project project) {
      myProject = project;

      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.Actions.Diff);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return showChanged(myProject);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(SHOW_RECENT_CHANGED_LOCATIONS, state);

      update(AnActionEvent.createFromAnAction(this, e.getInputEvent(), ActionPlaces.UNKNOWN, e.getDataContext()));

      myProject.getMessageBus().syncPublisher(ShowRecentChangedLocationListener.TOPIC).showChangedLocation(state);
    }
  }

  static class RecentLocationItem {
    @NotNull private final PlaceInfo myInfo;
    @NotNull private final EditorEx myEditor;

    RecentLocationItem(@NotNull EditorEx editor, @NotNull PlaceInfo info) {
      myInfo = info;
      myEditor = editor;
    }

    @NotNull
    PlaceInfo getInfo() {
      return myInfo;
    }

    @NotNull
    EditorEx getEditor() {
      return myEditor;
    }
  }

  private interface ShowRecentChangedLocationListener {
    Topic<ShowRecentChangedLocationListener> TOPIC = Topic.create("ShowRecentChangedLocation", ShowRecentChangedLocationListener.class);

    void showChangedLocation(boolean state);
  }
}

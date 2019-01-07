// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.PlaceInfo;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.breadcrumbs.Crumb;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.NameFilteringListModel;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.actionSystem.ex.CustomComponentAction.COMPONENT_KEY;
import static com.intellij.ui.speedSearch.SpeedSearchSupply.ENTERED_PREFIX_PROPERTY_NAME;

public class RecentLocationsAction extends AnAction {
  private static final JBColor BACKGROUND_COLOR = JBColor.namedColor("Table.lightSelectionBackground", new JBColor(0xE9EEF5, 0x464A4D));
  private static final String LOCATION_SETTINGS_KEY = "recent.locations.popup";
  private static final String SHOW_RECENT_CHANGED_LOCATIONS = "SHOW_RECENT_CHANGED_LOCATIONS";
  private static final String NOT_SIGNIFICANT_LINE_REGEXP = "[^A-Za-z0-9]*";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.locations");
    Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    boolean changed = showChanged(project);

    final Ref<List<RecentLocationItem>> changedPlaces = Ref.create();
    final Ref<List<RecentLocationItem>> navigationPlaces = Ref.create();
    Collection<Editor> editorsToRelease = ContainerUtil.newArrayList();

    JBList<RecentLocationItem> list = new JBList<>(JBList.createDefaultListModel(
      cacheAndGetItems(project, changed, changedPlaces, navigationPlaces, editorsToRelease)));
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

    ShowRecentLocationsRenderer renderer = new ShowRecentLocationsRenderer(project, speedSearch);
    list.setCellRenderer(renderer);
    list.setEmptyText(IdeBundle.message("title.popup.recent.locations.empty"));
    ScrollingUtil.installActions(list);
    ScrollingUtil.ensureSelectionExists(list);

    JLabel title = createTitle(changed);
    JPanel topPanel = createTopPanel(createCheckbox(project, listWithFilter, e, changed), title);
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
      .setShowBorder(false)
      .setDimensionServiceKey(project, LOCATION_SETTINGS_KEY, true)
      .setMinSize(new Dimension(JBUI.scale(500), JBUI.scale(100)))
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

    popup.setSize(new Dimension(JBUI.scale(500), JBUI.scale(mainPanel.getPreferredSize().height)));

    showPopup(project, e, popup);
  }

  private static void showPopup(@NotNull Project project, @NotNull AnActionEvent e, @NotNull JBPopup popup) {
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
  public static List<RecentLocationItem> cacheAndGetItems(@NotNull Project project,
                                                          boolean changed,
                                                          @NotNull Ref<List<RecentLocationItem>> changedPlaces,
                                                          @NotNull Ref<List<RecentLocationItem>> navigationPlaces,
                                                          @NotNull Collection<Editor> editorsToRelease) {
    List<RecentLocationItem> items = getPlaceLinePairs(project, changed);
    Ref<List<RecentLocationItem>> places = changed ? changedPlaces : navigationPlaces;
    if (places.get() == null) {
      places.set(items);
    }

    editorsToRelease.addAll(ContainerUtil.map(items, item -> item.getEditor()));

    return items;
  }

  @NotNull
  public static Collection<Editor> getEditors(List<RecentLocationItem> placeItems) {
    return ContainerUtil.map(placeItems, item -> item.getEditor());
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
    JLabel line = createLine();
    mainPanel.add(line);
    mainPanel.add(listWithFilter);
    mainPanel.setBorder(BorderFactory.createEmptyBorder());
    return mainPanel;
  }

  @NotNull
  private static JLabel createLine() {
    JLabel line = new JLabel();
    Border bottom = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorTextColor(), 0, 0, 1, 0);
    line.setBorder(bottom);
    return line;
  }

  @NotNull
  private static JPanel createTopPanel(@NotNull JComponent checkbox, @NotNull JLabel title) {
    JPanel topPanel = new JPanel(new BorderLayout());
    topPanel.add(title, BorderLayout.WEST);
    topPanel.add(checkbox, BorderLayout.EAST);
    topPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

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
    action.getTemplatePresentation()
      .setText(IdeBundle.message("title.popup.recent.locations.text", KeymapUtil.getShortcutsText(set.getShortcuts())));

    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, e.getDataContext());
    JComponent checkbox = action.createCustomComponent(action.getTemplatePresentation());
    action.getTemplatePresentation().putClientProperty(COMPONENT_KEY, checkbox);
    action.setSelected(event, changed);
    checkbox.setBorder(BorderFactory.createEmptyBorder());

    return checkbox;
  }

  @NotNull
  private static JLabel createTitle(boolean changed) {
    JLabel title = new JLabel();
    title.setBorder(BorderFactory.createEmptyBorder());
    updateTitleText(title, changed);
    return title;
  }

  private static void updateTitleText(@NotNull JLabel title, boolean state) {
    title.setText(state ? IdeBundle.message("title.popup.recent.changed.locations") : IdeBundle.message("title.popup.recent.locations"));
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

  @NotNull
  private static String getBreadcrumbs(@NotNull Project project, @NotNull PlaceInfo info) {
    Collection<Iterable<? extends Crumb>> breadcrumbs = RecentLocationManager.getInstance(project).getBreadcrumbs(info);
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
      if (editor == null) {
        continue;
      }

      if (caretsList.size() > RecentLocationManager.LIST_SIZE - 1) {
        break;
      }

      caretsList.add(new RecentLocationItem(placeInfo, editor));
    }

    return caretsList;
  }

  @Nullable
  private static EditorEx createEditor(@NotNull Project project, @NotNull PlaceInfo placeInfo) {
    RangeMarker rangeMarker = RecentLocationManager.getInstance(project).getRangeMarker(placeInfo);
    if (rangeMarker == null) {
      return null;
    }
    Document document = rangeMarker.getDocument();
    int offset = rangeMarker.getStartOffset();

    String text = document.getText(TextRange.create(offset, rangeMarker.getEndOffset()));
    List<String> lines = Arrays.asList(StringUtil.splitByLinesKeepSeparators(text));
    StringBuilder stringBuilder = new StringBuilder();
    int skipBeforeLines = getLinesToSkip(lines);
    int skipAfterLines = getLinesToSkip(ContainerUtil.reverse(lines));

    for (int i = 0; i < lines.size(); i++) {
      if (i >= skipBeforeLines && i < lines.size() - skipAfterLines) {
        stringBuilder.append(lines.get(i));
      }
    }

    text = stringBuilder.toString();

    int newLinesBefore = StringUtil.countNewLines(text) - StringUtil.countNewLines(StringUtil.trimLeading(text, '\n'));
    text = StringUtil.trimLeading(text, '\n');
    text = StringUtil.trimTrailing(text, '\n');

    if (StringUtil.isEmpty(text)) {
      return null;
    }

    EditorEx smallEditor = createEditor(project, text, document.getLineNumber(offset) + skipBeforeLines + newLinesBefore, placeInfo);
    smallEditor.getGutterComponentEx().setPaintBackground(false);

    return smallEditor;
  }

  private static int getLinesToSkip(@NotNull List<String> lines) {
    int skipLines = 0;
    boolean matched = false;
    for (String line : lines) {
      if (matched) {
        break;
      }

      if (line.matches(NOT_SIGNIFICANT_LINE_REGEXP)) {
        skipLines++;
      }
      else {
        matched = true;
      }
    }
    return skipLines;
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

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null);
  }

  public static boolean showChanged(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(SHOW_RECENT_CHANGED_LOCATIONS, false);
  }

  private static class RecentLocationItem {
    private final PlaceInfo myInfo;
    private final EditorEx myEditor;

    RecentLocationItem(PlaceInfo info, EditorEx editor) {
      myInfo = info;
      myEditor = editor;
    }

    PlaceInfo getInfo() {
      return myInfo;
    }

    EditorEx getEditor() {
      return myEditor;
    }
  }

  public static void clearSelectionInEditor(@NotNull Editor editor) {
    editor.getSelectionModel().removeSelection(true);
  }

  public static void selectSearchResultsInEditor(@NotNull Editor editor,
                                                 @NotNull Iterator<TextRange> resultIterator,
                                                 int caretShiftFromSelectionStart) {
    if (!editor.getCaretModel().supportsMultipleCarets()) {
      return;
    }
    ArrayList<CaretState> caretStates = new ArrayList<>();
    while (resultIterator.hasNext()) {
      TextRange findResult = resultIterator.next();

      int caretOffset = getCaretPosition(findResult, caretShiftFromSelectionStart);

      int selectionStartOffset = findResult.getStartOffset();
      int selectionEndOffset = findResult.getEndOffset();
      EditorActionUtil.makePositionVisible(editor, caretOffset);
      EditorActionUtil.makePositionVisible(editor, selectionStartOffset);
      EditorActionUtil.makePositionVisible(editor, selectionEndOffset);
      caretStates.add(new CaretState(editor.offsetToLogicalPosition(caretOffset),
                                     editor.offsetToLogicalPosition(selectionStartOffset),
                                     editor.offsetToLogicalPosition(selectionEndOffset)));
    }
    if (caretStates.isEmpty()) {
      return;
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  private static int getCaretPosition(TextRange findResult, int caretShiftFromSelectionStart) {
    return caretShiftFromSelectionStart < 0
           ? findResult.getEndOffset() : Math.min(findResult.getStartOffset() + caretShiftFromSelectionStart, findResult.getEndOffset());
  }

  private static class ShowRecentLocationsRenderer extends ColoredListCellRenderer<RecentLocationItem> {
    private final Project myProject;
    private final SpeedSearch mySpeedSearch;

    private ShowRecentLocationsRenderer(@NotNull Project project, @NotNull SpeedSearch speedSearch) {
      myProject = project;
      mySpeedSearch = speedSearch;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends RecentLocationItem> list,
                                                  RecentLocationItem value,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      PlaceInfo placeInfo = value.getInfo();
      EditorEx smallEditor = value.getEditor();
      String text = smallEditor.getDocument().getText();

      Iterable<TextRange> ranges = mySpeedSearch.matchingFragments(text);

      if (ranges != null) {
        selectSearchResultsInEditor(smallEditor, ranges.iterator(), -1);
      }
      else {
        clearSelectionInEditor(smallEditor);
      }

      String breadcrumb = getBreadcrumbs(myProject, placeInfo);
      SimpleColoredComponent breadcrumbTextComponent = createBreadcrumbsComponent(list, breadcrumb, selected);
      SimpleColoredComponent fileNameComponent = createFileNameComponent(list, placeInfo, breadcrumb, selected);

      JComponent titledSeparator = new TitledSeparator();
      JComponent title = JBUI.Panels
        .simplePanel()
        .addToLeft(breadcrumbTextComponent)
        .addToCenter(titledSeparator)
        .addToRight(fileNameComponent);

      JComponent editorComponent = smallEditor.getComponent();

      smallEditor.setBorder(BorderFactory.createEmptyBorder());
      editorComponent.setBorder(BorderFactory.createEmptyBorder());

      JPanel editorPanel = new JPanel(new VerticalFlowLayout(0, 0));
      editorPanel.add(title);
      editorPanel.add(editorComponent);

      updateBackground(smallEditor, title, titledSeparator, breadcrumbTextComponent,
                       selected ? BACKGROUND_COLOR : UIUtil.getEditorPaneBackground());

      return editorPanel;
    }

    @NotNull
    public SimpleColoredComponent createBreadcrumbsComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                             @NotNull String breadcrumb,
                                                             boolean selected) {
      SimpleColoredComponent breadcrumbTextComponent = new SimpleColoredComponent();
      breadcrumbTextComponent.append(breadcrumb);
      Iterable<TextRange> breadCrumbRanges = mySpeedSearch.matchingFragments(breadcrumb);
      if (breadCrumbRanges != null) {
        SpeedSearchUtil.applySpeedSearchHighlighting(list, breadcrumbTextComponent, true, selected);
      }

      return breadcrumbTextComponent;
    }

    @NotNull
    public SimpleColoredComponent createFileNameComponent(@NotNull JList<? extends RecentLocationItem> list,
                                                          @NotNull PlaceInfo placeInfo, @NotNull String breadcrumb, boolean selected) {
      SimpleColoredComponent fileNameComponent = new SimpleColoredComponent();
      if (!StringUtil.equals(breadcrumb, placeInfo.getFile().getName())) {
        fileNameComponent.append(placeInfo.getFile().getName());
        fileNameComponent.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 2));
        Iterable<TextRange> fileNameRanges = mySpeedSearch.matchingFragments(placeInfo.getFile().getName());
        if (fileNameRanges != null) {
          SpeedSearchUtil.applySpeedSearchHighlighting(list, fileNameComponent, true, selected);
        }
      }

      return fileNameComponent;
    }

    private static void updateBackground(@NotNull EditorEx smallEditor,
                                         @NotNull JComponent title,
                                         @NotNull JComponent titledSeparator,
                                         @NotNull SimpleColoredComponent breadcrumbTextComponent, Color background) {
      title.setBackground(background);
      smallEditor.setBackgroundColor(background);
      titledSeparator.setBackground(background);
      breadcrumbTextComponent.setBackground(background);
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends RecentLocationItem> list,
                                         RecentLocationItem value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {

    }
  }

  @NotNull
  private static EditorEx createEditor(@NotNull Project project, @NotNull String text, int startLineNumber, @NotNull PlaceInfo placeInfo) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument(text);
    EditorEx editor = (EditorEx)editorFactory.createEditor(editorDocument, project);
    editor.getGutterComponentEx().setLineNumberConvertor(index -> index + startLineNumber);
    fillEditorSettings(editor.getSettings());
    setHighlighting(project, editor, placeInfo);

    return editor;
  }

  private static void setHighlighting(@NotNull Project project, @NotNull EditorEx editor, @NotNull PlaceInfo placeInfo) {
    VirtualFile file = placeInfo.getFile();

    Language language = LanguageUtil.getLanguageForPsi(project, file);
    if (language == null) {
      return;
    }

    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, null);

    EditorColorsScheme colorsScheme = RecentLocationManager.getInstance(project).getColorScheme(placeInfo);
    if (colorsScheme == null) {
      colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    }

    EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(syntaxHighlighter, colorsScheme);

    LexerPosition lexerPosition = RecentLocationManager.getInstance(project).getLexerPosition(placeInfo);
    if (lexerPosition != null) {
      Lexer lexer = syntaxHighlighter.getHighlightingLexer();
      lexer.start(editor.getDocument().getText());
      lexer.restore(lexerPosition);
    }

    editor.setHighlighter(highlighter);
    editor.setColorsScheme(colorsScheme);
  }

  private static void fillEditorSettings(@NotNull EditorSettings settings) {
    settings.setLineNumbersShown(true);
    settings.setCaretRowShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setAdditionalColumnsCount(0);
    settings.setAdditionalLinesCount(0);
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

  private interface ShowRecentChangedLocationListener {
    Topic<ShowRecentChangedLocationListener> TOPIC = Topic.create("ShowRecentChangedLocation", ShowRecentChangedLocationListener.class);

    void showChangedLocation(boolean state);
  }
}

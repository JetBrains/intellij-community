/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.find.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.*;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class ShowUsagesAction extends AnAction implements PopupAction {
  private final boolean showSettingsDialogBefore;

  private static final int USAGES_PAGE_SIZE = 100;
  private static final Comparator<Object> USAGE_COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object c1, Object c2) {
      if (!(c1 instanceof UsageNode)) return 1;
      if (!(c2 instanceof UsageNode)) return -1;
      Usage o1 = ((UsageNode)c1).getUsage();
      Usage o2 = ((UsageNode)c2).getUsage();
      if (o1 == NullUsage.INSTANCE) return 1;
      if (o2 == NullUsage.INSTANCE) return -1;

      VirtualFile v1 = UsageListCellRenderer.getVirtualFile(o1);
      VirtualFile v2 = UsageListCellRenderer.getVirtualFile(o2);
      String name1 = v1 == null ? null : v1.getName();
      String name2 = v2 == null ? null : v2.getName();
      int i = Comparing.compare(name1, name2);
      if (i!=0) return i;

      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        return ((Comparable)o1).compareTo(o2);
      }

      FileEditorLocation loc1 = o1.getLocation();
      FileEditorLocation loc2 = o2.getLocation();
      return Comparing.compare(loc1, loc2);
    }
  };
  private static final Runnable HIDE_HINTS_ACTION = new Runnable() {
    @Override
    public void run() {
      hideHints();
    }
  };
  private final UsageViewSettings myUsageViewSettings;
  @Nullable private Runnable mySearchEverywhereRunnable;

  // used from plugin.xml
  @SuppressWarnings({"UnusedDeclaration"})
  public ShowUsagesAction() {
    this(false);
  }

  private ShowUsagesAction(boolean showDialogBefore) {
    setInjectedContext(true);
    showSettingsDialogBefore = showDialogBefore;

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    myUsageViewSettings = new UsageViewSettings();
    myUsageViewSettings.loadState(usageViewSettings);
    myUsageViewSettings.GROUP_BY_FILE_STRUCTURE = false;
    myUsageViewSettings.GROUP_BY_MODULE = false;
    myUsageViewSettings.GROUP_BY_PACKAGE = false;
    myUsageViewSettings.GROUP_BY_USAGE_TYPE = false;
    myUsageViewSettings.GROUP_BY_SCOPE = false;
  }

  public static class ShowSettings extends ShowUsagesAction {
    public ShowSettings() {
      super(true);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    Runnable searchEverywhere = mySearchEverywhereRunnable;
    mySearchEverywhereRunnable = null;
    hideHints();

    if (searchEverywhere != null) {
      searchEverywhere.run();
      return;
    }

    myWidth = -1;
    final RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (usageTargets == null) {
      FindUsagesAction.chooseAmbiguousTargetAndPerform(project, editor, new PsiElementProcessor<PsiElement>() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
          return false;
        }
      });
    }
    else {
      PsiElement element = ((PsiElementUsageTarget)usageTargets[0]).getElement();
      if (element != null) {
        startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
      }
    }
  }

  private static void hideHints() {
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
  }

  private void startFindUsages(@NotNull PsiElement element, RelativePoint popupPosition, Editor editor, int maxUsages) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    if (handler == null) return;
    if (showSettingsDialogBefore) {
      showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
      return;
    }
    showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler));
  }

  private static FindUsagesOptions getDefaultOptions(FindUsagesHandler handler) {
    return handler.getFindUsagesOptions(DataManager.getInstance().getDataContext());
  }

  private void showElementUsages(@NotNull final FindUsagesHandler handler,
                                 final Editor editor,
                                 final RelativePoint popupPosition,
                                 final int maxUsages, final FindUsagesOptions options) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setDetachedMode(true);

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    final UsageViewSettings savedGlobalSettings = new UsageViewSettings();

    savedGlobalSettings.loadState(usageViewSettings);
    usageViewSettings.loadState(myUsageViewSettings);

    UsageViewManager manager = UsageViewManager.getInstance(handler.getProject());
    final UsageViewImpl usageView = (UsageViewImpl)manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null);

    Disposer.register(usageView, new Disposable() {
      @Override
      public void dispose() {
        myUsageViewSettings.loadState(usageViewSettings);
        usageViewSettings.loadState(savedGlobalSettings);
      }
    });

    final List<Usage> usages = new ArrayList<Usage>();
    final Set<UsageNode> visibleNodes = new LinkedHashSet<UsageNode>();
    Processor<Usage> collect = new Processor<Usage>() {
      private final UsageTarget[] myUsageTarget = {new PsiElement2UsageTargetAdapter(handler.getPsiElement())};
      @Override
      public boolean process(@NotNull Usage usage) {
        synchronized (usages) {
          if (visibleNodes.size() > maxUsages) return false;
          if(UsageViewManager.isSelfUsage(usage, myUsageTarget)) return true;
          UsageNode node = usageView.doAppendUsage(usage);
          if (node != null) {
            if (visibleNodes.size() == maxUsages) {
              usageView.removeUsage(usage);
              visibleNodes.add(UsageViewImpl.NULL_NODE);
              return false;
            }
            visibleNodes.add(node);
          }
          usages.add(usage);
        }
        return true;
      }
    };
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(handler.getProject())).getFindUsagesManager();
    presentation = findUsagesManager.processUsages(handler, collect, options);
    if (presentation == null) {
      Disposer.dispose(usageView);
      return;
    }
    final String title = presentation.getTabText();

    JBPopup popup = createUsagePopup(usages, visibleNodes, title, handler, editor, popupPosition, maxUsages, usageView, options);
    if (popup == null) {
      Disposer.dispose(usageView);
    }
    else {
      Disposer.register(popup, usageView);
      popup.show(popupPosition);
    }
  }

  private void showHint(String text, final Editor editor, final RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages, FindUsagesOptions options) {
    JComponent label = createHintComponent(text, handler, popupPosition, editor, HIDE_HINTS_ACTION, maxUsages, options, handler.getProject());
    if (editor == null) {
      HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY |
                                                               HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
    }
    else {
      HintManager.getInstance().showInformationHint(editor, label);
    }
  }

  private JComponent createHintComponent(String text, final FindUsagesHandler handler, final RelativePoint popupPosition, final Editor editor,
                                         final Runnable cancelAction,
                                         final int maxUsages, final FindUsagesOptions options, Project project) {
    JComponent label = HintUtil.createInformationLabel(suggestSecondInvocation(options, project, text));
    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction);

    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        mySearchEverywhereRunnable = new Runnable() {
          public void run() {
            searchEverywhere(options, handler, editor, popupPosition, maxUsages);
          }
        };
        super.addNotify();
      }

      @Override
      public void removeNotify() {
        mySearchEverywhereRunnable = null;
        super.removeNotify();
      }
    };
    button.setBackground(label.getBackground());
    panel.setBackground(label.getBackground());
    label.setOpaque(false);
    label.setBorder(null);
    panel.setBorder(HintUtil.createHintBorder());
    panel.add(label, BorderLayout.CENTER);
    panel.add(button, BorderLayout.EAST);
    return panel;
  }

  private InplaceButton createSettingsButton(final FindUsagesHandler handler, final RelativePoint popupPosition,
                                             final Editor editor,
                                             final int maxUsages,
                                             final Runnable cancelAction) {
    String shortcutText = "";
    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return new InplaceButton("Options..." + shortcutText, IconLoader.getIcon("/general/ideOptions.png"), new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
          }
        });
        cancelAction.run();
      }
    });
  }

  private void showDialogAndFindUsages(FindUsagesHandler handler, RelativePoint popupPosition, Editor editor, int maxUsages) {
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(false, false, false);
    dialog.show();
    if (dialog.isOK()) {
      dialog.calcFindUsagesOptions();
      showElementUsages(handler, editor, popupPosition, maxUsages, getDefaultOptions(handler));
    }
  }

  private static String searchScopePresentableName(FindUsagesOptions options, Project project) {
    SearchScope searchScope = options.searchScope;
    if (searchScope == null) searchScope = ProjectScope.getAllScope(project);
    return searchScope.getDisplayName();
  }

  private JBPopup createUsagePopup(final List<Usage> usages,
                                   Set<UsageNode> visibleNodes,
                                   final String title,
                                   final FindUsagesHandler handler,
                                   final Editor editor,
                                   final RelativePoint popupPosition,
                                   final int maxUsages,
                                   final UsageViewImpl usageView, final FindUsagesOptions options) {
    boolean hasMore = visibleNodes.remove(UsageViewImpl.NULL_NODE);

    final Project project = handler.getProject();

    if (visibleNodes.isEmpty()) {
      if (usages.isEmpty()) {
        String text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(options, project));
        showHint(text, editor, popupPosition, handler, maxUsages, options);
        return null;
      }
      else {
        // all usages filtered out
      }
    }
    if (visibleNodes.size() == 1 && usages.size() == 1) {
      //the only usage
      Usage usage = visibleNodes.iterator().next().getUsage();
      navigateAndHint(usage, UsageViewBundle.message("show.usages.only.usage",
                                                     searchScopePresentableName(options, project)), handler, popupPosition,
                      maxUsages, options);
      return null;
    }
    if (visibleNodes.size() == 1 && usages.size() >= 1) {
      // usage view can filter usages down to one
      Usage usage = visibleNodes.iterator().next().getUsage();
      if (areAllUsagesInThisLine(usage, usages)) {
        String hint = UsageViewBundle.message("all.usages.are.in.this.line", usages.size(),
                                              searchScopePresentableName(options, project));
        navigateAndHint(usage, hint, handler, popupPosition, maxUsages, options);
        return null;
      }
    }

    if (hasMore) {
      usages.add(NullUsage.INSTANCE);
      visibleNodes.add(UsageViewImpl.NULL_NODE);
    }
    List<UsageNode> outNodes = new ArrayList<UsageNode>();
    addUsageNodes(usageView.getRoot(), usageView, outNodes);
    int filtered = filtered(usages, usageView);

    final JTable table = new MyTable();
    TableScrollingUtil.installActions(table);
    final Vector<Object> data = new Vector<Object>();
    setModel(table, visibleNodes, usageView, data, filtered);

    final Runnable navigateRunnable = new Runnable() {
      @Override
      public void run() {
        int[] selected = table.getSelectedRows();
        for (int i : selected) {
          Object value = table.getValueAt(i,0);
          if (value instanceof UsageNode) {
            Usage usage = ((UsageNode)value).getUsage();
            if (usage == NullUsage.INSTANCE) {
              appendMoreUsages(editor, popupPosition, handler, maxUsages);
              return;
            }
            navigateAndHint(usage, null, handler, popupPosition, maxUsages, options);
          }
        }
      }
    };

    SpeedSearchBase<JTable> speedSearch = new SpeedSearchBase<JTable>(table) {
      @Override
      protected int getSelectedIndex() {
        return table.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return table.convertRowIndexToModel(viewIndex);
      }

      @Override
      protected Object[] getAllElements() {
        return ArrayUtil.toObjectArray(data);
      }

      @Override
      protected String getElementText(Object element) {
        if (!(element instanceof UsageNode)) return element.toString();
        UsageNode node = (UsageNode)element;
        GroupNode group = (GroupNode)node.getParent();
        Usage usage = node.getUsage();
        if (usage == NullUsage.INSTANCE) return "";
        return usage.getPresentation().getPlainText() + group.toString();
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        int i = data.indexOf(element);
        if (i == -1) return;
        final int viewRow = table.convertRowIndexToView(i);
        table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
      }
    };
    speedSearch.setComparator(new SpeedSearchComparator(false));

    PopupChooserBuilder builder = new PopupChooserBuilder(table);
    if (title != null) {
      String s;
      if (hasMore) {
        s = "<b>Some</b> " + title + " " + "<b>(Only " + (visibleNodes.size() - 1) + " usages shown)</b>";
      }
      else {
        s = title + " (" + UsageViewBundle.message("usages.n", usages.size()) + " found)";
      }
      builder.setTitle(suggestSecondInvocation(options, project, s));
    }

    builder.setMovable(true).setResizable(true);
    builder.setItemChoosenCallback(navigateRunnable);
    final JBPopup[] popup = new JBPopup[1];

    KeyboardShortcut shortcut = getSettingsShortcut();
    if (shortcut != null) {
      builder.registerKeyboardAction(shortcut.getFirstKeyStroke(), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          popup[0].cancel();
          showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
        }
      });
    }
    shortcut = getShowUsagesShortcut();
    if (shortcut != null) {
      builder.registerKeyboardAction(shortcut.getFirstKeyStroke(), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          popup[0].cancel();
          searchEverywhere(options, handler, editor, popupPosition, maxUsages);
        }
      });
    }

    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, new Runnable() {
      @Override
      public void run() {
        popup[0].cancel();
      }
    });
    builder.setCommandButton(button);

    DefaultActionGroup toolbar = new DefaultActionGroup();
    usageView.addFilteringActions(toolbar);

    toolbar.add(UsageGroupingRuleProviderImpl.createGroupByFileStructureAction(usageView));
    toolbar.add(new AnAction("Open Find Usages Toolwindow", "Show all usages in a separate toolwindow", IconLoader.getIcon("/general/toolWindowFind.png")) {
      {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
        setShortcutSet(action.getShortcutSet());
      }
      @Override
      public void actionPerformed(AnActionEvent e) {
        hideHints();
        popup[0].cancel();
        FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
        FindUsagesManager.SearchData data = new FindUsagesManager.SearchData();
        data.myOptions = handler.getFindUsagesOptions();
        SmartPsiElementPointer<PsiElement> pointer =
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(handler.getPsiElement());
        data.myElements = new SmartPsiElementPointer[]{pointer};
        findUsagesManager.rerunAndRecallFromHistory(data);
      }
    });

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);
    builder.setSettingButton(toolBar);

    popup[0] = builder.createPopup();
    for (AnAction action : toolbar.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), popup[0].getContent());
    }

    final MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, new Runnable() {
      @Override
      public void run() {
        rebuildPopup(usageView, usages, table, popup[0]);
      }
    });

    return popup[0];
  }

  private static String suggestSecondInvocation(FindUsagesOptions options, Project project, String s) {
    if (getShowUsagesShortcut() != null && !Comparing.equal(options.searchScope, GlobalSearchScope.allScope(project))) {
      s += "<br><small>Press " + KeymapUtil.getShortcutText(getShowUsagesShortcut()) + " again to search in Project and Libraries</small>";
    }
    return "<html><body>" + s + "</body></html>";
  }

  private void searchEverywhere(FindUsagesOptions options,
                                FindUsagesHandler handler,
                                Editor editor,
                                RelativePoint popupPosition, int maxUsages) {
    FindUsagesOptions cloned = options.clone();
    cloned.searchScope = GlobalSearchScope.allScope(handler.getProject());
    showElementUsages(handler, editor, popupPosition, maxUsages, cloned);
  }

  @Nullable
  private static KeyboardShortcut getShowUsagesShortcut() {
    return ActionManagerEx.getInstanceEx().getKeyboardShortcut("ShowUsages");
  }

  private static int filtered(@NotNull List<Usage> usages, @NotNull UsageViewImpl usageView) {
    int count=0;
    for (Usage usage : usages) {
      if (!usageView.isVisible(usage)) count++;
    }
    return count;
  }

  private static int getUsageOffset(Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) return -1;
    PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
    if (element == null) return -1;
    return element.getTextRange().getStartOffset();
  }
  private static boolean areAllUsagesInThisLine(Usage usage, List<Usage> usages) {
    Editor editor = getEditorFor(usage);
    if (editor == null) return false;
    int offset = getUsageOffset(usage);
    if (offset == -1) return false;
    int lineNumber = editor.getDocument().getLineNumber(offset);
    for (Usage other : usages) {
      Editor otherEditor = getEditorFor(other);
      if (otherEditor != editor) return false;
      int otherOffset = getUsageOffset(other);
      if (otherOffset == -1) return false;
      int otherLine = otherEditor.getDocument().getLineNumber(otherOffset);
      if (otherLine != lineNumber) return false;
    }
    return true;
  }

  private static int setModel(JTable table,
                              Collection<UsageNode> visibleNodes,
                              UsageViewImpl usageView,
                              final Vector<Object> data,
                              int filtered) {
    if (filtered != 0) {
      data.add(UsageViewBundle.message("usages.were.filtered.out", filtered));
    }
    data.addAll(visibleNodes);
    Collections.sort(data, USAGE_COMPARATOR);
    AbstractTableModel model = new AbstractTableModel() {
      @Override
      public int getRowCount() {
        return data.size();
      }

      @Override
      public int getColumnCount() {
        return !data.isEmpty() && data.get(0) instanceof UsageNode ? 3 : 1;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
        return data.get(rowIndex);
      }
    };
    table.setModel(model);

    table.setRowHeight(PlatformIcons.CLASS_ICON.getIconHeight()+2);
    table.setShowGrid(false);
    table.setShowVerticalLines(false);
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    ShowUsagesTableCellRenderer renderer = new ShowUsagesTableCellRenderer(usageView);
    for (int i=0;i<table.getColumnModel().getColumnCount();i++) {
      TableColumn column = table.getColumnModel().getColumn(i);
      column.setCellRenderer(renderer);
    }
    table.setIntercellSpacing(new Dimension(0, 0));

    int colsNum = table.getColumnModel().getColumnCount();

    int totalWidth = 0;
    for (int col = 0; col < colsNum -1; col++) {
      TableColumn column = table.getColumnModel().getColumn(col);
      int preferred = column.getPreferredWidth();
      int width = Math.max(preferred, calcMaxWidth(table, col));
      totalWidth += width;
      column.setMinWidth(width);
      column.setMaxWidth(width);
      column.setWidth(width);
      column.setPreferredWidth(width);
    }

    totalWidth += calcMaxWidth(table, colsNum - 1);

    Dimension dimension = new Dimension(totalWidth, table.getRowHeight() * data.size());
    table.setMinimumSize(dimension);
    table.setSize(dimension);
    table.setPreferredSize(dimension);
    table.setMaximumSize(dimension);
    table.setPreferredScrollableViewportSize(dimension);

    return totalWidth;
  }

  private static int calcMaxWidth(JTable table, int col) {
    TableColumn column = table.getColumnModel().getColumn(col);
    int width = 0;
    for (int row = 0; row < table.getRowCount(); row++) {
      Component component = table.prepareRenderer(column.getCellRenderer(), row, col);

      int rendererWidth = component.getPreferredSize().width;
      width = Math.max(width, rendererWidth + table.getIntercellSpacing().width);
    }
    return width;
  }

  private int myWidth;
  private void rebuildPopup(final UsageViewImpl usageView, final List<Usage> usages, final JTable table, final JBPopup popup) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JComponent content = popup.getContent();
        Window window = SwingUtilities.windowForComponent(content);
        Dimension d = window.getSize();

        final List<UsageNode> nodes = new ArrayList<UsageNode>();
        addUsageNodes(usageView.getRoot(), usageView, nodes);
        int filtered = filtered(usages, usageView);

        int old = table.getModel().getRowCount();
        Vector<Object> data = new Vector<Object>();
        int width = setModel(table, nodes, usageView, data, filtered);


        if (myWidth == -1) myWidth = width;
        Dimension newDim = new Dimension(Math.max(width, d.width + width - myWidth), d.height + (data.size() - old) * table.getRowHeight());
        myWidth = width;
        window.setSize(newDim);
        window.validate();
        window.repaint();
        table.revalidate();
        table.repaint();
      }
    });
  }

  private void appendMoreUsages(Editor editor, RelativePoint popupPosition, FindUsagesHandler handler, int maxUsages) {
    showElementUsages(handler,  editor, popupPosition, maxUsages+USAGES_PAGE_SIZE, getDefaultOptions(handler));
  }

  private static KeyboardShortcut getSettingsShortcut() {
    return ActionManagerEx.getInstanceEx().getKeyboardShortcut("ShowUsagesSettings");
  }

  private static void addUsageNodes(GroupNode root, final UsageViewImpl usageView, List<UsageNode> outNodes) {
    for (UsageNode node : root.getUsageNodes()) {
      Usage usage = node.getUsage();
      if (usageView.isVisible(usage)) {
        node.setParent(root);
        outNodes.add(node);
      }
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, usageView, outNodes);
    }
  }

  @Override
  public void update(AnActionEvent e){
    FindUsagesInFileAction.updateFindUsagesAction(e);
  }

  private void navigateAndHint(Usage usage, final String hint, final FindUsagesHandler handler,
                               final RelativePoint popupPosition,
                               final int maxUsages, final FindUsagesOptions options) {
    usage.navigate(true);
    if (hint == null) return;
    final Editor newEditor = getEditorFor(usage);
    if (newEditor == null) return;
    final Project project = handler.getProject();
    //opening editor is performing in invokeLater
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        newEditor.getScrollingModel().runActionOnScrollingFinished(new Runnable() {
          @Override
          public void run() {
            // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
            IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(new Runnable() {
              @Override
              public void run() {
                if (newEditor.getComponent().isShowing()) {
                  showHint(hint, newEditor, popupPosition, handler, maxUsages, options);
                }
              }
            });
          }
        });
      }
    });
  }

  private static Editor getEditorFor(Usage usage) {
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    return newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
  }

  private static class MyTable extends JBTableWithHintProvider implements DataProvider {
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public Object getData(@NonNls String dataId) {
      if (LangDataKeys.PSI_ELEMENT.is(dataId)) {
        final int[] selected = getSelectedRows();
        if (selected.length == 1) {
          return getPsiElementForHint(getValueAt(selected[0], 0));
        }
      }
      return null;
    }

    @Override
    @Nullable
    protected PsiElement getPsiElementForHint(Object selectedValue) {
      if (selectedValue instanceof UsageNode) {
        final Usage usage = ((UsageNode)selectedValue).getUsage();
        if (usage instanceof UsageInfo2UsageAdapter) {
          final PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
          if (element != null) {
            final PsiElement view = UsageToPsiElementProvider.findAppropriateParentFrom(element);
            return view == null ? element : view;
          }
        }
      }
      return null;
    }
  }
}

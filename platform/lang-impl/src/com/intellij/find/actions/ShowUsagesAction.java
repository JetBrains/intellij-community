/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindManager;
import com.intellij.find.FindSettings;
import com.intellij.find.UsagesPreviewPanelProvider;
import com.intellij.find.findUsages.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.gotoByName.ModelDiff;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.preview.PreviewManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.*;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ShowUsagesAction extends AnAction implements PopupAction {
  public static final String ID = "ShowUsages";
  public static final int USAGES_PAGE_SIZE = 100;

  static final Usage MORE_USAGES_SEPARATOR = NullUsage.INSTANCE;
  static final Usage USAGES_OUTSIDE_SCOPE_SEPARATOR = new UsageAdapter();

  private static final UsageNode MORE_USAGES_SEPARATOR_NODE = UsageViewImpl.NULL_NODE;
  private static final UsageNode USAGES_OUTSIDE_SCOPE_NODE =
    new UsageNode(USAGES_OUTSIDE_SCOPE_SEPARATOR, new UsageViewTreeModelBuilder(new UsageViewPresentation(), UsageTarget.EMPTY_ARRAY));

  private static final Comparator<UsageNode> USAGE_NODE_COMPARATOR = (c1, c2) -> {
    if (c1 instanceof StringNode) return 1;
    if (c2 instanceof StringNode) return -1;
    Usage o1 = c1.getUsage();
    Usage o2 = c2.getUsage();
    int weight1 = o1 == USAGES_OUTSIDE_SCOPE_SEPARATOR ? 2 : o1 == MORE_USAGES_SEPARATOR ? 1 : 0;
    int weight2 = o2 == USAGES_OUTSIDE_SCOPE_SEPARATOR ? 2 : o2 == MORE_USAGES_SEPARATOR ? 1 : 0;
    if (weight1 != weight2) return weight1 - weight2;

    VirtualFile v1 = UsageListCellRenderer.getVirtualFile(o1);
    VirtualFile v2 = UsageListCellRenderer.getVirtualFile(o2);
    String name1 = v1 == null ? null : v1.getName();
    String name2 = v2 == null ? null : v2.getName();
    int i = Comparing.compare(name1, name2);
    if (i != 0) return i;

    if (o1 instanceof Comparable && o2 instanceof Comparable) {
      //noinspection unchecked
      return ((Comparable)o1).compareTo(o2);
    }

    FileEditorLocation loc1 = o1.getLocation();
    FileEditorLocation loc2 = o2.getLocation();
    return Comparing.compare(loc1, loc2);
  };

  private final boolean myShowSettingsDialogBefore;
  private final UsageViewSettings myUsageViewSettings;
  private Runnable mySearchEverywhereRunnable;

  // used from plugin.xml
  @SuppressWarnings("UnusedDeclaration")
  public ShowUsagesAction() {
    this(false);
  }

  private ShowUsagesAction(boolean showDialogBefore) {
    setInjectedContext(true);
    myShowSettingsDialogBefore = showDialogBefore;

    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    myUsageViewSettings = new UsageViewSettings();
    myUsageViewSettings.loadState(usageViewSettings);
    myUsageViewSettings.GROUP_BY_FILE_STRUCTURE = false;
    myUsageViewSettings.GROUP_BY_MODULE = false;
    myUsageViewSettings.GROUP_BY_PACKAGE = false;
    myUsageViewSettings.GROUP_BY_USAGE_TYPE = false;
    myUsageViewSettings.GROUP_BY_SCOPE = false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    FindUsagesInFileAction.updateFindUsagesAction(e);

    if (e.getPresentation().isEnabled()) {
      UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
      if (usageTargets != null && !(ArrayUtil.getFirstElement(usageTargets) instanceof PsiElementUsageTarget)) {
        e.getPresentation().setEnabled(false);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;

    Runnable searchEverywhere = mySearchEverywhereRunnable;
    mySearchEverywhereRunnable = null;
    hideHints();

    if (searchEverywhere != null) {
      searchEverywhere.run();
      return;
    }

    final RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext());
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");

    UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (usageTargets == null) {
      FindUsagesAction.chooseAmbiguousTargetAndPerform(project, editor, element -> {
        startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
        return false;
      });
    }
    else if (ArrayUtil.getFirstElement(usageTargets) instanceof PsiElementUsageTarget) {
      PsiElement element = ((PsiElementUsageTarget)usageTargets[0]).getElement();
      if (element != null) {
        startFindUsages(element, popupPosition, editor, USAGES_PAGE_SIZE);
      }
    }
  }

  private static void hideHints() {
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
  }

  public void startFindUsages(@NotNull PsiElement element, @NotNull RelativePoint popupPosition, Editor editor, int maxUsages) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, false);
    if (handler == null) return;
    if (myShowSettingsDialogBefore) {
      showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
      return;
    }
    showElementUsages(editor, popupPosition, handler, maxUsages, handler.getFindUsagesOptions(DataManager.getInstance().getDataContext()));
  }

  private void showElementUsages(final Editor editor,
                                 @NotNull final RelativePoint popupPosition,
                                 @NotNull final FindUsagesHandler handler,
                                 final int maxUsages,
                                 @NotNull final FindUsagesOptions options) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final UsageViewSettings usageViewSettings = UsageViewSettings.getInstance();
    final UsageViewSettings savedGlobalSettings = new UsageViewSettings();

    savedGlobalSettings.loadState(usageViewSettings);
    usageViewSettings.loadState(myUsageViewSettings);

    final Project project = handler.getProject();
    UsageViewManager manager = UsageViewManager.getInstance(project);
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    final UsageViewPresentation presentation = findUsagesManager.createPresentation(handler, options);
    presentation.setDetachedMode(true);
    final UsageViewImpl usageView = (UsageViewImpl)manager.createUsageView(UsageTarget.EMPTY_ARRAY, Usage.EMPTY_ARRAY, presentation, null);
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor);
      if (reference != null) {
        UsageInfo2UsageAdapter origin = new UsageInfo2UsageAdapter(new UsageInfo(reference));
        usageView.setOriginUsage(origin);
      }
    }

    Disposer.register(usageView, () -> {
      myUsageViewSettings.loadState(usageViewSettings);
      usageViewSettings.loadState(savedGlobalSettings);
    });

    final MyTable table = new MyTable();
    final AsyncProcessIcon processIcon = new AsyncProcessIcon("xxx");

    addUsageNodes(usageView.getRoot(), usageView, new ArrayList<>());

    final List<Usage> usages = new ArrayList<>();
    final Set<UsageNode> visibleNodes = new LinkedHashSet<>();
    final List<UsageNode> data = collectData(usages, visibleNodes, usageView, presentation);
    final AtomicInteger outOfScopeUsages = new AtomicInteger();
    setTableModel(table, usageView, data, outOfScopeUsages, options.searchScope);


    boolean isPreviewMode = Boolean.TRUE == PreviewManager.SERVICE.preview(handler.getProject(), UsagesPreviewPanelProvider.ID, Pair.create(usageView, table), false);
    Runnable itemChosenCallback = prepareTable(table, editor, popupPosition, handler, maxUsages, options, isPreviewMode);

    @Nullable final JBPopup popup = isPreviewMode ? null : createUsagePopup(usages, visibleNodes, handler, editor, popupPosition,
                                           maxUsages, usageView, options, table, itemChosenCallback, presentation, processIcon);
    if (popup != null) {
      Disposer.register(popup, usageView);

      // show popup only if find usages takes more than 300ms, otherwise it would flicker needlessly
      Alarm alarm = new Alarm(usageView);
      alarm.addRequest(() -> showPopupIfNeedTo(popup, popupPosition), 300);
    }

    final PingEDT pingEDT = new PingEDT("Rebuild popup in EDT", o -> popup != null && popup.isDisposed(), 100, () -> {
      if (popup != null && popup.isDisposed()) return;

      final List<UsageNode> nodes = new ArrayList<>();
      List<Usage> copy;
      synchronized (usages) {
        // open up popup as soon as several usages 've been found
        if (popup != null && !popup.isVisible() && (usages.size() <= 1 || !showPopupIfNeedTo(popup, popupPosition))) {
          return;
        }
        addUsageNodes(usageView.getRoot(), usageView, nodes);
        copy = new ArrayList<>(usages);
      }

      rebuildTable(usageView, copy, nodes, table, popup, presentation, popupPosition, !processIcon.isDisposed(), outOfScopeUsages,
                   options.searchScope);
    });

    final MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, pingEDT::ping);

    final UsageTarget[] myUsageTarget = {new PsiElement2UsageTargetAdapter(handler.getPsiElement())};
    Processor<Usage> collect = usage -> {
      if (!UsageViewManagerImpl.isInScope(usage, options.searchScope)) {
        if (outOfScopeUsages.getAndIncrement() == 0) {
          visibleNodes.add(USAGES_OUTSIDE_SCOPE_NODE);
          usages.add(USAGES_OUTSIDE_SCOPE_SEPARATOR);
        }
        return true;
      }
      synchronized (usages) {
        if (visibleNodes.size() >= maxUsages) return false;
        if(UsageViewManager.isSelfUsage(usage, myUsageTarget)) return true;
        UsageNode node = ApplicationManager.getApplication().runReadAction(new Computable<UsageNode>() {
          @Override
          public UsageNode compute() {
            return usageView.doAppendUsage(usage);
          }
        });
        usages.add(usage);
        if (node != null) {
          visibleNodes.add(node);
          boolean continueSearch = true;
          if (visibleNodes.size() == maxUsages) {
            visibleNodes.add(MORE_USAGES_SEPARATOR_NODE);
            usages.add(MORE_USAGES_SEPARATOR);
            continueSearch = false;
          }
          pingEDT.ping();

          return continueSearch;
        }
      }

      return true;
    };

    final ProgressIndicator indicator = FindUsagesManager.startProcessUsages(handler, handler.getPrimaryElements(), handler.getSecondaryElements(), collect,
       options, ()-> ApplicationManager.getApplication().invokeLater(() -> {
         Disposer.dispose(processIcon);
         Container parent = processIcon.getParent();
         if (parent != null) {
           parent.remove(processIcon);
           parent.repaint();
         }
         pingEDT.ping(); // repaint title
         synchronized (usages) {
           if (visibleNodes.isEmpty()) {
             if (usages.isEmpty()) {
               String text = UsageViewBundle.message("no.usages.found.in", searchScopePresentableName(options));
               hint(editor, text, handler, popupPosition, maxUsages, options, false);
               cancel(popup);
             }
             // else all usages filtered out
           }
           else if (visibleNodes.size() == 1) {
             if (usages.size() == 1) {
               //the only usage
               Usage usage = visibleNodes.iterator().next().getUsage();
               if (usage == USAGES_OUTSIDE_SCOPE_SEPARATOR) {
                 hint(editor, UsageViewManagerImpl.outOfScopeMessage(outOfScopeUsages.get(), options.searchScope), handler, popupPosition, maxUsages, options, true);
               }
               else {
                 String message = UsageViewBundle.message("show.usages.only.usage", searchScopePresentableName(options));
                 navigateAndHint(usage, message, handler, popupPosition, maxUsages, options);
               }
               cancel(popup);
             }
             else {
               assert usages.size() > 1 : usages;
               // usage view can filter usages down to one
               Usage visibleUsage = visibleNodes.iterator().next().getUsage();
               if (areAllUsagesInOneLine(visibleUsage, usages)) {
                 String hint = UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScopePresentableName(options));
                 navigateAndHint(visibleUsage, hint, handler, popupPosition, maxUsages, options);
                 cancel(popup);
               }
             }
           }
           else {
             if (popup != null) {
               String title = presentation.getTabText();
               boolean shouldShowMoreSeparator = visibleNodes.contains(MORE_USAGES_SEPARATOR_NODE);
               String fullTitle =
                 getFullTitle(usages, title, shouldShowMoreSeparator, visibleNodes.size() - (shouldShowMoreSeparator ? 1 : 0), false);
               ((AbstractPopup)popup).setCaption(fullTitle);
             }
           }
         }
       }, project.getDisposed()));
    if (popup != null) {
      Disposer.register(popup, indicator::cancel);
    }
  }

  @NotNull
  private static UsageNode createStringNode(@NotNull final Object string) {
    return new StringNode(string);
  }

  private static class MyModel extends ListTableModel<UsageNode> implements ModelDiff.Model<Object> {
    private MyModel(@NotNull List<UsageNode> data, int cols, @NotNull UsageViewImpl usageView) {
      super(cols(cols, usageView), data, 0);
    }

    @NotNull
    private static ColumnInfo[] cols(int cols, @NotNull UsageViewImpl usageView) {
      ColumnInfo<UsageNode, UsageNode> o = new ColumnInfo<UsageNode, UsageNode>("") {
        @Nullable
        @Override
        public UsageNode valueOf(UsageNode node) {
          return node;
        }
      };
      List<ColumnInfo<UsageNode, UsageNode>> list = Collections.nCopies(cols, o);
      return list.toArray(new ColumnInfo[list.size()]);
    }

    @Override
    public void addToModel(int idx, Object element) {
      UsageNode node = element instanceof UsageNode ? (UsageNode)element : createStringNode(element);

      if (idx < getRowCount()) {
        insertRow(idx, node);
      }
      else {
        addRow(node);
      }
    }

    @Override
    public void removeRangeFromModel(int start, int end) {
      for (int i=end; i>=start; i--) {
        removeRow(i);
      }
    }
  }

  private static boolean showPopupIfNeedTo(@NotNull JBPopup popup, @NotNull RelativePoint popupPosition) {
    if (!popup.isDisposed() && !popup.isVisible()) {
      popup.show(popupPosition);
      return true;
    }
    else {
      return false;
    }
  }


  @NotNull
  private JComponent createHintComponent(@NotNull String text,
                                         @NotNull final FindUsagesHandler handler,
                                         @NotNull final RelativePoint popupPosition,
                                         final Editor editor,
                                         @NotNull final Runnable cancelAction,
                                         final int maxUsages,
                                         @NotNull final FindUsagesOptions options,
                                         boolean isWarning) {
    JComponent label = HintUtil.createInformationLabel(suggestSecondInvocation(options, handler, text + "&nbsp;"));
    if (isWarning) {
      label.setBackground(MessageType.WARNING.getPopupBackground());
    }
    InplaceButton button = createSettingsButton(handler, popupPosition, editor, maxUsages, cancelAction);

    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        mySearchEverywhereRunnable = () -> searchEverywhere(options, handler, editor, popupPosition, maxUsages);
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

  @NotNull
  private InplaceButton createSettingsButton(@NotNull final FindUsagesHandler handler,
                                             @NotNull final RelativePoint popupPosition,
                                             final Editor editor,
                                             final int maxUsages,
                                             @NotNull final Runnable cancelAction) {
    String shortcutText = "";
    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    if (shortcut != null) {
      shortcutText = "(" + KeymapUtil.getShortcutText(shortcut) + ")";
    }
    return new InplaceButton("Settings..." + shortcutText, AllIcons.General.Settings, e -> {
      ApplicationManager.getApplication().invokeLater(() -> showDialogAndFindUsages(handler, popupPosition, editor, maxUsages));
      cancelAction.run();
    });
  }

  private void showDialogAndFindUsages(@NotNull FindUsagesHandler handler,
                                       @NotNull RelativePoint popupPosition,
                                       Editor editor,
                                       int maxUsages) {
    AbstractFindUsagesDialog dialog = handler.getFindUsagesDialog(false, false, false);
    if (dialog.showAndGet()) {
      dialog.calcFindUsagesOptions();
      FindUsagesOptions options = handler.getFindUsagesOptions(DataManager.getInstance().getDataContext());
      showElementUsages(editor, popupPosition, handler, maxUsages, options);
    }
  }

  @NotNull
  private static String searchScopePresentableName(@NotNull FindUsagesOptions options) {
    return options.searchScope.getDisplayName();
  }

  @NotNull
  private Runnable prepareTable(final MyTable table,
                                final Editor editor,
                                final RelativePoint popupPosition,
                                final FindUsagesHandler handler,
                                final int maxUsages,
                                @NotNull final FindUsagesOptions options,
                                final boolean previewMode) {

    SpeedSearchBase<JTable> speedSearch = new MySpeedSearch(table);
    speedSearch.setComparator(new SpeedSearchComparator(false));

    table.setRowHeight(PlatformIcons.CLASS_ICON.getIconHeight()+2);
    table.setShowGrid(false);
    table.setShowVerticalLines(false);
    table.setShowHorizontalLines(false);
    table.setTableHeader(null);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    table.setIntercellSpacing(new Dimension(0, 0));

    final AtomicReference<List<Object>> selectedUsages = new AtomicReference<>();
    final AtomicBoolean moreUsagesSelected = new AtomicBoolean();
    final AtomicBoolean outsideScopeUsagesSelected = new AtomicBoolean();
    table.getSelectionModel().addListSelectionListener(e -> {
      selectedUsages.set(null);
      outsideScopeUsagesSelected.set(false);
      moreUsagesSelected.set(false);
      List<Object> usages = null;

      for (int i : table.getSelectedRows()) {
        Object value = table.getValueAt(i, 0);
        if (value instanceof UsageNode) {
          Usage usage = ((UsageNode)value).getUsage();
          if (usage == USAGES_OUTSIDE_SCOPE_SEPARATOR) {
            outsideScopeUsagesSelected.set(true);
            usages = null;
            break;
          }
          else if (usage == MORE_USAGES_SEPARATOR) {
            moreUsagesSelected.set(true);
            usages = null;
            break;
          }
          else {
            if (usages == null) usages = new ArrayList<>();
            usages.add(usage instanceof UsageInfo2UsageAdapter ? ((UsageInfo2UsageAdapter)usage).getUsageInfo().copy() : usage);
          }
        }
      }

      selectedUsages.set(usages);
    });

    final Runnable itemChosenCallback = () -> {
      if (moreUsagesSelected.get()) {
        appendMoreUsages(editor, popupPosition, handler, maxUsages, options);
        return;
      }

      if (outsideScopeUsagesSelected.get()) {
        options.searchScope = GlobalSearchScope.projectScope(handler.getProject());
        showElementUsages(editor, popupPosition, handler, maxUsages, options);
        return;
      }

      List<Object> usages = selectedUsages.get();
      if (usages != null) {
        for (Object usage : usages) {
          if (usage instanceof UsageInfo) {
            UsageViewUtil.navigateTo((UsageInfo)usage, true);
          }
          else if (usage instanceof Navigatable) {
            ((Navigatable)usage).navigate(true);
          }
        }
      }
    };

    if (previewMode) {
      table.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed()) {
            itemChosenCallback.run();
          }
        }
      });
      table.addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            itemChosenCallback.run();
          }
        }
      });
    }

    return itemChosenCallback;
  }

  @NotNull
  private JBPopup createUsagePopup(@NotNull final List<Usage> usages,
                                   @NotNull Set<UsageNode> visibleNodes,
                                   @NotNull final FindUsagesHandler handler,
                                   final Editor editor,
                                   @NotNull final RelativePoint popupPosition,
                                   final int maxUsages,
                                   @NotNull final UsageViewImpl usageView,
                                   @NotNull final FindUsagesOptions options,
                                   @NotNull final JTable table,
                                   @NotNull final Runnable itemChoseCallback,
                                   @NotNull final UsageViewPresentation presentation,
                                   @NotNull final AsyncProcessIcon processIcon) {

    PopupChooserBuilder builder = new PopupChooserBuilder(table);
    final String title = presentation.getTabText();
    if (title != null) {
      String result = getFullTitle(usages, title, false, visibleNodes.size() - 1, true);
      builder.setTitle(result);
      builder.setAdText(getSecondInvocationTitle(options, handler));
    }

    builder.setMovable(true).setResizable(true);
    builder.setMovable(true).setResizable(true);
    builder.setItemChoosenCallback(itemChoseCallback);
    final JBPopup[] popup = new JBPopup[1];

    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    if (shortcut != null) {
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          cancel(popup[0]);
          showDialogAndFindUsages(handler, popupPosition, editor, maxUsages);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcut.getFirstKeyStroke()), table);
    }
    shortcut = getShowUsagesShortcut();
    if (shortcut != null) {
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          cancel(popup[0]);
          searchEverywhere(options, handler, editor, popupPosition, maxUsages);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcut.getFirstKeyStroke()), table);
    }

    InplaceButton settingsButton = createSettingsButton(handler, popupPosition, editor, maxUsages, () -> cancel(popup[0]));

    ActiveComponent spinningProgress = new ActiveComponent.Adapter() {
      @Override
      public JComponent getComponent() {
        return processIcon;
      }
    };
    final DefaultActionGroup pinGroup = new DefaultActionGroup();
    final ActiveComponent pin = createPinButton(handler, usageView, options, popup, pinGroup);
    builder.setCommandButton(new CompositeActiveComponent(spinningProgress, settingsButton, pin));

    DefaultActionGroup toolbar = new DefaultActionGroup();
    usageView.addFilteringActions(toolbar);

    toolbar.add(UsageGroupingRuleProviderImpl.createGroupByFileStructureAction(usageView)); 
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, toolbar, true);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent toolBar = actionToolbar.getComponent();
    toolBar.setOpaque(false);
    builder.setSettingButton(toolBar);
    builder.setCancelKeyEnabled(false); 

    popup[0] = builder.createPopup();
    JComponent content = popup[0].getContent();

    myWidth = (int)(toolBar.getPreferredSize().getWidth()
                  + new JLabel(getFullTitle(usages, title, false, visibleNodes.size() - 1, true)).getPreferredSize().getWidth()
                  + settingsButton.getPreferredSize().getWidth());
    myWidth = -1;
    for (AnAction action : toolbar.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }

    for (AnAction action : pinGroup.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }

    return popup[0];
  }

  private ActiveComponent createPinButton(@NotNull final FindUsagesHandler handler,
                                          @NotNull final UsageViewImpl usageView,
                                          @NotNull final FindUsagesOptions options,
                                          @NotNull final JBPopup[] popup,
                                          @NotNull DefaultActionGroup pinGroup) {
    final AnAction pinAction =
      new AnAction("Open Find Usages Toolwindow", "Show all usages in a separate toolwindow", AllIcons.General.AutohideOff) {
        {
          AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
          setShortcutSet(action.getShortcutSet());
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          hideHints();
          cancel(popup[0]);
          FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(usageView.getProject())).getFindUsagesManager();
          findUsagesManager.findUsages(handler.getPrimaryElements(), handler.getSecondaryElements(), handler, options,
                                       FindSettings.getInstance().isSkipResultsWithOneUsage());
        }
      };
    pinGroup.add(pinAction);
    final ActionToolbar pinToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.USAGE_VIEW_TOOLBAR, pinGroup, true);
    pinToolbar.setReservePlaceAutoPopupIcon(false);
    final JComponent pinToolBar = pinToolbar.getComponent();
    pinToolBar.setBorder(null);
    pinToolBar.setOpaque(false);

    return new ActiveComponent.Adapter() {
      @Override
      public JComponent getComponent() {
        return pinToolBar;
      }
    };
  }

  private static void cancel(@Nullable JBPopup popup) {
    if (popup != null) {
      popup.cancel();
    }
  }

  @NotNull
  private static String getFullTitle(@NotNull List<Usage> usages,
                                     @NotNull String title,
                                     boolean hadMoreSeparator,
                                     int visibleNodesCount,
                                     boolean findUsagesInProgress) {
    String s;
    String soFarSuffix = findUsagesInProgress ? " so far" : "";
    if (hadMoreSeparator) {
      s = "<b>Some</b> " + title + " " + "<b>(Only " + visibleNodesCount + " usages shown" + soFarSuffix + ")</b>";
    }
    else {
      s = title + " (" + UsageViewBundle.message("usages.n", usages.size()) + soFarSuffix + ")";
    }
    return "<html><nobr>" + s + "</nobr></html>";
  }

  @NotNull
  private static String suggestSecondInvocation(@NotNull FindUsagesOptions options, @NotNull FindUsagesHandler handler, @NotNull String text) {
    final String title = getSecondInvocationTitle(options, handler);

    if (title != null) {
        text += "<br><small> " + title + "</small>";
    }
    return XmlStringUtil.wrapInHtml(UIUtil.convertSpace2Nbsp(text));
  }

  @Nullable
  private static String getSecondInvocationTitle(@NotNull FindUsagesOptions options, @NotNull FindUsagesHandler handler) {
    if (getShowUsagesShortcut() != null) {
       GlobalSearchScope maximalScope = FindUsagesManager.getMaximalScope(handler);
      if (!options.searchScope.equals(maximalScope)) {
         return "Press " + KeymapUtil.getShortcutText(getShowUsagesShortcut()) + " again to search in " + maximalScope.getDisplayName();
       }
     }
     return null;
  }

  private void searchEverywhere(@NotNull FindUsagesOptions options,
                                @NotNull FindUsagesHandler handler,
                                Editor editor,
                                @NotNull RelativePoint popupPosition,
                                int maxUsages) {
    FindUsagesOptions cloned = options.clone();
    cloned.searchScope = FindUsagesManager.getMaximalScope(handler);
    showElementUsages(editor, popupPosition, handler, maxUsages, cloned);
  }

  @Nullable
  private static KeyboardShortcut getShowUsagesShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut(ID);
  }

  private static int filtered(@NotNull List<Usage> usages, @NotNull UsageViewImpl usageView) {
    int count=0;
    for (Usage usage : usages) {
      if (!usageView.isVisible(usage)) count++;
    }
    return count;
  }

  private static int getUsageOffset(@NotNull Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) return -1;
    PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
    if (element == null) return -1;
    return element.getTextRange().getStartOffset();
  }

  private static boolean areAllUsagesInOneLine(@NotNull Usage visibleUsage, @NotNull List<Usage> usages) {
    Editor editor = getEditorFor(visibleUsage);
    if (editor == null) return false;
    int offset = getUsageOffset(visibleUsage);
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

  @NotNull
  private static MyModel setTableModel(@NotNull JTable table,
                                       @NotNull UsageViewImpl usageView,
                                       @NotNull final List<UsageNode> data,
                                       @NotNull AtomicInteger outOfScopeUsages,
                                       @NotNull SearchScope searchScope) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final int columnCount = calcColumnCount(data);
    MyModel model = table.getModel() instanceof MyModel ? (MyModel)table.getModel() : null;
    if (model == null || model.getColumnCount() != columnCount) {
      model = new MyModel(data, columnCount, usageView);
      table.setModel(model);

      ShowUsagesTableCellRenderer renderer = new ShowUsagesTableCellRenderer(usageView, outOfScopeUsages, searchScope);
      for (int i=0;i<table.getColumnModel().getColumnCount();i++) {
        TableColumn column = table.getColumnModel().getColumn(i);
        column.setPreferredWidth(0);
        column.setCellRenderer(renderer);
      }
    }
    return model;
  }

  private static int calcColumnCount(@NotNull List<UsageNode> data) {
    return data.isEmpty() || data.get(0) instanceof StringNode ? 1 : 3;
  }

  @NotNull
  private static List<UsageNode> collectData(@NotNull List<Usage> usages,
                                             @NotNull Collection<UsageNode> visibleNodes,
                                             @NotNull UsageViewImpl usageView,
                                             @NotNull UsageViewPresentation presentation) {
    @NotNull List<UsageNode> data = new ArrayList<>();
    int filtered = filtered(usages, usageView);
    if (filtered != 0) {
      data.add(createStringNode(UsageViewBundle.message("usages.were.filtered.out", filtered)));
    }
    data.addAll(visibleNodes);
    if (data.isEmpty()) {
      String progressText = StringUtil.escapeXml(UsageViewManagerImpl.getProgressTitle(presentation));
      data.add(createStringNode(progressText));
    }
    Collections.sort(data, USAGE_NODE_COMPARATOR);
    return data;
  }

  private static int calcMaxWidth(JTable table) {
    int colsNum = table.getColumnModel().getColumnCount();

    int totalWidth = 0;
    for (int col = 0; col < colsNum -1; col++) {
      TableColumn column = table.getColumnModel().getColumn(col);
      int preferred = column.getPreferredWidth();
      int width = Math.max(preferred, columnMaxWidth(table, col));
      totalWidth += width;
      column.setMinWidth(width);
      column.setMaxWidth(width);
      column.setWidth(width);
      column.setPreferredWidth(width);
    }

    totalWidth += columnMaxWidth(table, colsNum - 1);

    return totalWidth;
  }

  private static int columnMaxWidth(@NotNull JTable table, int col) {
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

  private void rebuildTable(@NotNull final UsageViewImpl usageView,
                            @NotNull final List<Usage> usages,
                            @NotNull List<UsageNode> nodes,
                            @NotNull final JTable table,
                            @Nullable final JBPopup popup,
                            @NotNull final UsageViewPresentation presentation,
                            @NotNull final RelativePoint popupPosition,
                            boolean findUsagesInProgress,
                            @NotNull AtomicInteger outOfScopeUsages,
                            @NotNull SearchScope searchScope) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean shouldShowMoreSeparator = usages.contains(MORE_USAGES_SEPARATOR);
    if (shouldShowMoreSeparator) {
      nodes.add(MORE_USAGES_SEPARATOR_NODE);
    }
    boolean hasOutsideScopeUsages = usages.contains(USAGES_OUTSIDE_SCOPE_SEPARATOR);
    if (hasOutsideScopeUsages && !shouldShowMoreSeparator) {
      nodes.add(USAGES_OUTSIDE_SCOPE_NODE);
    }

    String title = presentation.getTabText();
    String fullTitle = getFullTitle(usages, title, shouldShowMoreSeparator || hasOutsideScopeUsages, nodes.size() - (shouldShowMoreSeparator || hasOutsideScopeUsages ? 1 : 0), findUsagesInProgress);
    if (popup != null) {
      ((AbstractPopup)popup).setCaption(fullTitle);
    }

    List<UsageNode> data = collectData(usages, nodes, usageView, presentation);
    MyModel tableModel = setTableModel(table, usageView, data, outOfScopeUsages, searchScope);
    List<UsageNode> existingData = tableModel.getItems();

    int row = table.getSelectedRow();

    int newSelection = updateModel(tableModel, existingData, data, row == -1 ? 0 : row);
    if (newSelection < 0 || newSelection >= tableModel.getRowCount()) {
      ScrollingUtil.ensureSelectionExists(table);
      newSelection = table.getSelectedRow();
    }
    else {
      // do not pre-select the usage under caret by default
      if (newSelection == 0 && table.getModel().getRowCount() > 1) {
        Object valueInTopRow = table.getModel().getValueAt(0, 0);
        if (valueInTopRow instanceof UsageNode && usageView.isOriginUsage(((UsageNode)valueInTopRow).getUsage())) {
          newSelection++;
        }
      }
      table.getSelectionModel().setSelectionInterval(newSelection, newSelection);
    }
    ScrollingUtil.ensureIndexIsVisible(table, newSelection, 0);

    if (popup != null) {
      setSizeAndDimensions(table, popup, popupPosition, data);
    }
  }

  // returns new selection
  private static int updateModel(@NotNull MyModel tableModel, @NotNull List<UsageNode> listOld, @NotNull List<UsageNode> listNew, int oldSelection) {
    UsageNode[] oa = listOld.toArray(new UsageNode[listOld.size()]);
    UsageNode[] na = listNew.toArray(new UsageNode[listNew.size()]);
    List<ModelDiff.Cmd> cmds = ModelDiff.createDiffCmds(tableModel, oa, na);
    int selection = oldSelection;
    if (cmds != null) {
      for (ModelDiff.Cmd cmd : cmds) {
        selection = cmd.translateSelection(selection);
        cmd.apply();
      }
    }
    return selection;
  }

  private void setSizeAndDimensions(@NotNull JTable table,
                                    @NotNull JBPopup popup,
                                    @NotNull RelativePoint popupPosition,
                                    @NotNull List<UsageNode> data) {
    JComponent content = popup.getContent();
    Window window = SwingUtilities.windowForComponent(content);
    Dimension d = window.getSize();

    int width = calcMaxWidth(table);
    width = (int)Math.max(d.getWidth(), width);
    Dimension headerSize = ((AbstractPopup)popup).getHeaderPreferredSize();
    width = Math.max((int)headerSize.getWidth(), width);
    width = Math.max(myWidth, width);

    if (myWidth == -1) myWidth = width;
    int newWidth = Math.max(width, d.width + width - myWidth);

    myWidth = newWidth;

    int rowsToShow = Math.min(30, data.size());
    Dimension dimension = new Dimension(newWidth, table.getRowHeight() * rowsToShow);
    Rectangle rectangle = fitToScreen(dimension, popupPosition, table);
    if (!data.isEmpty()) {
      ScrollingUtil.ensureSelectionExists(table);
    }
    table.setSize(rectangle.getSize());
    //table.setPreferredSize(dimension);
    //table.setMaximumSize(dimension);
    //table.setPreferredScrollableViewportSize(dimension);


    Dimension footerSize = ((AbstractPopup)popup).getFooterPreferredSize();

    int footer = footerSize.height;
    int footerBorder = footer == 0 ? 0 : 1;
    Insets insets = ((AbstractPopup)popup).getPopupBorder().getBorderInsets(content);
    rectangle.height += headerSize.height + footer + footerBorder + insets.top + insets.bottom;
    ScreenUtil.fitToScreen(rectangle);
    Dimension newDim = rectangle.getSize();
    window.setBounds(rectangle);
    window.setMinimumSize(newDim);
    window.setMaximumSize(newDim);

    window.validate();
    window.repaint();
  }

  private static Rectangle fitToScreen(@NotNull Dimension newDim, @NotNull RelativePoint popupPosition, JTable table) {
    Rectangle rectangle = new Rectangle(popupPosition.getScreenPoint(), newDim);
    ScreenUtil.fitToScreen(rectangle);
    if (rectangle.getHeight() != newDim.getHeight()) {
      int newHeight = (int)rectangle.getHeight();
      int roundedHeight = newHeight - newHeight % table.getRowHeight();
      rectangle.setSize((int)rectangle.getWidth(), Math.max(roundedHeight, table.getRowHeight()));
    }
    return rectangle;

  }

  private void appendMoreUsages(Editor editor,
                                @NotNull RelativePoint popupPosition,
                                @NotNull FindUsagesHandler handler,
                                int maxUsages,
                                @NotNull FindUsagesOptions options) {
    showElementUsages(editor, popupPosition, handler, maxUsages+USAGES_PAGE_SIZE, options);
  }

  private static void addUsageNodes(@NotNull GroupNode root, @NotNull final UsageViewImpl usageView, @NotNull List<UsageNode> outNodes) {
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

  private void navigateAndHint(@NotNull Usage usage,
                               @Nullable final String hint,
                               @NotNull final FindUsagesHandler handler,
                               @NotNull final RelativePoint popupPosition,
                               final int maxUsages,
                               @NotNull final FindUsagesOptions options) {
    usage.navigate(true);
    if (hint == null) return;
    final Editor newEditor = getEditorFor(usage);
    if (newEditor == null) return;
    hint(newEditor, hint, handler, popupPosition, maxUsages, options, false);
  }

  private void showHint(@Nullable final Editor editor,
                        @NotNull String hint,
                        @NotNull FindUsagesHandler handler,
                        @NotNull final RelativePoint popupPosition,
                        int maxUsages,
                        @NotNull FindUsagesOptions options,
                        boolean isWarning) {
    Runnable runnable = () -> {
      JComponent label = createHintComponent(hint, handler, popupPosition, editor, ShowUsagesAction::hideHints, maxUsages, options, isWarning);
      if (editor == null || editor.isDisposed() || !editor.getComponent().isShowing()) {
        HintManager.getInstance().showHint(label, popupPosition, HintManager.HIDE_BY_ANY_KEY |
                                                                 HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0);
      }
      else {
        HintManager.getInstance().showInformationHint(editor, label);
      }
    };
    if (editor == null) {
      runnable.run();
    } else {
      AsyncEditorLoader.performWhenLoaded(editor, runnable);
    }
  }

  private void hint(@Nullable final Editor editor,
                    @NotNull final String hint,
                    @NotNull final FindUsagesHandler handler,
                    @NotNull final RelativePoint popupPosition,
                    final int maxUsages,
                    @NotNull final FindUsagesOptions options,
                    final boolean isWarning) {
    final Project project = handler.getProject();
    //opening editor is performing in invokeLater
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
      Runnable runnable = () -> {
        // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
          () -> showHint(editor, hint, handler, popupPosition, maxUsages, options, isWarning));
      };
      if (editor == null) {
        runnable.run();
      }
      else {
        editor.getScrollingModel().runActionOnScrollingFinished(runnable);
      }
    });
  }

  @Nullable
  private static Editor getEditorFor(@NotNull Usage usage) {
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    return newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
  }

  private static class MyTable extends JBTable implements DataProvider {
    private static final int MARGIN = 2;

    public MyTable() {
      ScrollingUtil.installActions(this);
      HintUpdateSupply.installSimpleHintUpdateSupply(this);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
        final int[] selected = getSelectedRows();
        if (selected.length == 1) {
          return getPsiElementForHint(getValueAt(selected[0], 0));
        }
      }
      return null;
    }

    @Override
    public int getRowHeight() {
      return super.getRowHeight() + 2 * MARGIN;
    }
    
    @NotNull
    @Override
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
      Component component = super.prepareRenderer(renderer, row, column);
      if (component instanceof JComponent) {
        ((JComponent)component).setBorder(IdeBorderFactory.createEmptyBorder(MARGIN, MARGIN, MARGIN, 0));
      }
      return component;
    }

    @Nullable
    private static PsiElement getPsiElementForHint(Object selectedValue) {
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

  static class StringNode extends UsageNode {
    @NotNull private final Object myString;

    StringNode(@NotNull Object string) {
      super(NullUsage.INSTANCE, new UsageViewTreeModelBuilder(new UsageViewPresentation(), UsageTarget.EMPTY_ARRAY));
      myString = string;
    }

    @Override
    public String toString() {
      return myString.toString();
    }
  }

  private static class MySpeedSearch extends SpeedSearchBase<JTable> {
    MySpeedSearch(@NotNull MyTable table) {
      super(table);
    }

    @Override
    protected int getSelectedIndex() {
      return getTable().getSelectedRow();
    }

    @Override
    protected int convertIndexToModel(int viewIndex) {
      return getTable().convertRowIndexToModel(viewIndex);
    }

    @NotNull
    @Override
    protected Object[] getAllElements() {
      return ((MyModel)getTable().getModel()).getItems().toArray();
    }

    @Override
    protected String getElementText(@NotNull Object element) {
      if (!(element instanceof UsageNode)) return element.toString();
      UsageNode node = (UsageNode)element;
      if (node instanceof StringNode) return "";
      Usage usage = node.getUsage();
      if (usage == MORE_USAGES_SEPARATOR || usage == USAGES_OUTSIDE_SCOPE_SEPARATOR) return "";
      GroupNode group = (GroupNode)node.getParent();
      return group + usage.getPresentation().getPlainText();
    }

    @Override
    protected void selectElement(Object element, String selectedText) {
      List<UsageNode> data = ((MyModel)getTable().getModel()).getItems();
      int i = data.indexOf(element);
      if (i == -1) return;
      final int viewRow = getTable().convertRowIndexToView(i);
      getTable().getSelectionModel().setSelectionInterval(viewRow, viewRow);
      TableUtil.scrollSelectionToVisible(getTable());
    }

    private MyTable getTable() {
      return (MyTable)myComponent;
    }
  }
}

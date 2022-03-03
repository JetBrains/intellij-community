// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.ModelDiff;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupUpdateProcessor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.usages.impl.*;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import com.intellij.util.*;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.find.actions.ResolverKt.findShowUsages;
import static com.intellij.find.actions.ShowUsagesActionHandler.getSecondInvocationHint;
import static com.intellij.find.findUsages.FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS;
import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public class ShowUsagesAction extends AnAction implements PopupAction, HintManagerImpl.ActionToIgnore, UpdateInBackground {
  public static final String ID = "ShowUsages";
  private static final String DIMENSION_SERVICE_KEY = "ShowUsagesActions.dimensionServiceKey";
  private static final String SPLITTER_SERVICE_KEY = "ShowUsagesActions.splitterServiceKey";
  private static final String PREVIEW_PROPERTY_KEY = "ShowUsagesActions.previewPropertyKey";

  private static int ourPopupDelayTimeout = 300;

  public ShowUsagesAction() {
    setInjectedContext(true);
  }

  private static final class UsageNodeComparator implements Comparator<UsageNode> {
    private final ShowUsagesTable myTable;

    private UsageNodeComparator(@NotNull ShowUsagesTable table) {
      this.myTable = table;
    }

    @Override
    public int compare(UsageNode c1, UsageNode c2) {
      if (c1 instanceof StringNode || c2 instanceof StringNode) {
        if (c1 instanceof StringNode && c2 instanceof StringNode) {
          return Comparing.compare(c1.toString(), c2.toString());
        }
        return c1 instanceof StringNode ? 1 : -1;
      }

      Usage o1 = c1.getUsage();
      Usage o2 = c2.getUsage();
      int weight1 = o1 == myTable.USAGES_FILTERED_OUT_SEPARATOR ? 3 : o1 == myTable.USAGES_OUTSIDE_SCOPE_SEPARATOR ? 2 : o1 == myTable.MORE_USAGES_SEPARATOR ? 1 : 0;
      int weight2 = o2 == myTable.USAGES_FILTERED_OUT_SEPARATOR ? 3 : o2 == myTable.USAGES_OUTSIDE_SCOPE_SEPARATOR ? 2 : o2 == myTable.MORE_USAGES_SEPARATOR ? 1 : 0;
      if (weight1 != weight2) return weight1 - weight2;

      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        //noinspection unchecked,rawtypes
        return ((Comparable)o1).compareTo(o2);
      }

      VirtualFile v1 = UsageListCellRenderer.getVirtualFile(o1);
      VirtualFile v2 = UsageListCellRenderer.getVirtualFile(o2);
      String name1 = v1 == null ? null : v1.getName();
      String name2 = v2 == null ? null : v2.getName();
      int i = Comparing.compare(name1, name2);
      if (i != 0) return i;
      if (Comparing.equal(v1, v2)) {
        FileEditorLocation loc1 = o1.getLocation();
        FileEditorLocation loc2 = o2.getLocation();
        return Comparing.compare(loc1, loc2);
      }
      else {
        String path1 = v1 == null ? null : v1.getPath();
        String path2 = v2 == null ? null : v2.getPath();
        return Comparing.compare(path1, path2);
      }
    }
  }

  public static int getUsagesPageSize() {
    return Math.max(1, AdvancedSettings.getInt("ide.usages.page.size"));
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
    Project project = e.getProject();
    if (project == null) return;

    ShowUsagesActionState state = getState(project);
    Runnable continuation = state.continuation;
    if (continuation != null) {
      state.continuation = null;
      hideHints(); // This action is invoked when the hint is showing because it implements HintManagerImpl.ActionToIgnore
      continuation.run();
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.usages");
    DataContext dataContext = e.getDataContext();
    showUsages(project, dataContext, ResolverKt.allTargets(dataContext));
  }

  @ApiStatus.Internal
  public static void showUsages(@NotNull Project project,
                                @NotNull DataContext dataContext,
                                @NotNull List<? extends @NotNull TargetVariant> targetVariants) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    RelativePoint popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(dataContext);
    SearchScope searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindSettings.getInstance().getDefaultScopeName());
    SlowOperations.allowSlowOperations(() -> findShowUsages(
      project, dataContext, targetVariants, FindBundle.message("show.usages.ambiguous.title"),
      createVariantHandler(project, editor, popupPosition, searchScope)
    ));
  }

  @NotNull
  private static UsageVariantHandler createVariantHandler(@NotNull Project project,
                                                          @Nullable Editor editor,
                                                          @NotNull RelativePoint popupPosition,
                                                          @NotNull SearchScope searchScope) {
    return new UsageVariantHandler() {

      @Override
      public void handleTarget(@NotNull SearchTarget target) {
        ShowTargetUsagesActionHandler.showUsages(
          project, searchScope, target,
          ShowUsagesParameters.initial(project, editor, popupPosition)
        );
      }

      @Override
      public void handlePsi(@NotNull PsiElement element) {
        startFindUsages(element, popupPosition, editor);
      }
    };
  }

  /**
   * Shows Usage popup for a single search target without disambiguation via Choose Target popup.
   */
  @ApiStatus.Internal
  public static void showUsages(@NotNull Project project,
                                @NotNull DataContext dataContext,
                                @NotNull RelativePoint popupPosition,
                                @NotNull SearchTarget target) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    SearchScope searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindSettings.getInstance().getDefaultScopeName());
    ShowTargetUsagesActionHandler.showUsages(
      project, searchScope, target,
      ShowUsagesParameters.initial(project, editor, popupPosition)
    );
  }

  private static void hideHints() {
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
  }

  public static void startFindUsages(@NotNull PsiElement element, @NotNull RelativePoint popupPosition, @Nullable Editor editor) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandlerBase handler = findUsagesManager.getFindUsagesHandler(element, USAGES_WITH_DEFAULT_OPTIONS);
    if (handler == null) return;
    //noinspection deprecation
    FindUsagesOptions options = handler.getFindUsagesOptions(DataManager.getInstance().getDataContext());
    showElementUsages(ShowUsagesParameters.initial(project, editor, popupPosition), createActionHandler(handler, options));
  }

  private static void rulesChanged(@NotNull UsageViewImpl usageView, @NotNull PingEDT pingEDT, JBPopup popup) {
    // later to make sure UsageViewImpl.rulesChanged was invoked
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if ((popup == null || !popup.isDisposed()) && !usageView.isDisposed()) {
        usageView.waitForUpdateRequestsCompletion();
        if ((popup == null || !popup.isDisposed()) && !usageView.isDisposed()) {
          pingEDT.ping();
        }
      }
    }));
  }

  private static @Nls @NotNull String getUsagesTitle(@NotNull PsiElement element) {
    HtmlBuilder builder = new HtmlBuilder();

    builder.append(StringUtil.capitalize(UsageViewUtil.getType(element))).nbsp().
      append(HtmlChunk.text(UsageViewUtil.getLongName(element)).bold());

    if (element instanceof NavigationItem) {
      ItemPresentation itemPresentation = ((NavigationItem)element).getPresentation();
      if (itemPresentation != null && StringUtil.isNotEmpty(itemPresentation.getLocationString())) {
        builder.nbsp().append(HtmlChunk.text(itemPresentation.getLocationString()).
                                wrapWith("font").attr("color", "#" + ColorUtil.toHex(SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor())));
      }
    }

    return builder.toString();
  }

  @NotNull
  private static ShowUsagesActionHandler createActionHandler(@NotNull FindUsagesHandlerBase handler, @NotNull FindUsagesOptions options) {
    // show super method warning dialogs before starting finding usages
    PsiElement[] primaryElements = handler.getPrimaryElements();
    PsiElement[] secondaryElements = handler.getSecondaryElements();

    String title = getUsagesTitle(handler.getPsiElement());
    String optionsString = options.generateUsagesString();

    return new ShowUsagesActionHandler() {
      @Override
      public boolean isValid() {
        return handler.getPsiElement().isValid();
      }

      @Override
      public @NotNull UsageSearchPresentation getPresentation() {
        return new UsageSearchPresentation() {
          @Nls
          @Override
          public @NotNull String getSearchTargetString() {
            return title;
          }

          @Nls
          @Override
          public @NotNull String getOptionsString() {
            return optionsString;
          }
        };
      }

      @Override
      public @NotNull UsageSearcher createUsageSearcher() {
        return FindUsagesManager.createUsageSearcher(handler, primaryElements, secondaryElements, options);
      }

      @Override
      public @NotNull SearchScope getSelectedScope() {
        return options.searchScope;
      }

      @Override
      public @NotNull GlobalSearchScope getMaximalScope() {
        return FindUsagesManager.getMaximalScope(handler);
      }

      @Override
      public ShowUsagesActionHandler showDialog() {
        FindUsagesOptions newOptions = ShowUsagesAction.showDialog(handler);
        if (newOptions == null) {
          return null;
        }
        else {
          return createActionHandler(handler, newOptions);
        }
      }

      @Override
      public void findUsages() {
        Project project = handler.getProject();
        FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
        findUsagesManager.findUsages(
          handler.getPrimaryElements(), handler.getSecondaryElements(),
          handler, options,
          FindSettings.getInstance().isSkipResultsWithOneUsage()
        );
      }

      @Override
      public @NotNull ShowUsagesActionHandler withScope(@NotNull SearchScope searchScope) {
        FindUsagesOptions newOptions = options.clone();
        newOptions.searchScope = searchScope;
        return createActionHandler(handler, newOptions);
      }

      @Override
      public Language getTargetLanguage() {
        return handler.getPsiElement().getLanguage();
      }

      @Override
      public @NotNull Class<?> getTargetClass() {
        return handler.getPsiElement().getClass();
      }
    };
  }

  static void showElementUsages(@NotNull ShowUsagesParameters parameters, @NotNull ShowUsagesActionHandler actionHandler) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Project project = parameters.project;
    UsageViewImpl usageView = createUsageView(project);
    UsageViewStatisticsCollector.logSearchStarted(project);

    final SearchScope searchScope = actionHandler.getSelectedScope();
    final AtomicInteger outOfScopeUsages = new AtomicInteger();
    AtomicBoolean manuallyResized = new AtomicBoolean();

    Predicate<? super Usage> originUsageCheck = originUsageCheck(parameters.editor);
    var renderer = new ShowUsagesTableCellRenderer(project, originUsageCheck, outOfScopeUsages, searchScope);
    var table = new ShowUsagesTable(renderer);
    AsyncProcessIcon processIcon = new AsyncProcessIcon("xxx");
    TitlePanel statusPanel = new TitlePanel();
    statusPanel.add(processIcon, BorderLayout.WEST);

    addUsageNodes(usageView.getRoot(), usageView, new ArrayList<>());

    List<Usage> usages = new ArrayList<>();
    Set<Usage> visibleUsages = new LinkedHashSet<>();
    table.setTableModel(new SmartList<>(new StringNode(UsageViewBundle.message("progress.searching"))));

    Runnable itemChosenCallback = table.prepareTable(
      showMoreUsagesRunnable(parameters, actionHandler),
      showUsagesInMaximalScopeRunnable(parameters, actionHandler)
    );

    Consumer<AbstractPopup> tableResizer = popup -> {
      if (popup != null && popup.isVisible() && !manuallyResized.get()) {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        var dataSize = table.getModel().getRowCount();
        setPopupSize(table, popup, parameters.popupPosition, parameters.minWidth, properties.isValueSet(PREVIEW_PROPERTY_KEY), dataSize);
      }
    };

    AbstractPopup popup = createUsagePopup(
      usageView, table, itemChosenCallback, tableResizer, statusPanel,
      parameters, actionHandler
    );

    popup.addResizeListener(() -> manuallyResized.set(true), popup);

    ProgressIndicator indicator = new ProgressIndicatorBase();
    if (!popup.isDisposed()) {
      Disposer.register(popup, usageView);
      Disposer.register(popup, indicator::cancel);

      // show popup only if find usages takes more than 300ms, otherwise it would flicker needlessly
      EdtScheduledExecutorService.getInstance().schedule(() -> {
        if (!usageView.isDisposed()) {
          showPopupIfNeedTo(popup, parameters.popupPosition);
        }
      }, ourPopupDelayTimeout, TimeUnit.MILLISECONDS);
    }

    UsageNode USAGES_OUTSIDE_SCOPE_NODE = new UsageNode(null, table.USAGES_OUTSIDE_SCOPE_SEPARATOR);
    UsageNode MORE_USAGES_SEPARATOR_NODE = new UsageNode(null, table.MORE_USAGES_SEPARATOR);

    PingEDT pingEDT = new PingEDT("Rebuild popup in EDT", () -> popup.isDisposed(), 100, () -> {
      if (popup.isDisposed()) return;

      List<UsageNode> nodes = new ArrayList<>(usages.size());
      List<Usage> copy;
      synchronized (usages) {
        // open up popup as soon as the first usage has been found
        if (!popup.isVisible() && (usages.isEmpty() || !showPopupIfNeedTo(popup, parameters.popupPosition))) {
          return;
        }
        addUsageNodes(usageView.getRoot(), usageView, nodes);
        copy = new ArrayList<>(usages);
      }

      boolean shouldShowMoreSeparator = copy.contains(table.MORE_USAGES_SEPARATOR);
      if (shouldShowMoreSeparator) {
        nodes.add(MORE_USAGES_SEPARATOR_NODE);
      }
      boolean hasOutsideScopeUsages = copy.contains(table.USAGES_OUTSIDE_SCOPE_SEPARATOR);
      if (hasOutsideScopeUsages && !shouldShowMoreSeparator) {
        nodes.add(USAGES_OUTSIDE_SCOPE_NODE);
      }
      List<UsageNode> data = new ArrayList<>(nodes);
      int filteredOutCount = getFilteredOutNodeCount(copy, usageView);
      if (filteredOutCount != 0) {
        DefaultActionGroup filteringActions = popup.getUserData(DefaultActionGroup.class);
        if (filteringActions == null) return;

        List<ToggleAction> unselectedActions = Arrays.stream(filteringActions.getChildren(null))
          .filter(action -> action instanceof ToggleAction)
          .map(action -> (ToggleAction)action)
          .filter(ta -> !ta.isSelected(fakeEvent(ta)))
          .filter(ta -> !StringUtil.isEmpty(ta.getTemplatePresentation().getText()))
          .collect(Collectors.toList());
        data.add(new FilteredOutUsagesNode(table.USAGES_FILTERED_OUT_SEPARATOR,
                                           UsageViewBundle.message("usages.were.filtered.out", filteredOutCount),
                                           UsageViewBundle.message("usages.were.filtered.out.tooltip")) {
          @Override
          public void onSelected() {
            // toggle back unselected toggle actions
            toggleFilters(unselectedActions);
            // and restart show usages in hope it will show filtered out items now
            showElementUsages(parameters, actionHandler);
          }
        });
      }
      data.sort(new UsageNodeComparator(table));

      boolean hasMore = shouldShowMoreSeparator || hasOutsideScopeUsages;
      int totalCount = copy.size();
      int visibleCount = totalCount - filteredOutCount;
      statusPanel.setText(getStatusString(!processIcon.isDisposed(), hasMore, visibleCount, totalCount));
      rebuildTable(project, originUsageCheck, data, table, popup, parameters.popupPosition, parameters.minWidth, manuallyResized);
    });

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, () -> rulesChanged(usageView, pingEDT, popup));

    AtomicLong firstUsageAddedTS = new AtomicLong();
    AtomicBoolean tooManyResults = new AtomicBoolean();
    Processor<Usage> collect = usage -> {
      if (!UsageViewManagerImpl.isInScope(usage, searchScope)) {
        if (outOfScopeUsages.getAndIncrement() == 0) {
          visibleUsages.add(USAGES_OUTSIDE_SCOPE_NODE.getUsage());
          usages.add(table.USAGES_OUTSIDE_SCOPE_SEPARATOR);
        }
        return true;
      }
      synchronized (usages) {
        if (visibleUsages.size() >= parameters.maxUsages) {
          tooManyResults.set(true);
          return false;
        }

        UsageNode nodes = ReadAction.compute(() -> usageView.doAppendUsage(usage));
        usages.add(usage);
        firstUsageAddedTS.compareAndSet(0, System.nanoTime()); // Successes only once - at first assignment

        if (nodes != null) {
          visibleUsages.add(nodes.getUsage());
          boolean continueSearch = true;
          if (visibleUsages.size() == parameters.maxUsages) {
            visibleUsages.add(MORE_USAGES_SEPARATOR_NODE.getUsage());
            usages.add(table.MORE_USAGES_SEPARATOR);
            continueSearch = false;
          }
          pingEDT.ping();

          return continueSearch;
        }
      }

      return true;
    };

    UsageSearcher usageSearcher = actionHandler.createUsageSearcher();
    long searchStarted = System.nanoTime();
    FindUsagesManager.startProcessUsages(indicator, project, usageSearcher, collect, () -> ApplicationManager.getApplication().invokeLater(
      () -> {
        Disposer.dispose(processIcon);
        Container parent = processIcon.getParent();
        if (parent != null) {
          parent.remove(processIcon);
          parent.repaint();
        }
        pingEDT.ping(); // repaint status
        synchronized (usages) {
          if (visibleUsages.isEmpty()) {
            if (usages.isEmpty()) {
              String hint = UsageViewBundle.message("no.usages.found.in", searchScope.getDisplayName());
              hint(false, hint, parameters, actionHandler);
              cancel(popup);
            }
            // else all usages filtered out
          }
          else if (visibleUsages.size() == 1) {
            if (usages.size() == 1) {
              //the only usage
              Usage usage = visibleUsages.iterator().next();
              if (usage == table.USAGES_OUTSIDE_SCOPE_SEPARATOR) {
                String hint = UsageViewManagerImpl.outOfScopeMessage(outOfScopeUsages.get(), searchScope);
                hint(true, hint, parameters, actionHandler);
              }
              else {
                String hint = UsageViewBundle.message("show.usages.only.usage", searchScope.getDisplayName());
                navigateAndHint(usage, hint, parameters, actionHandler);
              }
              cancel(popup);
            }
            else {
              assert usages.size() > 1 : usages;
              // usage view can filter usages down to one
              Usage visibleUsage = visibleUsages.iterator().next();
              if (areAllUsagesInOneLine(visibleUsage, usages)) {
                String hint = UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScope.getDisplayName());
                navigateAndHint(visibleUsage, hint, parameters, actionHandler);
                cancel(popup);
              }
            }
          }
        }

        long current = System.nanoTime();
        UsageViewStatisticsCollector.logSearchFinished(project,
           actionHandler.getTargetClass(), searchScope, actionHandler.getTargetLanguage(), usages.size(),
           TimeUnit.MILLISECONDS.convert(current - firstUsageAddedTS.get(), TimeUnit.NANOSECONDS),
           TimeUnit.MILLISECONDS.convert(current - searchStarted, TimeUnit.NANOSECONDS),
           tooManyResults.get(),
           CodeNavigateSource.ShowUsagesPopup);
      },
      project.getDisposed()
    ));
  }

  private static void toggleFilters(@NotNull List<? extends ToggleAction> unselectedActions) {
    for (ToggleAction action : unselectedActions) {
      action.actionPerformed(fakeEvent(action));
    }
  }

  private static @NotNull AnActionEvent fakeEvent(@NotNull ToggleAction action) {
    return new AnActionEvent(
      null, DataContext.EMPTY_CONTEXT, "",
      action.getTemplatePresentation(), ActionManager.getInstance(), 0
    );
  }

  @NotNull
  private static UsageViewImpl createUsageView(@NotNull Project project) {
    UsageViewPresentation usageViewPresentation = new UsageViewPresentation();
    usageViewPresentation.setDetachedMode(true);
    return new UsageViewImpl(project, usageViewPresentation, UsageTarget.EMPTY_ARRAY, null) {
      @Override
      public @NotNull UsageViewSettings getUsageViewSettings() {
        return ShowUsagesSettings.getInstance().getState();
      }
    };
  }

  private static @NotNull Predicate<? super Usage> originUsageCheck(@Nullable Editor editor) {
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor);
      if (reference != null) {
        UsageInfo originUsageInfo = new UsageInfo(reference);
        return usage -> usage instanceof UsageInfo2UsageAdapter &&
                        ((UsageInfo2UsageAdapter)usage).getUsageInfo().equals(originUsageInfo);
      }
    }
    return __ -> false;
  }

  private static boolean showPopupIfNeedTo(@NotNull JBPopup popup, @NotNull RelativePoint popupPosition) {
    if (!popup.isDisposed() && !popup.isVisible()) {
      popup.show(popupPosition);
      return true;
    }
    return false;
  }

  @NotNull
  private static JComponent createHintComponent(@NotNull @NlsContexts.HintText String secondInvocationTitle, boolean isWarning, @NotNull JComponent button) {
    JComponent label = HintUtil.createInformationLabel(secondInvocationTitle);
    if (isWarning) {
      label.setBackground(MessageType.WARNING.getPopupBackground());
    }

    JPanel panel = new JPanel(new BorderLayout());
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
  private static InplaceButton createSettingsButton(@NotNull Project project,
                                                    @NotNull Runnable cancelAction,
                                                    @NotNull Runnable showDialogAndFindUsagesRunnable) {
    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    String tooltip = shortcut == null
                     ? FindBundle.message("show.usages.settings.tooltip")
                     : FindBundle.message("show.usages.settings.tooltip.shortcut", KeymapUtil.getShortcutText(shortcut));
    return new InplaceButton(tooltip, AllIcons.General.Settings, __ -> {
      ApplicationManager.getApplication().invokeLater(showDialogAndFindUsagesRunnable, project.getDisposed());
      cancelAction.run();
    });
  }

  private static @Nullable FindUsagesOptions showDialog(@NotNull FindUsagesHandlerBase handler) {
    UIEventLogger.ShowUsagesPopupShowSettings.log(handler.getProject());
    AbstractFindUsagesDialog dialog;
    if (handler instanceof FindUsagesHandlerUi) {
      dialog = ((FindUsagesHandlerUi)handler).getFindUsagesDialog(false, false, false);
    }
    else {
      dialog = FindUsagesHandler.createDefaultFindUsagesDialog(false, false, false, handler);
    }
    if (dialog.showAndGet()) {
      dialog.calcFindUsagesOptions();
      //noinspection deprecation
      return handler.getFindUsagesOptions(DataManager.getInstance().getDataContext());
    }
    else {
      return null;
    }
  }

  @NotNull
  private static AbstractPopup createUsagePopup(@NotNull UsageViewImpl usageView,
                                                @NotNull JTable table,
                                                @NotNull Runnable itemChoseCallback,
                                                @NotNull Consumer<AbstractPopup> tableResizer,
                                                @NotNull TitlePanel statusPanel,
                                                @NotNull ShowUsagesParameters parameters,
                                                @NotNull ShowUsagesActionHandler actionHandler) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Project project = parameters.project;
    String title = actionHandler.getPresentation().getSearchTargetString();

    PopupChooserBuilder<?> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(table).
      setTitle(XmlStringUtil.wrapInHtml("<body><nobr>" + title + "</nobr></body>")).
      setAdText(getSecondInvocationHint(actionHandler)).
      setMovable(true).
      setResizable(true).
      setCancelKeyEnabled(true).
      setDimensionServiceKey(DIMENSION_SERVICE_KEY);

    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    boolean addCodePreview = properties.isValueSet(PREVIEW_PROPERTY_KEY);
    JBSplitter contentSplitter = null;
    if (addCodePreview) {
      contentSplitter = new OnePixelSplitter(true, .6f);
      contentSplitter.setSplitterProportionKey(SPLITTER_SERVICE_KEY);
      contentSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE);
      contentSplitter.getDivider().setBackground(OnePixelDivider.BACKGROUND);
      builder.setContentSplitter(contentSplitter);
    }

    Disposable contentDisposable = Disposer.newDisposable();
    AtomicReference<AbstractPopup> popupRef = new AtomicReference<>();

    KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
    if (shortcut != null) {
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          cancel(popupRef.get());
          showDialogAndRestart(parameters, actionHandler);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcut.getFirstKeyStroke()), table);
    }
    shortcut = getShowUsagesShortcut();
    if (shortcut != null) {
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          cancel(popupRef.get());
          showUsagesInMaximalScope(parameters, actionHandler);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcut.getFirstKeyStroke()), table);
    }

    ActiveComponent statusComponent = new ActiveComponent() {
      @Override
      public void setActive(boolean active) {
        statusPanel.setActive(active);
      }

      @NotNull
      @Override
      public JComponent getComponent() {
        return statusPanel;
      }
    };
    DefaultActionGroup pinGroup = new DefaultActionGroup();
    ActiveComponent pin = createPinButton(project, popupRef, pinGroup, table, actionHandler::findUsages);
    builder.setCommandButton(new CompositeActiveComponent(statusComponent, pin));

    DefaultActionGroup filteringGroup = new DefaultActionGroup();
    usageView.addFilteringActions(filteringGroup);
    filteringGroup.add(ActionManager.getInstance().getAction("UsageGrouping.FileStructure"));
    filteringGroup.add(new ToggleAction(UsageViewBundle.message("preview.usages.action.text"), null, AllIcons.Actions.PreviewDetailsVertically) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return properties.isValueSet(PREVIEW_PROPERTY_KEY);
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (e.getDataContext() != DataContext.EMPTY_CONTEXT) { // Avoid fake events
          properties.setValue(PREVIEW_PROPERTY_KEY, state);
          cancel(popupRef.get());

          WindowStateService.getInstance().putSize(DIMENSION_SERVICE_KEY, null);
          showElementUsages(parameters, actionHandler);
        }
      }
    });

    JPanel northPanel = new JPanel(new GridBagLayout());
    GridBag gc = new GridBag().nextLine();

    ActionToolbar actionToolbar = createActionToolbar(table, filteringGroup);
    JComponent toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setOpaque(false);
    northPanel.add(toolbarComponent, gc.next());

    ScopeChooserCombo scopeChooserCombo = new ScopeChooserCombo();
    scopeChooserCombo.initialize(project, false, false, actionHandler.getSelectedScope().getDisplayName(), null)
      .onSuccess(__ -> {
        var scopeComboBox = scopeChooserCombo.getComboBox();
        scopeComboBox.setMinimumAndPreferredWidth(JBUIScale.scale(200));
        scopeComboBox.addItemListener(event -> {
          if (event.getStateChange() == ItemEvent.SELECTED) {
            SearchScope scope = scopeChooserCombo.getSelectedScope();
            if (scope != null) {
              UsageViewStatisticsCollector.logScopeChanged(project, actionHandler.getSelectedScope(), scope,
                                                           actionHandler.getTargetClass());
              cancel(popupRef.get());
              showElementUsages(parameters, actionHandler.withScope(scope));
            }
          }
        });
        scopeComboBox.putClientProperty("JComboBox.isBorderless", Boolean.TRUE);
      });
    Disposer.register(contentDisposable, scopeChooserCombo);
    scopeChooserCombo.setButtonVisible(false);
    northPanel.add(scopeChooserCombo, gc.next());

    northPanel.add(new Box.Filler(JBUI.size(10, 0), JBUI.size(10, 0), JBUI.size(Short.MAX_VALUE, 0)), gc.next().weightx(1.0).fillCellHorizontally());

    DefaultActionGroup settingsGroup = new DefaultActionGroup(
      new SettingsAction(project, () -> cancel(popupRef.get()), showDialogAndRestartRunnable(parameters, actionHandler)));
    actionToolbar = createActionToolbar(table, settingsGroup);
    toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setOpaque(false);

    if (Registry.is("ide.usages.popup.show.options.string")) {
      JLabel optionsLabel = new JLabel(actionHandler.getPresentation().getOptionsString());
      northPanel.add(optionsLabel, gc.next());
    }
    northPanel.add(toolbarComponent, gc.next());

    builder.setNorthComponent(northPanel);

    PopupUpdateProcessor processor = new PopupUpdateProcessor(usageView.getProject()) {
      @Override
      public void updatePopup(Object lookupItemObject) {/*not used*/}
    };
    builder.addListener(processor);

    if (addCodePreview) {
      SimpleColoredComponent previewTitle = new SimpleColoredComponent();
      previewTitle.setBorder(JBUI.Borders.empty(3, 8, 4, 8));
      UsagePreviewPanel usagePreviewPanel = new UsagePreviewPanel(project, usageView.getPresentation(), false) {
        @Override
        public Dimension getPreferredSize() {
          return new Dimension(table.getWidth(), Math.max(getHeight(), getLineHeight() * 5));
        }

        @Override
        public Dimension getMinimumSize() {
          Dimension size = super.getMinimumSize();
          size.height = getLineHeight() * 5;
          return size;
        }
      };

      PropertyChangeListener lineHeightListener = e -> {
        if ((Integer)e.getNewValue() > 0) {
          tableResizer.accept(popupRef.get());
        }
      };

      usagePreviewPanel.addPropertyChangeListener(UsagePreviewPanel.LINE_HEIGHT_PROPERTY, lineHeightListener);

      Disposer.register(contentDisposable, () -> usagePreviewPanel.removePropertyChangeListener(lineHeightListener));
      Disposer.register(contentDisposable, usagePreviewPanel);

      JPanel previewPanel = new JPanel(new BorderLayout());
      previewPanel.add(previewTitle, BorderLayout.NORTH);
      previewPanel.add(usagePreviewPanel.createComponent(), BorderLayout.CENTER);
      contentSplitter.setSecondComponent(previewPanel);

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent event) {
          if (event.getSource() != table) return false;
          itemChoseCallback.run();
          return true;
        }
      }.installOn(table);

      builder.setAutoselectOnMouseMove(false).setCloseOnEnter(false).
        registerKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), __ -> itemChoseCallback.run());

      Runnable updatePreviewRunnable = () -> {
        if (popupRef.get().isDisposed()) return;
        int[] selectedRows = table.getSelectedRows();
        final List<Promise<UsageInfo[]>> selectedUsagePromises = new SmartList<>();
        String file = null;
        for (int row : selectedRows) {
          Object value = table.getModel().getValueAt(row, 0);

          if (value instanceof UsageNode) {
            Usage usage = ((UsageNode)value).getUsage();
            if (usage instanceof UsageInfoAdapter) {
              UsageInfoAdapter adapter = (UsageInfoAdapter)usage;
              file = adapter.getPath();
              if (adapter.isValid()) {
                selectedUsagePromises.add(adapter.getMergedInfosAsync());
              }
            }
          }
        }

        String selectedFile = file;
        Promises.collectResults(selectedUsagePromises).onSuccess(data -> {
          final List<UsageInfo> selectedUsages = new SmartList<>();
          for (UsageInfo[] usageInfos : data) {
            Collections.addAll(selectedUsages, usageInfos);
          }

          usagePreviewPanel.updateLayout(selectedUsages);
          previewTitle.clear();

          if (usagePreviewPanel.getCannotPreviewMessage(selectedUsages) == null && selectedFile != null) {
            previewTitle.append(PathUtil.getFileName(selectedFile), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        });
      };

      Alarm previewUpdater = new Alarm(contentDisposable);

      table.getSelectionModel().addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting() && !previewUpdater.isDisposed()) {
          previewUpdater.addRequest(updatePreviewRunnable, 50);
        }
      });
    }
    else {
      builder.setAutoselectOnMouseMove(true).setItemChoosenCallback(itemChoseCallback).setCloseOnEnter(true);
    }

    AbstractPopup popup = (AbstractPopup)builder.createPopup();
    JComponent content = popup.getContent();
    Disposer.register(popup, contentDisposable);

    // Set title text alignment
    CaptionPanel caption = popup.getTitle();
    if (caption instanceof TitlePanel) {
      TitlePanel titlePanel = (TitlePanel)caption;
      titlePanel.getLabel().setHorizontalAlignment(SwingConstants.LEFT);
      titlePanel.obeyPreferredWidth(WindowStateService.getInstance().getSize(DIMENSION_SERVICE_KEY) == null);
    }

    parameters.minWidth.set(-1);
    for (AnAction action : filteringGroup.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }

    for (AnAction action : pinGroup.getChildren(null)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }
    /* save toolbar actions for using later, in automatic filter toggling in {@link #restartShowUsagesWithFiltersToggled(List} */
    popup.setUserData(addCodePreview ? Arrays.asList(filteringGroup, contentSplitter) : Collections.singletonList(filteringGroup));
    popup.setDataProvider(dataId -> {
      if (UsageView.USAGE_VIEW_SETTINGS_KEY.is(dataId)) {
        return usageView.getUsageViewSettings();
      }
      else {
        return null;
      }
    });
    popupRef.set(popup);
    return popup;
  }

  @NotNull
  private static ActionToolbar createActionToolbar(@NotNull JTable table, @NotNull DefaultActionGroup group) {
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SHOW_USAGES_POPUP_TOOLBAR, group, true);
    actionToolbar.setTargetComponent(table);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    return actionToolbar;
  }

  @NotNull
  private static ActiveComponent createPinButton(@NotNull Project project,
                                                 @NotNull AtomicReference<AbstractPopup> popupRef,
                                                 @NotNull DefaultActionGroup pinGroup,
                                                 @NotNull JTable table,
                                                 @NotNull Runnable findUsagesRunnable) {
    Icon icon = ToolWindowManager.getInstance(project).getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab);
    AnAction pinAction =
      new AnAction(IdeBundle.messagePointer("show.in.find.window.button.name"),
                   IdeBundle.messagePointer("show.in.find.window.button.pin.description"), icon) {
        {
          AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES);
          setShortcutSet(action.getShortcutSet());
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          UsageViewStatisticsCollector.logOpenInFindToolWindow(project);
          hideHints();
          cancel(popupRef.get());
          findUsagesRunnable.run();
        }
      };
    pinGroup.add(pinAction);
    ActionToolbar pinToolbar = createActionToolbar(table, pinGroup);
    JComponent pinToolBar = pinToolbar.getComponent();
    pinToolBar.setBorder(null);
    pinToolBar.setOpaque(false);

    return new ActiveComponent.Adapter() {
      @NotNull
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

  private static @Nls @NotNull String getStatusString(boolean findUsagesInProgress, boolean hasMore, int visibleCount, int totalCount) {
    if (findUsagesInProgress || hasMore) {
      return UsageViewBundle.message("showing.0.usages", visibleCount - (hasMore ? 1 : 0));
    }
    else if (visibleCount != totalCount) {
      return UsageViewBundle.message("showing.0.of.1.usages", visibleCount, totalCount);
    }
    else {
      return UsageViewBundle.message("found.0.usages", totalCount);
    }
  }

  private static @Nls @NotNull String suggestSecondInvocation(@Nls(capitalization = Sentence) @NotNull String text,
                                                              @Nls(capitalization = Sentence) @Nullable String hint) {
    HtmlBuilder builder = new HtmlBuilder().append(text);
    if (hint != null) {
      builder.br().append(HtmlChunk.text(hint).wrapWith("small"));
    }
    return builder.wrapWithHtmlBody().toString();
  }

  @Nullable
  static KeyboardShortcut getShowUsagesShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut(ID);
  }

  private static int getFilteredOutNodeCount(@NotNull List<? extends Usage> usages, @NotNull UsageViewImpl usageView) {
    return (int)usages.stream().filter(usage -> !usageView.isVisible(usage)).count();
  }

  private static int getUsageOffset(@NotNull Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) return -1;
    PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
    if (element == null) return -1;
    return element.getTextRange().getStartOffset();
  }

  private static boolean areAllUsagesInOneLine(@NotNull Usage visibleUsage, @NotNull List<? extends Usage> usages) {
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

  private static int calcMaxWidth(@NotNull JTable table) {
    int colsNum = table.getColumnModel().getColumnCount();

    int totalWidth = 0;
    for (int col = 0; col < colsNum - 1; col++) {
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

  private static void rebuildTable(@NotNull Project project,
                                   @NotNull Predicate<? super Usage> originUsageCheck,
                                   @NotNull List<UsageNode> data,
                                   @NotNull ShowUsagesTable table,
                                   @Nullable AbstractPopup popup,
                                   @NotNull RelativePoint popupPosition,
                                   @NotNull IntRef minWidth,
                                   @NotNull AtomicBoolean manuallyResized) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    ShowUsagesTable.MyModel tableModel = table.setTableModel(data);
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
        if (valueInTopRow instanceof UsageNode && originUsageCheck.test(((UsageNode)valueInTopRow).getUsage())) {
          newSelection++;
        }
      }
      table.getSelectionModel().setSelectionInterval(newSelection, newSelection);
    }
    ScrollingUtil.ensureIndexIsVisible(table, newSelection, 0);

    if (popup != null) {
      if (manuallyResized.get()) {
        calcMaxWidth(table); // compute column widths
      }
      else {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        setPopupSize(table, popup, popupPosition, minWidth, properties.isValueSet(PREVIEW_PROPERTY_KEY), data.size());
      }
    }
  }

  // returns new selection
  private static int updateModel(@NotNull ShowUsagesTable.MyModel tableModel,
                                 @NotNull List<? extends UsageNode> listOld,
                                 @NotNull List<? extends UsageNode> listNew,
                                 int oldSelection) {
    UsageNode[] oa = listOld.toArray(new UsageNode[0]);
    UsageNode[] na = listNew.toArray(new UsageNode[0]);
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

  private static void setPopupSize(@NotNull JTable table,
                                   @NotNull AbstractPopup popup,
                                   @NotNull RelativePoint popupPosition,
                                   @NotNull IntRef minWidth,
                                   boolean showCodePreview,
                                   int dataSize) {

    if (isCodeWithMeClientInstance(popup)) return;

    Dimension d = popup.getSize();
    Insets contentInsets = popup.getContent().getInsets();
    JBInsets.removeFrom(d, contentInsets);

    Component toolbarComponent = ((BorderLayout)popup.getComponent().getLayout()).getLayoutComponent(BorderLayout.NORTH);
    Dimension toolbarSize = toolbarComponent != null ? toolbarComponent.getPreferredSize() : JBUI.emptySize();
    Dimension headerSize = popup.getHeaderPreferredSize();

    int width = Math.max(d.width, calcMaxWidth(table));
    width = Math.max(Math.max(headerSize.width, width), toolbarSize.width);
    width = Math.max(minWidth.get(), width);

    int delta = minWidth.get() == -1 ? 0 : width - minWidth.get();
    int newWidth = Math.max(width, d.width + delta);

    minWidth.set(newWidth);

    int minHeight = headerSize.height + toolbarSize.height;

    Rectangle rectangle = getPreferredBounds(table, popupPosition.getScreenPoint(), newWidth, minHeight, dataSize, showCodePreview);
    table.setSize(rectangle.width, rectangle.height - minHeight);
    if (dataSize > 0) ScrollingUtil.ensureSelectionExists(table);

    Dimension savedSize = WindowStateService.getInstance().getSize(DIMENSION_SERVICE_KEY);
    JBSplitter splitter = popup.getUserData(JBSplitter.class);

    if (savedSize != null) {
      rectangle.width = Math.min(savedSize.width, rectangle.width);
    }

    if (splitter != null) {
      int newHeight = rectangle.height + splitter.getDividerWidth() + splitter.getSecondComponent().getMinimumSize().height;
      if (savedSize != null) {
        savedSize.height -= popup.getAdComponentHeight();
        newHeight = Math.max(newHeight, savedSize.height);
      }

      rectangle.height = newHeight;
    }

    popup.setSize(rectangle.getSize());
  }

  private static boolean isCodeWithMeClientInstance(@NotNull JBPopup popup) {
    JComponent content = popup.getContent();
    return content.getClientProperty("THIN_CLIENT") != null;
  }

  @NotNull
  private static Rectangle getPreferredBounds(@NotNull JTable table, @NotNull Point point, int width, int minHeight, int modelRows,
                                              boolean showCodePreview) {
    boolean addExtraSpace = Registry.is("ide.preferred.scrollable.viewport.extra.space");
    int visibleRows = Math.min(showCodePreview ? 20 : 30, modelRows);
    int rowHeight = table.getRowHeight();
    int space = addExtraSpace && visibleRows < modelRows ? rowHeight / 2 : 0;
    int height = visibleRows * rowHeight + minHeight + space;
    Rectangle bounds = new Rectangle(point.x, point.y, width, height);
    ScreenUtil.fitToScreen(bounds);
    if (bounds.height != height) {
      minHeight += addExtraSpace && space == 0 ? rowHeight / 2 : space;
      bounds.height = Math.max(1, (bounds.height - minHeight) / rowHeight) * rowHeight + minHeight;
    }
    return bounds;
  }

  private static void addUsageNodes(@NotNull GroupNode root, @NotNull UsageViewImpl usageView, @NotNull List<? super UsageNode> outNodes) {
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

  private static void navigateAndHint(@NotNull Usage usage,
                                      @Nls(capitalization = Sentence) @NotNull String hint,
                                      @NotNull ShowUsagesParameters parameters,
                                      @NotNull ShowUsagesActionHandler actionHandler) {
    usage.navigate(true);
    Editor newEditor = getEditorFor(usage);
    if (newEditor == null) return;
    hint(false, hint, parameters.withEditor(newEditor), actionHandler);
  }

  private static void hint(boolean isWarning,
                           @Nls(capitalization = Sentence) @NotNull String hint,
                           @NotNull ShowUsagesParameters parameters,
                           @NotNull ShowUsagesActionHandler actionHandler) {
    Project project = parameters.project;
    Editor editor = parameters.editor;

    Runnable runnable = () -> {
      if (!actionHandler.isValid()) {
        return;
      }
      JComponent label = createHintComponent(
        suggestSecondInvocation(hint, getSecondInvocationHint(actionHandler)),
        isWarning,
        createSettingsButton(
          project,
          ShowUsagesAction::hideHints,
          showDialogAndRestartRunnable(parameters, actionHandler)
        )
      );

      ShowUsagesActionState state = getState(project);
      state.continuation = showUsagesInMaximalScopeRunnable(parameters, actionHandler);
      Runnable clearContinuation = () -> state.continuation = null;

      if (editor == null || editor.isDisposed() || !UIUtil.isShowing(editor.getContentComponent())) {
        int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
        HintManager.getInstance().showHint(label, parameters.popupPosition, flags, 0, clearContinuation);
      }
      else {
        HintManager.getInstance().showInformationHint(editor, label, clearContinuation);
      }
    };

    if (editor == null) {
      //opening editor is performing in invokeLater
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(() -> {
        // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(runnable);
      });
    }
    else {
      //opening editor is performing in invokeLater
      IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
        () -> editor.getScrollingModel().runActionOnScrollingFinished(
          () -> {
            // after new editor created, some editor resizing events are still bubbling. To prevent hiding hint, invokeLater this
            IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(
              () -> AsyncEditorLoader.performWhenLoaded(editor, runnable)
            );
          })
      );
    }
  }

  @Nullable
  private static Editor getEditorFor(@NotNull Usage usage) {
    FileEditorLocation location = usage.getLocation();
    FileEditor newFileEditor = location == null ? null : location.getEditor();
    return newFileEditor instanceof TextEditor ? ((TextEditor)newFileEditor).getEditor() : null;
  }

  static final class StringNode extends UsageNode {
    private final @Nls @NotNull String myString;

    private StringNode(@Nls @NotNull String string) {
      super(null, NullUsage.INSTANCE);
      myString = string;
    }

    @Nls @NotNull String getString() {
      return myString;
    }

    @Override
    public String toString() {
      return myString;
    }
  }

  private static class SettingsAction extends DumbAwareAction implements CustomComponentAction {
    private final Project project;
    private final Runnable cancelAction;
    private final Runnable showDialogAction;

    private SettingsAction(@NotNull Project project, @NotNull Runnable cancelAction, @NotNull Runnable showDialogAction) {
      super(FindBundle.message("show.usages.settings.tooltip"), null, AllIcons.General.GearPlain);
      this.project = project;
      this.cancelAction = cancelAction;
      this.showDialogAction = showDialogAction;
      //getTemplatePresentation().setHoveredIcon(AllIcons.General.GearHover);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(showDialogAction, project.getDisposed());
      cancelAction.run();
    }

    @Override
    @NotNull
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return new ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        protected @Nullable String getShortcutText() {
          KeyboardShortcut shortcut = UsageViewImpl.getShowUsagesWithSettingsShortcut();
          return shortcut != null ? KeymapUtil.getShortcutText(shortcut) : null;
        }
      };
    }
  }

  static abstract class FilteredOutUsagesNode extends UsageNode {

    private final @Nls @NotNull String myString;
    private final @Nls @NotNull String myToolTip;

    private FilteredOutUsagesNode(@NotNull Usage fakeUsage, @Nls @NotNull String string, @Nls @NotNull String toolTip) {
      super(null, fakeUsage);
      myString = string;
      myToolTip = toolTip;
    }

    @Nls @NotNull String getString() {
      return myString;
    }

    @Nls @NotNull String getTooltip() {
      return myToolTip;
    }

    @Override
    public String toString() {
      return myString;
    }

    public abstract void onSelected();
  }

  private static @NotNull Runnable showMoreUsagesRunnable(@NotNull ShowUsagesParameters parameters,
                                                          @NotNull ShowUsagesActionHandler actionHandler) {
    return () -> showElementUsages(parameters.moreUsages(), actionHandler);
  }

  private static @NotNull Runnable showUsagesInMaximalScopeRunnable(@NotNull ShowUsagesParameters parameters,
                                                                    @NotNull ShowUsagesActionHandler actionHandler) {
    return () -> showUsagesInMaximalScope(parameters, actionHandler);
  }

  private static void showUsagesInMaximalScope(@NotNull ShowUsagesParameters parameters,
                                               @NotNull ShowUsagesActionHandler actionHandler) {
    showElementUsages(parameters, actionHandler.withScope(actionHandler.getMaximalScope()));
  }

  private static @NotNull Runnable showDialogAndRestartRunnable(@NotNull ShowUsagesParameters parameters,
                                                                @NotNull ShowUsagesActionHandler actionHandler) {
    return () -> showDialogAndRestart(parameters, actionHandler);
  }

  private static void showDialogAndRestart(@NotNull ShowUsagesParameters parameters,
                                           @NotNull ShowUsagesActionHandler actionHandler) {
    ShowUsagesActionHandler newActionHandler = actionHandler.showDialog();
    if (newActionHandler != null) {
      showElementUsages(parameters, newActionHandler);
    }
  }

  @Service
  private static final class ShowUsagesActionState {
    Runnable continuation;
  }

  @NotNull
  private static ShowUsagesActionState getState(@NotNull Project project) {
    return project.getService(ShowUsagesActionState.class);
  }

  @TestOnly
  public static void setPopupDelayTimeout(int timeout) {
    ourPopupDelayTimeout = timeout;
  }
}

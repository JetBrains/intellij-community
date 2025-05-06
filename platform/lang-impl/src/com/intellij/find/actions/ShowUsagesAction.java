// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindUsagesSettings;
import com.intellij.find.findUsages.*;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.find.impl.UsageAdaptersKt;
import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.impl.Psi2UsageInfo2UsageAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.gotoByName.ModelDiff;
import com.intellij.ide.util.scopeChooser.ScopeChooserGroup;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.ObjectEventData;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.psi.*;
import com.intellij.psi.search.FileRankerMlService;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
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
import com.intellij.usages.rules.UsageInFile;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.ScreenReader;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.find.actions.ResolverKt.findShowUsages;
import static com.intellij.find.actions.SearchOptionsService.SearchVariant.SHOW_USAGES;
import static com.intellij.find.actions.ShowUsagesActionHandler.getSecondInvocationHint;
import static com.intellij.find.findUsages.FindUsagesHandlerFactory.OperationMode.USAGES_WITH_DEFAULT_OPTIONS;
import static com.intellij.usageView.UsageViewUtil.FIND_OPTIONS_HREF_TARGET;
import static com.intellij.util.FindUsagesScopeKt.FindUsagesScope;
import static com.intellij.util.ObjectUtils.doIfNotNull;
import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public final class ShowUsagesAction extends AnAction implements PopupAction, HintManagerImpl.ActionToIgnore {
  public static final String ID = "ShowUsages";

  @ApiStatus.Internal
  public static final String CLOSE_REASON_PREVIEW = "Preview";
  @ApiStatus.Internal
  public static final String CLOSE_REASON_CHANGE_SCOPE = "ChangeScope";
  @ApiStatus.Internal
  public static final String CLOSE_REASON_RESET_FILTERS = "ResetFilters";

  private static final String DIMENSION_SERVICE_KEY = "ShowUsagesActions.dimensionServiceKey";
  private static final String SPLITTER_SERVICE_KEY = "ShowUsagesActions.splitterServiceKey";
  private static final String PREVIEW_PROPERTY_KEY = "ShowUsagesActions.previewPropertyKey";

  private static final IJTracer myFindUsagesTracer = TelemetryManager.getInstance().getTracer(FindUsagesScope);

  private static int ourPopupDelayTimeout = 300;

  public ShowUsagesAction() {
    setInjectedContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
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

      return UsageViewImpl.USAGE_COMPARATOR_BY_FILE_AND_OFFSET.compare(o1, o2);
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
    WriteIntentReadAction.run((Runnable) () -> {
      performShowUsagesAction(e);
    });
  }

  @ApiStatus.Internal
  public static void performShowUsagesAction(@NotNull AnActionEvent e) {
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
    DataContext dataContext = e.getDataContext();
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    RelativePoint popupPosition = editor != null
                                  ? popupFactory.guessBestPopupLocation(editor)
                                  : popupFactory.guessBestPopupLocation(dataContext);
    SearchScope searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName());
    ReadAction.nonBlocking(() -> ResolverKt.allTargets(dataContext))
      .expireWith(project).finishOnUiThread(ModalityState.nonModal(), variants -> showUsages(project, variants, popupPosition, editor, searchScope))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @ApiStatus.Internal
  public static void showUsages(@NotNull Project project,
                                @NotNull List<? extends @NotNull TargetVariant> targetVariants,
                                RelativePoint popupPosition,
                                @Nullable Editor editor,
                                SearchScope searchScope) {
    try (AccessToken ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      findShowUsages(
        project, editor, popupPosition, targetVariants, FindBundle.message("show.usages.ambiguous.title"),
        createVariantHandler(project, editor, popupPosition, searchScope)
      );
    }
  }

  private static @NotNull UsageVariantHandler createVariantHandler(@NotNull Project project,
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
    SearchScope searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName());
    ShowTargetUsagesActionHandler.showUsages(
      project, searchScope, target,
      ShowUsagesParameters.initial(project, editor, popupPosition)
    );
  }

  static void hideHints() {
    HintManager.getInstance().hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
  }

  @ApiStatus.Internal
  public static Future<Collection<Usage>> startFindUsagesWithResult(@NotNull PsiElement element,
                                                                    @NotNull RelativePoint popupPosition,
                                                                    @Nullable Editor editor,
                                                                    @Nullable SearchScope scope) {
    return startFindUsagesWithResult(element, popupPosition, editor, scope, getUsagesTitle(element));
  }

  @ApiStatus.Internal
  public static Future<Collection<Usage>> startFindUsagesWithResult(@NotNull PsiElement element,
                                                                    @NotNull RelativePoint popupPosition,
                                                                    @Nullable Editor editor,
                                                                    @Nullable SearchScope scope,
                                                                    @Nls @NotNull String title) {
    Project project = element.getProject();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    FindUsagesHandlerBase handler;
    ShowUsagesActionHandler actionHandler;
    FindUsagesOptions options;
    try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
      handler = findUsagesManager.getFindUsagesHandler(element, USAGES_WITH_DEFAULT_OPTIONS);
      if (handler == null) return null;
      //noinspection deprecation
      DataContext dataContext = DataManager.getInstance().getDataContext();
      options = handler.getFindUsagesOptions(dataContext);
      if (options instanceof PersistentFindUsagesOptions) {
        ((PersistentFindUsagesOptions)options).setDefaults(project);
      }
      if (scope != null) {
        options.searchScope = scope;
      }
      else {
        options.searchScope = FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName());
      }
      actionHandler = createActionHandler(handler, options, title);
    }
    return showElementUsagesWithResult(ShowUsagesParameters.initial(project, editor, popupPosition), actionHandler);
  }

  @ApiStatus.Internal
  public static @NotNull Future<Collection<Usage>> startFindUsagesWithResult(@NotNull Project project,
                                                                             @NotNull SearchTarget target,
                                                                             @NotNull RelativePoint popupPosition,
                                                                             @Nullable Editor editor,
                                                                             @NotNull SearchScope searchScope) {
    return showElementUsagesWithResult(ShowUsagesParameters.initial(project, editor, popupPosition), createActionHandler(project, searchScope, target));
  }

  public static void startFindUsages(@NotNull PsiElement element, @NotNull RelativePoint popupPosition, @Nullable Editor editor) {
    ReadAction.nonBlocking(() -> getUsagesTitle(element))
      .expireWhen(() -> editor != null && editor.isDisposed())
      .finishOnUiThread(ModalityState.nonModal(), title -> startFindUsagesWithResult(element, popupPosition, editor, null, title))
      .submit(AppExecutorUtil.getAppExecutorService());
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

    HtmlChunk type = HtmlChunk.text(StringUtil.capitalize(UsageViewUtil.getType(element)));
    if (ExperimentalUI.isNewUI()) {
      type = type.bold();
    }

    builder.append(type).nbsp().
      append(HtmlChunk.text(UsageViewUtil.getLongName(element)).bold());

    if (element instanceof NavigationItem) {
      ItemPresentation itemPresentation = ((NavigationItem)element).getPresentation();
      if (itemPresentation != null && StringUtil.isNotEmpty(itemPresentation.getLocationString())) {
        builder.nbsp().append(getLocationString(itemPresentation.getLocationString()));
      }
    }

    return builder.toString();
  }

  private static @NotNull HtmlChunk getLocationString(@Nls @NotNull String locationString) {
    Color color = ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.ContextHelp.FOREGROUND : SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor();
    return HtmlChunk.text(locationString).
      wrapWith("font").attr("color", "#" + ColorUtil.toHex(color));
  }

  private static @NotNull ShowUsagesActionHandler createActionHandler(@NotNull FindUsagesHandlerBase handler,
                                                                      @NotNull FindUsagesOptions options,
                                                                      @Nls @NotNull String title) {
    // show super method warning dialogs before starting finding usages
    PsiElement[] primaryElements = handler.getPrimaryElements();
    PsiElement[] secondaryElements = handler.getSecondaryElements();

    String optionsString = options.generateUsagesString();

    return new ShowUsagesActionHandler() {
      @Override
      public boolean isValid() {
        return handler.getPsiElement().isValid();
      }

      @Override
      public @NotNull UsageSearchPresentation getPresentation() {
        return new UsageSearchPresentation() {
          @Override
          public @Nls @NotNull String getSearchTargetString() {
            return title;
          }

          @Override
          public @Nls @NotNull String getOptionsString() {
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
          return createActionHandler(handler, newOptions, title);
        }
      }

      @Override
      public void findUsages() {
        Project project = handler.getProject();
        FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
        findUsagesManager.findUsages(
          handler.getPrimaryElements(), handler.getSecondaryElements(),
          handler, options,
          FindUsagesSettings.getInstance().isSkipResultsWithOneUsage()
        );
      }

      @Override
      public @NotNull ShowUsagesActionHandler withScope(@NotNull SearchScope searchScope) {
        return withScope(searchScope, false);
      }

      private @NotNull ShowUsagesActionHandler withScope(@NotNull SearchScope searchScope, boolean isMaximalScope) {
        FindUsagesOptions newOptions = options.clone();
        newOptions.searchScope = searchScope;
        newOptions.isMaximalScope = isMaximalScope;
        return createActionHandler(handler, newOptions, title);
      }

      @Override
      public @Nullable ShowUsagesParameters moreUsages(@NotNull ShowUsagesParameters parameters) {
        return parameters.moreUsages();
      }

      @Override
      public Language getTargetLanguage() {
        return handler.getPsiElement().getLanguage();
      }

      @Override
      public @NotNull Class<?> getTargetClass() {
        return handler.getPsiElement().getClass();
      }

      @Override
      public @NotNull List<EventPair<?>> getEventData() {
        List<EventPair<?>> eventData = new ArrayList<>();
        PsiElement target = handler.getPsiElement();
        final ObjectEventData targetElementData = UsageViewStatisticsCollector.calculateElementData(target);
        if (targetElementData != null) {
          eventData.add(UsageViewStatisticsCollector.TARGET_ELEMENT_DATA.with(targetElementData));
        }
        eventData.add(UsageViewStatisticsCollector.NUMBER_OF_TARGETS.with(primaryElements.length + secondaryElements.length));
        return eventData;
      }

      @Override
      public @NotNull List<EventPair<?>> buildFinishEventData(@Nullable UsageInfo selectedUsage) {
        List<EventPair<?>> eventData = getEventData();
        PsiElement target = handler.getPsiElement();
        PsiElement selectedUsagesElement = selectedUsage != null ? selectedUsage.getElement() : null;
        if (selectedUsagesElement != null) {
          PsiFile containingFile = selectedUsagesElement.getContainingFile();
          PsiManager manager = selectedUsagesElement.getManager();
          eventData.add(UsageViewStatisticsCollector.IS_THE_SAME_FILE.with(
            manager != null && manager.areElementsEquivalent(target.getContainingFile(), containingFile)));
          ObjectEventData usageElementData = UsageViewStatisticsCollector.calculateElementData(selectedUsagesElement);
          if (usageElementData != null) {
            eventData.add(UsageViewStatisticsCollector.SELECTED_ELEMENT_DATA.with(usageElementData));
          }
          eventData.add(UsageViewStatisticsCollector.IS_SELECTED_ELEMENT_AMONG_RECENT_FILES.with(
            ContainerUtil.exists(ContainerUtil.reverse(EditorHistoryManager.getInstance(target.getProject()).getFileList()),
                                 e -> e.equals(containingFile.getVirtualFile()))));
        }
        return eventData;
      }

      @Override
      public boolean navigateToSingleUsageImmediately() {
        return true;
      }
    };
  }

  private static @NotNull ShowUsagesActionHandler createActionHandler(@NotNull Project project, @NotNull SearchScope searchScope, @NotNull SearchTarget target) {
    return new ShowTargetUsagesActionHandler(project, target, SearchOptionsServiceKt.getSearchOptions(SHOW_USAGES, target, searchScope));
  }

  static void showElementUsages(@NotNull ShowUsagesParameters parameters, @NotNull ShowUsagesActionHandler actionHandler) {
    showElementUsagesWithResult(parameters, actionHandler);
  }

  private static @NotNull Future<Collection<Usage>> showElementUsagesWithResult(@NotNull ShowUsagesParameters parameters,
                                                                                @NotNull ShowUsagesActionHandler actionHandler) {
    ThreadingAssertions.assertEventDispatchThread();
    Project project = parameters.project;
    UsageViewImpl usageView = actionHandler.createUsageView(project);
    return ShowUsagesManager.getInstance(project).showElementUsagesWithResult(parameters, actionHandler, usageView);
  }

  @ApiStatus.Internal
  private static Editor getEditorFor(Usage usage) {
    FileEditorLocation location = usage.getLocation();
    if (location == null) {
      return null;
    }
    FileEditor newFileEditor = location.getEditor();
    if (newFileEditor instanceof TextEditor fileEditor) {
      return fileEditor.getEditor();
    }
    return null;
  }

  public static Future<Collection<Usage>> showElementUsagesWithResult(@NotNull ShowUsagesParameters parameters,
                                                                      @NotNull ShowUsagesActionHandler actionHandler,
                                                                      @NotNull UsageViewImpl usageView) {
    Span findUsageSpan = myFindUsagesTracer.spanBuilder("findUsages").startSpan();
    Scope opentelemetryScope = findUsageSpan.makeCurrent();
    Span popupSpan = myFindUsagesTracer.spanBuilder("findUsage_popup").startSpan();
    Span firstUsageSpan = myFindUsagesTracer.spanBuilder("findUsages_firstUsage").startSpan();

    Project project = parameters.project;
    ReadAction.nonBlocking(() -> actionHandler.getEventData()).submit(AppExecutorUtil.getAppExecutorService()).onSuccess(
      (eventData) -> UsageViewStatisticsCollector.logSearchStarted(project, usageView, CodeNavigateSource.ShowUsagesPopup, eventData));
    final SearchScope searchScope = actionHandler.getSelectedScope();
    final AtomicInteger outOfScopeUsages = new AtomicInteger();
    AtomicBoolean manuallyResized = new AtomicBoolean();
    Ref<UsageNode> preselectedRow = new Ref<>();

    Predicate<? super Usage> originUsageCheck;
    try (AccessToken ignore = SlowOperations.knownIssue("IJPL-162330")) {
      originUsageCheck = originUsageCheck(parameters.editor);
    }
    ShowUsagesTableCellRenderer renderer = new ShowUsagesTableCellRenderer(originUsageCheck, outOfScopeUsages, searchScope);
    ShowUsagesTable table = new ShowUsagesTable(renderer, usageView);

    addUsageNodes(usageView.getRoot(), new ArrayList<>());

    List<Usage> usages = new ArrayList<>();
    Set<Usage> visibleUsages = new LinkedHashSet<>();
    table.setTableModel(new SmartList<>(new StringNode(UsageViewBundle.message("progress.searching"))));

    ShowUsagesPopupData showUsagesPopupData = new ShowUsagesPopupData(parameters, table, actionHandler, usageView);

    Runnable itemChosenCallback = table.prepareTable(
      showMoreUsagesRunnable(parameters, actionHandler),
      showUsagesInMaximalScopeRunnable(parameters, actionHandler, showUsagesPopupData),
      actionHandler, parameters
    );

    Consumer<AbstractPopup> tableResizer = popup -> {
      if (popup != null && popup.isVisible() && !manuallyResized.get()) {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        int dataSize = table.getModel().getRowCount();
        setPopupSize(table, popup, parameters.popupPosition, parameters.minWidth, properties.isValueSet(PREVIEW_PROPERTY_KEY), dataSize);
      }
    };

    AbstractPopup popup = createUsagePopup(usageView, showUsagesPopupData, itemChosenCallback, tableResizer);
    popup.addResizeListener(() -> manuallyResized.set(true), popup);
    Ref<Long> popupShownTimeRef = new Ref<>();
    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        @Nullable UsageNode node = getSelectedUsageNode(table);
        Usage usage = node != null ? node.getUsage() : null;
        UsageInfo2UsageAdapter usageAdapter = ObjectUtils.tryCast(usage, UsageInfo2UsageAdapter.class);
        UsageInfo usageInfo = usageAdapter != null ? usageAdapter.getUsageInfo() : null;
        int preselectedRowNumber = getRowNumber(preselectedRow.get(), table);
        int selectedRowNumber = getRowNumber(node, table);
        Long popupShownTime = popupShownTimeRef.get();
        Long durationTime = popupShownTime != null ? System.currentTimeMillis() - popupShownTime : null;
        ReadAction.nonBlocking(() ->
                                 actionHandler.buildFinishEventData(usageInfo)
        ).submit(AppExecutorUtil.getAppExecutorService()).onSuccess((finishEventData) -> {
          UsageViewStatisticsCollector.logPopupClosed(project, usageView, event.isOk(),
                                                      preselectedRowNumber,
                                                      selectedRowNumber, visibleUsages.size(),
                                                      durationTime,
                                                      finishEventData
          );
        });
      }
    });
    ProgressIndicator indicator = new ProgressIndicatorBase();
    if (!popup.isDisposed()) {
      Disposer.register(popup, usageView);
      Disposer.register(popup, indicator::cancel);

      // show popup only if find usages takes more than 300ms, otherwise it would flicker needlessly
      EdtScheduler.getInstance().schedule(ourPopupDelayTimeout, () -> {
        if (!usageView.isDisposed()) {
          showPopupIfNeedTo(popup, parameters.popupPosition, popupShownTimeRef);
          popupSpan.end();
        }
      });
    }

    UsageNode USAGES_OUTSIDE_SCOPE_NODE = new UsageNode(null, table.USAGES_OUTSIDE_SCOPE_SEPARATOR);
    UsageNode MORE_USAGES_SEPARATOR_NODE = new UsageNode(null, table.MORE_USAGES_SEPARATOR);

    Runnable runnable = () -> {
      if (popup.isDisposed()) return;

      List<UsageNode> nodes = new ArrayList<>(usages.size());
      List<Usage> copy;
      synchronized (usages) {
        // open up popup as soon as the first usage has been found
        if (!popup.isVisible() && (usages.isEmpty() || !showPopupIfNeedTo(popup, parameters.popupPosition, popupShownTimeRef))) {
          return;
        }
        addUsageNodes(usageView.getRoot(), nodes);
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
      int filteredOutCount = usageView.getFilteredOutNodeCount();
      if (filteredOutCount != 0) {
        DefaultActionGroup filteringActions = popup.getUserData(DefaultActionGroup.class);
        if (filteringActions == null) return;

        List<ToggleAction> unselectedActions = Arrays.stream(filteringActions.getChildren(ActionManager.getInstance()))
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
            actionHandler.beforeClose(CLOSE_REASON_RESET_FILTERS);

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
      showUsagesPopupData.header.setStatusText(hasMore, visibleCount, totalCount);
      rebuildTable(project, originUsageCheck, data, table, popup, parameters.popupPosition, parameters.minWidth, manuallyResized);
      preselectedRow.set(getSelectedUsageNode(table));
    };
    PingEDT pingEDT = new PingEDT("Rebuild popup in EDT", () -> popup.isDisposed(), 100, runnable);

    MessageBusConnection messageBusConnection = project.getMessageBus().connect(usageView);
    messageBusConnection.subscribe(UsageFilteringRuleProvider.RULES_CHANGED, () -> rulesChanged(usageView, pingEDT, popup));

    AtomicLong firstUsageAddedTS = new AtomicLong();
    AtomicBoolean tooManyResults = new AtomicBoolean();
    GlobalSearchScope everythingScope = GlobalSearchScope.everythingScope(project);
    Processor<Usage> collect = usage -> {
      if (!UsageViewManagerImpl.isInScope(usage, searchScope, everythingScope)) {
        if (outOfScopeUsages.getAndIncrement() == 0) {
          visibleUsages.add(USAGES_OUTSIDE_SCOPE_NODE.getUsage());
          usages.add(table.USAGES_OUTSIDE_SCOPE_SEPARATOR);
        }
        return true;
      }
      synchronized (usages) {
        firstUsageSpan.end();
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
    CompletableFuture<Collection<Usage>> result = new CompletableFuture<>();
    FindUsagesManager.startProcessUsages(indicator, project, usageSearcher, collect, () -> ApplicationManager.getApplication().invokeLater(
      () -> {
        showUsagesPopupData.header.disposeProcessIcon();
        pingEDT.ping(); // repaint status
        synchronized (usages) {
          findUsageSpan.setAttribute("number", usages.size());
          if (visibleUsages.isEmpty()) {
            if (usages.isEmpty()) {
              String hint = UsageViewBundle.message("no.usages.found.in", searchScope.getDisplayName());
              cancelAndShowHint(popup, false, hint, parameters, actionHandler);
            }
            // else all usages filtered out
          }
          else if (visibleUsages.size() == 1 && actionHandler.navigateToSingleUsageImmediately()) {
            final BiConsumer<Usage, String> onReady = (Usage usage, @Nls String hint) -> {
              var newEditor = getEditorFor(usage);
              if (newEditor != null && parameters.editor != null) {
                cancelAndShowHint(popup, false, hint, parameters, actionHandler);
              }
              else {
                cancel(popup);
              }
            };

            if (usages.size() == 1) {
              //the only usage
              Usage usage = visibleUsages.iterator().next();
              if (usage == table.USAGES_OUTSIDE_SCOPE_SEPARATOR) {
                String hint = UsageViewManagerImpl.outOfScopeMessage(outOfScopeUsages.get(), searchScope);
                cancelAndShowHint(popup, true, hint, parameters, actionHandler);
              }
              else {
                String hint = UsageViewBundle.message("show.usages.only.usage", searchScope.getDisplayName());
                UsageNavigation.getInstance(project).navigateAndHint(
                  project, usage, () -> onReady.accept(usage, hint), parameters.editor);
              }
            }
            else {
              assert usages.size() > 1 : usages;
              // usage view can filter usages down to one
              Usage visibleUsage = visibleUsages.iterator().next();
              if (areAllUsagesInOneLine(visibleUsage, usages)) {
                String hint = UsageViewBundle.message("all.usages.are.in.this.line", usages.size(), searchScope.getDisplayName());
                UsageNavigation.getInstance(project).navigateAndHint(
                  project, visibleUsage, () -> onReady.accept(visibleUsage, hint), parameters.editor);
              }
            }
          }
          result.complete(usages);
        }
        findUsageSpan.end();
        long current = System.nanoTime();
        long firstUsageTimestamp = firstUsageAddedTS.get();
        long durationFirstResults;
        if (firstUsageTimestamp != 0) {
          durationFirstResults = TimeUnit.NANOSECONDS.toMillis(firstUsageTimestamp - searchStarted);
        }
        else { // firstUsageTimestamp == 0 means that no usage was found.
          durationFirstResults = -1;
        }
        UsageViewManagerImpl.informRankerMlService(project, usages, FileRankerMlService.CallSource.SHOW_USAGES);
        UsageViewStatisticsCollector.logSearchFinished(project, usageView,
                                                       actionHandler.getTargetClass(), searchScope, actionHandler.getTargetLanguage(),
                                                       visibleUsages.size(),
                                                       durationFirstResults,
                                                       TimeUnit.NANOSECONDS.toMillis(current - searchStarted),
                                                       tooManyResults.get(),
                                                       indicator.isCanceled(),
                                                       CodeNavigateSource.ShowUsagesPopup);
      },
      project.getDisposed()
    ));
    opentelemetryScope.close();
    actionHandler.afterOpen(popup);
    return result;
  }

  private static void toggleFilters(@NotNull List<? extends ToggleAction> unselectedActions) {
    for (ToggleAction action : unselectedActions) {
      action.actionPerformed(fakeEvent(action));
    }
  }

  private static @NotNull AnActionEvent fakeEvent(@NotNull ToggleAction action) {
    return AnActionEvent.createEvent(
      DataContext.EMPTY_CONTEXT,
      action.getTemplatePresentation().clone(), ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
  }

  private static @NotNull Predicate<? super Usage> originUsageCheck(@Nullable Editor editor) {
    if (editor != null) {
      PsiReference reference = TargetElementUtil.findReference(editor);
      if (reference != null) {
        UsageInfo originUsageInfo = new UsageInfo(reference);
        return usage -> usage instanceof UsageInfo2UsageAdapter &&
                        ((UsageInfo2UsageAdapter)usage).getUsageInfo().equals(originUsageInfo);
      }

      VirtualFile file = editor.getVirtualFile();
      int offset = editor.getCaretModel().getOffset();
      if (file == null || offset <= 0) {
        return __ -> false;
      }

      int line = editor.getDocument().getLineNumber(offset);
      return usage -> {
        if (usage instanceof Psi2UsageInfo2UsageAdapter adapter) {
          if (line != adapter.getLine() || !file.equals(adapter.getFile())) {
            return false;
          }

          for (UsageInfo info : adapter.getMergedInfos()) {
            Segment range = doIfNotNull(info.getPsiFileRange(), it -> ReadAction.compute(it::getRange));
            if (range != null && range.containsInclusive(offset)) {
              return true;
            }
          }
        }

        return false;
      };
    }
    return __ -> false;
  }

  private static boolean showPopupIfNeedTo(@NotNull JBPopup popup, @NotNull RelativePoint popupPosition, @NotNull Ref<? super Long> popupShownTime) {
    if (!popup.isDisposed() && !popup.isVisible() && popup.canShow()) {
      popup.show(popupPosition);
      popupShownTime.set(System.currentTimeMillis());
      return true;
    }
    return false;
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

  private static @NotNull AbstractPopup createUsagePopup(@NotNull UsageViewImpl usageView,
                                                         @NotNull ShowUsagesPopupData showUsagesPopupData,
                                                         @NotNull Runnable itemChoseCallback,
                                                         @NotNull Consumer<? super AbstractPopup> tableResizer) {
    ThreadingAssertions.assertEventDispatchThread();

    @NotNull JTable table = showUsagesPopupData.table;
    @NotNull AtomicReference<AbstractPopup> popupRef = showUsagesPopupData.popupRef;
    @NotNull ShowUsagesActionHandler actionHandler = showUsagesPopupData.actionHandler;
    @NotNull ShowUsagesParameters parameters = showUsagesPopupData.parameters;
    @NotNull Project project = parameters.project;
    @NotNull DialogPanel headerPanel = showUsagesPopupData.header.panel;

    SpeedSearchAdvertiser advertiser = new SpeedSearchAdvertiser();
    String hint = getSecondInvocationHint(actionHandler);

    JPanel advertiserComponent = null;
    boolean hintAdded = advertiser.addAdvertisement(hint);
    boolean speedSearchAdded = advertiser.addSpeedSearchAdvertisement() != null;
    if (hintAdded || speedSearchAdded) {
      advertiserComponent = advertiser.getComponent();
    }

    PopupChooserBuilder<?> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(table).
      setTitle(showUsagesPopupData.header.getTitle()).
      setAdvertiser(advertiserComponent).
      setMovable(true).
      setResizable(true).
      setCancelKeyEnabled(true).
      setDimensionServiceKey(DIMENSION_SERVICE_KEY);

    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    boolean addCodePreview = properties.isValueSet(PREVIEW_PROPERTY_KEY);
    OnePixelSplitter contentSplitter = null;
    if (addCodePreview) {
      contentSplitter = new OnePixelSplitter(true, .6f);
      contentSplitter.setSplitterProportionKey(SPLITTER_SERVICE_KEY);
      contentSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_SECOND_SIZE);
      contentSplitter.getDivider().setBackground(OnePixelDivider.BACKGROUND);
      builder.setContentSplitter(contentSplitter);
    }

    Disposable contentDisposable = Disposer.newDisposable();

    KeyboardShortcut shortcut = UsageViewUtil.getShowUsagesWithSettingsShortcut();
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
          showUsagesInMaximalScope(parameters, actionHandler, showUsagesPopupData);
        }
      }.registerCustomShortcutSet(new CustomShortcutSet(shortcut.getFirstKeyStroke()), table);
    }

    DefaultActionGroup filteringGroup = new DefaultActionGroup();
    usageView.addFilteringActions(filteringGroup);
    ActionManager actionManager = ActionManager.getInstance();
    filteringGroup.add(actionManager.getAction("UsageGrouping.FileStructure"));
    filteringGroup.add(new ToggleAction(UsageViewBundle.message("preview.usages.action.text"), null, AllIcons.Actions.PreviewDetailsVertically) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return properties.isValueSet(PREVIEW_PROPERTY_KEY);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (e.getDataContext() != DataContext.EMPTY_CONTEXT) { // Avoid fake events
          properties.setValue(PREVIEW_PROPERTY_KEY, state);
          cancel(popupRef.get(), actionHandler, CLOSE_REASON_PREVIEW);

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

    SearchScope maximalScope;
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-349679, EA-891094")) {
      maximalScope = actionHandler.getMaximalScope();
    }
    if (!(maximalScope instanceof LocalSearchScope)) {
      DefaultActionGroup scopeChooserGroup = new DefaultActionGroup(createScopeChooser(project, contentDisposable, usageView, showUsagesPopupData));
      ActionToolbar scopeChooserToolbar =
        actionManager.createActionToolbar(ActionPlaces.SHOW_USAGES_POPUP_TOOLBAR, scopeChooserGroup, true);
      scopeChooserToolbar.setTargetComponent(table);
      scopeChooserToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
      ((ActionToolbarImpl) scopeChooserToolbar).setForceMinimumSize(true);
      JComponent component = scopeChooserToolbar.getComponent();
      component.setBorder(JBUI.Borders.empty());
      component.setOpaque(false);
      northPanel.add(component, gc.next());
    }

    Dimension fillerSize = JBUI.size(8, 0);
    northPanel.add(new Box.Filler(fillerSize, fillerSize, fillerSize), gc.next().weightx(1.0));

    DefaultActionGroup settingsGroup = new DefaultActionGroup(
      new SettingsAction(project, () -> cancel(popupRef.get()), showDialogAndRestartRunnable(parameters, actionHandler)));
    actionToolbar = createActionToolbar(table, settingsGroup);
    ((ActionToolbarImpl) actionToolbar).setForceMinimumSize(true);
    JComponent settingsToolbarComponent = actionToolbar.getComponent();
    settingsToolbarComponent.setOpaque(false);

    if (Registry.is("ide.usages.popup.show.options.string")) {
      JLabel optionsLabel = new JLabel(actionHandler.getPresentation().getOptionsString());
      if (ExperimentalUI.isNewUI()) {
        optionsLabel.setForeground(JBUI.CurrentTheme.ContextHelp.FOREGROUND);
      }
      northPanel.add(optionsLabel, gc.next());
    }
    northPanel.add(settingsToolbarComponent, gc.next());

    builder.setNorthComponent(northPanel);

    PopupUpdateProcessor processor = new PopupUpdateProcessor(usageView.getProject()) {
      @Override
      public void updatePopup(Object lookupItemObject) {/*not used*/}
    };
    builder.addListener(processor);

    if (addCodePreview) {
      SimpleColoredComponent previewTitle = new SimpleColoredComponent();
      PopupUtil.applyPreviewTitleInsets(previewTitle);
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

      if (ExperimentalUI.isNewUI()) {
        previewPanel.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
        previewTitle.setOpaque(false);
      }

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
        String file = null;
        List<UsageInfoAdapter> adapters = new ArrayList<>();
        for (int row : selectedRows) {
          Object value = table.getModel().getValueAt(row, 0);

          if (value instanceof UsageNode) {
            Usage usage = ((UsageNode)value).getUsage();
            if (usage instanceof UsageInfoAdapter adapter) {
              file = adapter.getPath();
              adapters.add(adapter);
            }
          }
        }

        String selectedFile = file;

        UsageAdaptersKt.getUsageInfoAsFuture(adapters, project).thenAccept(selectedUsages -> {
          ReadAction.nonBlocking(() -> UsagePreviewPanel.isOneAndOnlyOnePsiFileInUsages(selectedUsages))
              .finishOnUiThread(ModalityState.nonModal(), isOneAndOnlyOnePsiFileInUsages -> {
                usagePreviewPanel.updateLayout(project, selectedUsages);
                previewTitle.clear();

                if (isOneAndOnlyOnePsiFileInUsages && selectedFile != null) {
                  previewTitle.append(PathUtil.getFileName(selectedFile), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
              })
            .expireWith(contentDisposable)
            .submit(AppExecutorUtil.getAppExecutorService());
        }).exceptionally(throwable -> {
          Logger.getInstance(ShowUsagesAction.class).error(throwable);
          return null;
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
      builder.setAutoselectOnMouseMove(!ScreenReader.isActive()).setItemChosenCallback(itemChoseCallback).setCloseOnEnter(true);
    }

    AbstractPopup popup = (AbstractPopup)builder.createPopup();
    JComponent content = popup.getContent();
    Disposer.register(popup, contentDisposable);

    // Replace compound header by ShowUsagesHeader. A little hack because there is no suitable popup API
    // CWM: new API method in popup API should be introduced for setting headerPanel
    CaptionPanel captionPanel = popup.getTitle();
    Container parent = captionPanel.getParent();
    if (parent != null) {
      parent.remove(captionPanel);
      parent.add(headerPanel);
      new WindowMoveListener(headerPanel).installTo(headerPanel);
    }

    if (ExperimentalUI.isNewUI()) {
      headerPanel.setBorder(
        JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                              PopupUtil.getComplexPopupHorizontalHeaderBorder()));
      headerPanel.setBackground(JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND);

      Color background = JBUI.CurrentTheme.Popup.BACKGROUND;
      northPanel.setBackground(background);
      table.setBackground(background);
      northPanel.setBorder(createComplexPopupToolbarBorder());
      toolbarComponent.setBorder(JBUI.Borders.empty());
      settingsToolbarComponent.setBorder(JBUI.Borders.emptyLeft(8));
      if (contentSplitter != null) {
        contentSplitter.setBackground(background);
        contentSplitter.setOpaque(true);
        Insets textFieldBorderInsets = JBUI.CurrentTheme.ComplexPopup.textFieldBorderInsets();
        //noinspection UseDPIAwareInsets
        contentSplitter.setBlindZone(() -> new Insets(0, textFieldBorderInsets.left, 0, textFieldBorderInsets.right));
      }
    } else {
      headerPanel.setBorder(JBUI.Borders.empty(2, 15, 2, 2));
      headerPanel.setBackground(JBUI.CurrentTheme.Popup.headerBackground(true));
    }

    parameters.minWidth.set(-1);
    for (AnAction action : filteringGroup.getChildren(actionManager)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }

    for (AnAction action : showUsagesPopupData.pinGroup.getChildren(actionManager)) {
      action.unregisterCustomShortcutSet(usageView.getComponent());
      action.registerCustomShortcutSet(action.getShortcutSet(), content);
    }
    /* save toolbar actions for using later, in automatic filter toggling in {@link #restartShowUsagesWithFiltersToggled(List} */
    popup.setUserData(addCodePreview ? Arrays.asList(filteringGroup, contentSplitter) : Collections.singletonList(filteringGroup));
    popup.setUiDataProvider(sink -> {
      sink.set(UsageView.USAGE_VIEW_SETTINGS_KEY, usageView.getUsageViewSettings());
    });
    popupRef.set(popup);
    return popup;
  }

  private static Border createComplexPopupToolbarBorder() {
    Insets lineInsets = JBUI.CurrentTheme.ComplexPopup.textFieldBorderInsets();
    //noinspection UseDPIAwareBorders
    return JBUI.Borders.compound(new EmptyBorder(0, lineInsets.left, 4, lineInsets.right),
                                 JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                 JBUI.Borders.empty(JBUI.CurrentTheme.ComplexPopup.innerBorderInsets()));
  }

  static @NotNull ActionToolbar createActionToolbar(@NotNull JTable table, @NotNull DefaultActionGroup group) {
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SHOW_USAGES_POPUP_TOOLBAR, group, true);
    actionToolbar.setTargetComponent(table);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    return actionToolbar;
  }

  static void cancel(@Nullable AbstractPopup popup) {
    cancel(popup, null, null);
  }

  static void cancel(@Nullable AbstractPopup popup, @Nullable ShowUsagesActionHandler actionHandler, @Nullable String closeReason) {
    if (popup != null) {
      // TODO: think about better API for providing information about closing reason
      // It is important for RDCT - in some cases we have to terminate protocol session, but in some we can not do it
      if (actionHandler != null) {
        actionHandler.beforeClose(closeReason);
      }
      popup.cancel();
    }
  }

  private static @NotNull ScopeChooserGroup createScopeChooser(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull UsageViewImpl usageView,
    @NotNull ShowUsagesPopupData showUsagesPopupData
  ) {
    ShowUsagesActionHandler actionHandler = showUsagesPopupData.actionHandler;
    ScopeChooserGroup result = new ScopeChooserGroup(project, parentDisposable, actionHandler.getSelectedScope());
    result.addChangeListener(scope -> {
      UsageViewStatisticsCollector.logScopeChanged(project, usageView, actionHandler.getSelectedScope(), scope,
                                                   actionHandler.getTargetClass());
      if (scope == null) {
        return;
      }
      FindUsagesSettings.getInstance().setDefaultScopeName(scope.getDisplayName());
      cancel(showUsagesPopupData.popupRef.get(), actionHandler, CLOSE_REASON_CHANGE_SCOPE);
      ShowUsagesActionHandler handler = actionHandler.withScope(scope);
      if (handler != null) {
        showElementUsages(showUsagesPopupData.parameters, handler);
      }
    });

    return result;
  }

  static @Nullable KeyboardShortcut getShowUsagesShortcut() {
    return ActionManager.getInstance().getKeyboardShortcut(ID);
  }

  private static @Nullable UsageNode getSelectedUsageNode(@NotNull ShowUsagesTable table) {
    int selectedRowNumber = table.getSelectedRow();
    if (selectedRowNumber != -1) {
      Object selectedNode = table.getModel().getValueAt(selectedRowNumber, 0);
      return ObjectUtils.tryCast(selectedNode, UsageNode.class);
    }
    else {
      return null;
    }
  }

  private static int getRowNumber(@Nullable UsageNode node, @NotNull ShowUsagesTable table) {
    for (int i = 0; i < table.getRowCount(); i++) {
      if (table.getValueAt(i, 0) == node) {
        return i;
      }
    }
    return -1;
  }

  private static int getUsageOffset(@NotNull Usage usage) {
    if (!(usage instanceof UsageInfo2UsageAdapter)) return -1;
    PsiElement element = ((UsageInfo2UsageAdapter)usage).getElement();
    if (element == null) return -1;
    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(element.getProject());
    if (injectionManager.isInjectedFragment(element.getContainingFile())) {
      return injectionManager.injectedToHost(element, element.getTextRange().getStartOffset());
    }
    return element.getTextRange().getStartOffset();
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static boolean areAllUsagesInOneLine(@NotNull Usage visibleUsage, @NotNull List<? extends Usage> usages) {
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
    for (int col = 0; col < colsNum; col++) {
      TableColumn column = table.getColumnModel().getColumn(col);
      int preferred = column.getPreferredWidth();
      int width = Math.max(preferred, columnMaxWidth(table, col));
      totalWidth += width;
      column.setMinWidth(Math.min(ShowUsagesTable.MIN_COLUMN_WIDTH, width));
      column.setMaxWidth(width);
      column.setWidth(width);
      column.setPreferredWidth(width);
    }
    table.getColumnModel().getColumn(colsNum - 1).setMaxWidth(Integer.MAX_VALUE); //last column should grow for the rest of the table width

    return totalWidth;
  }

  private static int columnMaxWidth(@NotNull JTable table, int col) {
    TableColumn column = table.getColumnModel().getColumn(col);
    int width = 0;
    for (int i = 0; i < table.getRowCount(); i++) {
      int row = i;
      ShowUsagesTable.MyModel model = (ShowUsagesTable.MyModel)table.getModel();
      int rendererWidth = model.getOrCalcCellWidth(i, col, () -> {
        Component component = table.prepareRenderer(column.getCellRenderer(), row, col);
        return component.getPreferredSize().width;
      });

      width = Math.max(width, rendererWidth + table.getIntercellSpacing().width);
    }
    return Math.min(ShowUsagesTable.MAX_COLUMN_WIDTH, width);
  }

  private static void rebuildTable(@NotNull Project project,
                                   @NotNull Predicate<? super Usage> originUsageCheck,
                                   @NotNull List<UsageNode> data,
                                   @NotNull ShowUsagesTable table,
                                   @Nullable AbstractPopup popup,
                                   @NotNull RelativePoint popupPosition,
                                   @NotNull IntRef minWidth,
                                   @NotNull AtomicBoolean manuallyResized) {
    ThreadingAssertions.assertEventDispatchThread();

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

    if (Registry.is("find.usages.disable.smart.size", false)) {
      calcMaxWidth(table);
      return;
    }

    if (isCodeWithMeClientInstance(popup)) return;

    Component toolbarComponent = ((BorderLayout)popup.getComponent().getLayout()).getLayoutComponent(BorderLayout.NORTH);
    Dimension toolbarSize = toolbarComponent != null ? toolbarComponent.getPreferredSize() : JBUI.emptySize();
    Dimension headerSize = popup.getHeaderPreferredSize();

    int width = calcMaxWidth(table);
    width = Math.max(headerSize.width, width);
    width = Math.max(toolbarSize.width, width);
    width = Math.max(minWidth.get(), width);

    minWidth.set(width);

    int minHeight = headerSize.height + toolbarSize.height;

    Rectangle rectangle = getPreferredBounds(table, popupPosition.getScreenPoint(), width, minHeight, dataSize, showCodePreview);
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
    popup.moveToFitScreen();
  }

  private static boolean isCodeWithMeClientInstance(@NotNull JBPopup popup) {
    JComponent content = popup.getContent();
    return content.getClientProperty("THIN_CLIENT") != null;
  }

  private static @NotNull Rectangle getPreferredBounds(@NotNull JTable table, @NotNull Point point, int width, int minHeight, int modelRows,
                                                       boolean showCodePreview) {
    boolean addExtraSpace = Registry.is("ide.preferred.scrollable.viewport.extra.space");
    int visibleRows = Math.min(showCodePreview ? 20 : 30, modelRows);
    int rowHeight = table.getRowHeight();
    int space = addExtraSpace && visibleRows < modelRows ? rowHeight / 2 : 0;
    int height = visibleRows * rowHeight + minHeight + space;
    if (ExperimentalUI.isNewUI() && space == 0 && visibleRows == modelRows) {
      height += JBUIScale.scale(4);
    }
    Rectangle bounds = new Rectangle(point.x, point.y, width, height);
    ScreenUtil.fitToScreen(bounds);
    if (bounds.height != height) {
      minHeight += addExtraSpace && space == 0 ? rowHeight / 2 : space;
      bounds.height = Math.max(1, (bounds.height - minHeight) / rowHeight) * rowHeight + minHeight;
    }
    return bounds;
  }

  private static void addUsageNodes(@NotNull GroupNode root, @NotNull List<? super UsageNode> outNodes) {
    for (UsageNode node : root.getUsageNodes()) {
      node.setParent(root);
      outNodes.add(node);
    }
    for (GroupNode groupNode : root.getSubGroups()) {
      groupNode.setParent(root);
      addUsageNodes(groupNode, outNodes);
    }
  }

  private static void cancelAndShowHint(@NotNull AbstractPopup popupToCancel,
                                        boolean isWarning,
                                        @Nls(capitalization = Sentence) @NotNull String hint,
                                        @NotNull ShowUsagesParameters parameters,
                                        @NotNull ShowUsagesActionHandler actionHandler) {
    Project project = parameters.project;
    Editor editor = parameters.editor;

    Runnable runnable = () -> {
      if (!actionHandler.isValid()) {
        cancel(popupToCancel);
        return;
      }

      ReadAction.nonBlocking(() -> getSecondInvocationHint(actionHandler))
        .finishOnUiThread(ModalityState.nonModal(), (@NlsContexts.HintText String restartHint) -> {
          HtmlBuilder builder = new HtmlBuilder().append(hint);
          if (restartHint != null) {
            HtmlChunk chunk = HtmlChunk.text(restartHint);
            builder.br().append(ExperimentalUI.isNewUI() ? chunk.wrapWith("p").style("margin-top:5pt;") : chunk.wrapWith("small"));
          }
          builder.br().appendLink(FIND_OPTIONS_HREF_TARGET, UsageViewBundle.message("link.display.name.find.options"));
          KeyboardShortcut shortcut = UsageViewUtil.getShowUsagesWithSettingsShortcut();
          if (shortcut != null) {
            builder.nbsp().append("(" + KeymapUtil.getShortcutText(shortcut) + ")");
          }
          @NlsContexts.HintText String secondInvocationHintHtml = builder.toString();
          if (!actionHandler.isValid()) {
            cancel(popupToCancel);
            return;
          }

          JEditorPane label = IdeTooltipManager.initPane(
            new Html(secondInvocationHintHtml), isWarning ? HintUtil.getWarningHint() : HintUtil.getInformationHint(),
            null, true);

          label.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
              if (FIND_OPTIONS_HREF_TARGET.equals(e.getDescription())) {
                showDialogAndRestart(parameters, actionHandler);
              }
            }
          });
          label.setEditable(false);

          Runnable clearContinuation = actionHandler.enableMaximalScopeSearch(parameters);
          // canceling here, as the action handler becomes not fully valid after the cancellation
          // in case of rem-dev (FrontendShowUsagesActionHandler), and the above call won't work as expected
          cancel(popupToCancel);

          if (editor == null || editor.isDisposed() || !UIUtil.isShowing(editor.getContentComponent())) {
            label.setBorder(JBUI.Borders.empty(5));
            int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
            HintManager.getInstance().showHint(label, parameters.popupPosition, flags, 0, clearContinuation);
          }
          else {
            HintManager.getInstance().showInformationHint(editor, label, clearContinuation);
          }
        }).submit(AppExecutorUtil.getAppExecutorService());
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
              () -> AsyncEditorLoader.Companion.performWhenLoaded(editor, runnable)
            );
          })
      );
    }
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

  private static final class SettingsAction extends DumbAwareAction implements CustomComponentAction {
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
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return new ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        protected @Nullable String getShortcutText() {
          KeyboardShortcut shortcut = UsageViewUtil.getShowUsagesWithSettingsShortcut();
          return shortcut != null ? KeymapUtil.getShortcutText(shortcut) : null;
        }
      };
    }
  }

  abstract static class FilteredOutUsagesNode extends UsageNode {

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
    return () -> {
      ShowUsagesParameters moreUsagesParameters = actionHandler.moreUsages(parameters);
      if (moreUsagesParameters != null) {
        showElementUsages(moreUsagesParameters, actionHandler);
      }
    };
  }

  private static @NotNull Runnable showUsagesInMaximalScopeRunnable(@NotNull ShowUsagesParameters parameters,
                                                                    @NotNull ShowUsagesActionHandler actionHandler,
                                                                    @Nullable ShowUsagesPopupData showUsagesPopupData) {
    return () -> showUsagesInMaximalScope(parameters, actionHandler, showUsagesPopupData);
  }

  private static void showUsagesInMaximalScope(@NotNull ShowUsagesParameters parameters,
                                               @NotNull ShowUsagesActionHandler actionHandler,
                                               @Nullable ShowUsagesPopupData showUsagesPopupData) {
    if (showUsagesPopupData != null) {
      cancel(showUsagesPopupData.popupRef.get(), actionHandler, CLOSE_REASON_CHANGE_SCOPE);
    }
    ShowUsagesActionHandler handler = actionHandler.withScope(actionHandler.getMaximalScope());
    if (handler != null) {
      showElementUsages(parameters, handler);
    }
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

  @Service(Service.Level.PROJECT)
  private static final class ShowUsagesActionState {
    Runnable continuation;
  }

  private static @NotNull ShowUsagesActionState getState(@NotNull Project project) {
    return project.getService(ShowUsagesActionState.class);
  }

  @ApiStatus.Internal
  public static void requestMaximalScopeSearch(@NotNull ShowUsagesParameters parameters, @NotNull ShowUsagesActionHandler actionHandler) {
    ShowUsagesAction.ShowUsagesActionState state = getState(parameters.project);
    state.continuation = showUsagesInMaximalScopeRunnable(parameters, actionHandler, null);
  }

  @ApiStatus.Internal
  public static void resetMaximalScopeSearch(@NotNull Project project) {
    ShowUsagesAction.ShowUsagesActionState state = getState(project);
    state.continuation = null;
  }

  @TestOnly
  public static void setPopupDelayTimeout(int timeout) {
    ourPopupDelayTimeout = timeout;
  }
}
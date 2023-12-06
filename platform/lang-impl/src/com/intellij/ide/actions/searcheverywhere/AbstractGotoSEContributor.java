// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.actions.*;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.gotoByName.*;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.ide.util.scopeChooser.ScopeOption;
import com.intellij.ide.util.scopeChooser.ScopeService;
import com.intellij.navigation.AnonymousElementProvider;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractGotoSEContributor implements WeightedSearchEverywhereContributor<Object>, ScopeSupporting,
                                                           SearchEverywhereExtendedInfoProvider {
  protected static final Pattern ourPatternToDetectAnonymousClasses = Pattern.compile("([.\\w]+)((\\$[\\d]+)*(\\$)?)");
  private static final Logger LOG = Logger.getInstance(AbstractGotoSEContributor.class);
  private static final Key<Map<String, String>> SE_SELECTED_SCOPES = Key.create("SE_SELECTED_SCOPES");

  private static final Pattern ourPatternToDetectLinesAndColumns = Pattern.compile(
    "(.+?)" + // name, non-greedy matching
    "(?::|@|,| |#|#L|\\?l=| on line | at line |:line |:?\\(|:?\\[)" + // separator
    "(\\d+)?(?:\\W(\\d+)?)?" + // line + column
    "[)\\]]?" // possible closing paren/brace
  );

  protected final Project myProject;
  protected ScopeDescriptor myScopeDescriptor;

  private final GlobalSearchScope myEverywhereScope;
  private final GlobalSearchScope myProjectScope;

  protected final SmartPsiElementPointer<PsiElement> myPsiContext;

  protected AbstractGotoSEContributor(@NotNull AnActionEvent event) {
    myProject = event.getRequiredData(CommonDataKeys.PROJECT);
    PsiElement context = GotoActionBase.getPsiContext(event);
    myPsiContext = context != null ? SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(context) : null;

    GlobalSearchScope everywhereScope = SearchEverywhereClassifier.EP_Manager.getEverywhereScope(myProject);
    if (everywhereScope == null) {
      everywhereScope = GlobalSearchScope.everythingScope(myProject);
    }
    myEverywhereScope = everywhereScope;

    List<ScopeDescriptor> scopeDescriptors = createScopes();

    GlobalSearchScope projectScope = SearchEverywhereClassifier.EP_Manager.getProjectScope(myProject);
    if (projectScope == null) {
      projectScope = GlobalSearchScope.projectScope(myProject);
      if (myEverywhereScope.equals(projectScope)) {
        // just get the second scope, i.e. Attached Directories in DataGrip
        ScopeDescriptor secondScope = JBIterable.from(scopeDescriptors)
          .filter(o -> !o.scopeEquals(this.myEverywhereScope) && !o.scopeEquals(null))
          .first();
        projectScope = secondScope != null ? (GlobalSearchScope) secondScope.getScope() : this.myEverywhereScope;
      }
    }
    myProjectScope = projectScope;
    myScopeDescriptor = getInitialSelectedScope(scopeDescriptors);

    myProject.getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myScopeDescriptor = getInitialSelectedScope(createScopes());
      }
    });
  }

  protected List<ScopeDescriptor> createScopes() {
    return myProject.getService(ScopeService.class)
      .createModel(EnumSet.of(ScopeOption.LIBRARIES, ScopeOption.EMPTY_SCOPES))
      .getScopesImmediately(createContext(myProject, myPsiContext))
      .getScopeDescriptors();
  }

  @NotNull
  public static DataContext createContext(Project project, @Nullable SmartPsiElementPointer<PsiElement> psiContext) {
    DataContext parentContext = project == null ? null : SimpleDataContext.getProjectContext(project);
    PsiElement context = psiContext != null ? psiContext.getElement() : null;
    PsiFile file = context == null ? null : context.getContainingFile();

    return SimpleDataContext.builder()
      .setParent(parentContext)
      .add(CommonDataKeys.PSI_ELEMENT, context)
      .add(CommonDataKeys.PSI_FILE, file)
      .build();
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isShownInSeparateTab() {
    return true;
  }

  @NotNull
  protected <T> List<AnAction> doGetActions(@Nullable PersistentSearchEverywhereContributorFilter<T> filter,
                                            @Nullable ElementsChooser.StatisticsCollector<T> statisticsCollector,
                                            @NotNull Runnable onChanged) {
    if (myProject == null || filter == null) return Collections.emptyList();
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new ScopeChooserAction() {
      final boolean canToggleEverywhere = !myEverywhereScope.equals(myProjectScope);

      @Override
      protected void onScopeSelected(@NotNull ScopeDescriptor o) {
        setSelectedScope(o);
        onChanged.run();
      }

      @NotNull
      @Override
      protected ScopeDescriptor getSelectedScope() {
        return myScopeDescriptor;
      }

      @Override
      protected void onProjectScopeToggled() {
        setEverywhere(!myScopeDescriptor.scopeEquals(myEverywhereScope));
      }

      @Override
      protected boolean processScopes(@NotNull Processor<? super ScopeDescriptor> processor) {
        return ContainerUtil.process(createScopes(), processor);
      }

      @Override
      public boolean isEverywhere() {
        return myScopeDescriptor.scopeEquals(myEverywhereScope);
      }

      @Override
      public void setEverywhere(boolean everywhere) {
        setSelectedScope(new ScopeDescriptor(everywhere ? myEverywhereScope : myProjectScope));
        onChanged.run();
      }

      @Override
      public boolean canToggleEverywhere() {
        if (!canToggleEverywhere) return false;
        return myScopeDescriptor.scopeEquals(myEverywhereScope) ||
               myScopeDescriptor.scopeEquals(myProjectScope);
      }
    });
    result.add(new PreviewAction());
    result.add(new SearchEverywhereFiltersAction<>(filter, onChanged, statisticsCollector));
    return result;
  }

  @NotNull
  private ScopeDescriptor getInitialSelectedScope(List<? extends ScopeDescriptor> scopeDescriptors) {
    String selectedScope = myProject == null ? null : getSelectedScopes(myProject).get(getClass().getSimpleName());
    if (StringUtil.isNotEmpty(selectedScope)) {
      for (ScopeDescriptor descriptor : scopeDescriptors) {
        if (!selectedScope.equals(descriptor.getDisplayName()) || descriptor.scopeEquals(null)) continue;
        return descriptor;
      }
    }
    return new ScopeDescriptor(myProjectScope);
  }

  private void setSelectedScope(@NotNull ScopeDescriptor o) {
    myScopeDescriptor = o;
    getSelectedScopes(myProject).put(
      getClass().getSimpleName(),
      o.scopeEquals(myEverywhereScope) || o.scopeEquals(myProjectScope) ? null : o.getDisplayName());
  }

  @NotNull
  private static Map<String, String> getSelectedScopes(@NotNull Project project) {
    Map<String, String> map = SE_SELECTED_SCOPES.get(project);
    if (map == null) SE_SELECTED_SCOPES.set(project, map = new HashMap<>(3));
    return map;
  }

  @Override
  public void fetchWeightedElements(@NotNull String pattern,
                                    @NotNull ProgressIndicator progressIndicator,
                                    @NotNull Processor<? super FoundItemDescriptor<Object>> consumer) {
    if (myProject == null) return; //nowhere to search

    if (!isEmptyPatternSupported() && pattern.isEmpty()) return;

    Runnable fetchRunnable = () -> {
      if (!isDumbAware() && DumbService.isDumb(myProject)) return;

      FilteringGotoByModel<?> model = createModel(myProject);
      if (progressIndicator.isCanceled()) return;

      PsiElement context = myPsiContext != null ? myPsiContext.getElement() : null;
      ChooseByNameItemProvider provider = ChooseByNameModelEx.getItemProvider(model, context);
      GlobalSearchScope scope = (GlobalSearchScope)Objects.requireNonNull(myScopeDescriptor.getScope());

      boolean everywhere = scope.isSearchInLibraries();
      ChooseByNameViewModel viewModel = new MyViewModel(myProject, model);

      if (provider instanceof ChooseByNameInScopeItemProvider) {
        FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, scope);
        ((ChooseByNameInScopeItemProvider)provider).filterElementsWithWeights(viewModel, parameters, progressIndicator,
                                                                              item -> processElement(progressIndicator, consumer, model,
                                                                                                     item.getItem(), item.getWeight())
        );
      }
      else if (provider instanceof ChooseByNameWeightedItemProvider) {
        ((ChooseByNameWeightedItemProvider)provider).filterElementsWithWeights(viewModel, pattern, everywhere, progressIndicator,
                                                                               item -> processElement(progressIndicator, consumer, model,
                                                                                                      item.getItem(), item.getWeight())
        );
      }
      else {
        provider.filterElements(viewModel, pattern, everywhere, progressIndicator,
                                element -> processElement(progressIndicator, consumer, model, element,
                                                          getElementPriority(element, pattern))
        );
      }
    };


    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() && application.isDispatchThread()) {
      fetchRunnable.run();
    }
    else {
      ProgressIndicatorUtils.yieldToPendingWriteActions();
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(fetchRunnable, progressIndicator);
    }
  }
  protected boolean processElement(@NotNull ProgressIndicator progressIndicator,
                                   @NotNull Processor<? super FoundItemDescriptor<Object>> consumer,
                                   FilteringGotoByModel<?> model, Object element, int degree) {
    if (progressIndicator.isCanceled()) return false;

    if (element == null) {
      LOG.error("Null returned from " + model + " in " + this);
      return true;
    }

    return consumer.process(new FoundItemDescriptor<>(element, degree));
  }

  @Override
  public ScopeDescriptor getScope() {
    return myScopeDescriptor;
  }

  @Override
  public void setScope(ScopeDescriptor scope) {
    setSelectedScope(scope);
  }

  @Override
  public List<ScopeDescriptor> getSupportedScopes() {
    return createScopes();
  }

  @Override
  public @NotNull List<SearchEverywhereCommandInfo> getSupportedCommands() {
    return WeightedSearchEverywhereContributor.super.getSupportedCommands();
  }

  @NotNull
  protected abstract FilteringGotoByModel<?> createModel(@NotNull Project project);

  @NotNull
  @Override
  public String filterControlSymbols(@NotNull String pattern) {
    if (StringUtil.containsAnyChar(pattern, ":,;@[( #") ||
        pattern.contains(" line ") ||
        pattern.contains("?l=")) { // quick test if reg exp should be used
      return applyPatternFilter(pattern, ourPatternToDetectLinesAndColumns);
    }

    return pattern;
  }

  protected static String applyPatternFilter(String str, Pattern regex) {
    Matcher matcher = regex.matcher(str);
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return str;
  }

  @Override
  public boolean showInFindResults() {
    return true;
  }

  @Override
  public boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText) {
    if (selected instanceof PsiElement) {
      if (!((PsiElement)selected).isValid()) {
        LOG.warn("Cannot navigate to invalid PsiElement");
        return true;
      }

      ReadAction.nonBlocking(() -> {
          PsiElement psiElement = preparePsi((PsiElement)selected, modifiers, searchText);
          Navigatable extNavigatable = createExtendedNavigatable(psiElement, searchText, modifiers);
          VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);
          Runnable command = (modifiers & InputEvent.SHIFT_MASK) != 0 && file != null
                             ? () -> OpenInRightSplitAction.Companion.openInRightSplit(myProject, file, extNavigatable, true)
                             : () -> doNavigate(psiElement, extNavigatable);
          return command;
        })
        .finishOnUiThread(ModalityState.nonModal(), Runnable::run)
        .submit(AppExecutorUtil.getAppExecutorService());
    }
    else {
      EditSourceUtil.navigate(((NavigationItem)selected), true, false);
    }

    return true;
  }

  private static void doNavigate(PsiElement psiElement, Navigatable extNavigatable) {
    if (extNavigatable != null && extNavigatable.canNavigate()) {
      extNavigatable.navigate(true);
    }
    else {
      NavigationUtil.activateFileWithPsiElement(psiElement, true);
    }
  }

  @Override
  public Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      if (element instanceof PsiElement) {
        return element;
      }
      if (element instanceof DataProvider) {
        return ((DataProvider)element).getData(dataId);
      }
      if (element instanceof PsiElementNavigationItem) {
        return ((PsiElementNavigationItem)element).getTargetElement();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getItemDescription(@NotNull Object element) {
    return element instanceof PsiElement && ((PsiElement)element).isValid()
           ? QualifiedNameProviderUtil.getQualifiedName((PsiElement) element)
           : null;
  }

  @Override
  public boolean isMultiSelectionSupported() {
    return true;
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(createModel(myProject));
  }

  @NotNull
  @Override
  public ListCellRenderer<Object> getElementsRenderer() {
    return new SearchEverywherePsiRenderer(this);
  }

  @Override
  public int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return 50;
  }

  @Nullable
  protected Navigatable createExtendedNavigatable(PsiElement psi, String searchText, int modifiers) {
    VirtualFile file = PsiUtilCore.getVirtualFile(psi);
    Pair<Integer, Integer> position = getLineAndColumn(searchText);
    boolean positionSpecified = position.first >= 0 || position.second >= 0;
    if (file != null && positionSpecified) {
      return new OpenFileDescriptor(psi.getProject(), file, position.first, position.second);
    }

    return null;
  }

  protected PsiElement preparePsi(PsiElement psiElement, int modifiers, String searchText) {
    String path = pathToAnonymousClass(searchText);
    if (path != null) {
      psiElement = getElement(psiElement, path);
    }
    return psiElement.getNavigationElement();
  }

  protected static Pair<Integer, Integer> getLineAndColumn(String text) {
    int line = getLineAndColumnRegexpGroup(text, 2);
    int column = getLineAndColumnRegexpGroup(text, 3);

    if (line == -1 && column != -1) {
      line = 0;
    }

    return new Pair<>(line, column);
  }

  private static int getLineAndColumnRegexpGroup(String text, int groupNumber) {
    final Matcher matcher = ourPatternToDetectLinesAndColumns.matcher(text);
    if (matcher.matches()) {
      try {
        if (groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
      }
      catch (NumberFormatException ignored) {
      }
    }

    return -1;
  }

  private static String pathToAnonymousClass(String searchedText) {
    return ClassSearchEverywhereContributor.pathToAnonymousClass(ourPatternToDetectAnonymousClasses.matcher(searchedText));
  }

  @NotNull
  public static PsiElement getElement(@NotNull PsiElement element, @NotNull String path) {
    final String[] classes = path.split("\\$");
    List<Integer> indexes = new ArrayList<>();
    for (String cls : classes) {
      if (cls.isEmpty()) continue;
      try {
        indexes.add(Integer.parseInt(cls) - 1);
      }
      catch (Exception e) {
        return element;
      }
    }
    PsiElement current = element;
    for (int index : indexes) {
      final PsiElement[] anonymousClasses = getAnonymousClasses(current);
      if (index >= 0 && index < anonymousClasses.length) {
        current = anonymousClasses[index];
      }
      else {
        return current;
      }
    }
    return current;
  }

  private static PsiElement @NotNull [] getAnonymousClasses(@NotNull PsiElement element) {
    for (AnonymousElementProvider provider : AnonymousElementProvider.EP_NAME.getExtensionList()) {
      final PsiElement[] elements = provider.getAnonymousElements(element);
      if (elements.length > 0) {
        return elements;
      }
    }
    return PsiElement.EMPTY_ARRAY;
  }

  private static final class MyViewModel implements ChooseByNameViewModel {
    private final Project myProject;
    private final ChooseByNameModel myModel;

    private MyViewModel(Project project, ChooseByNameModel model) {
      myProject = project;
      myModel = model;
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public @NotNull ChooseByNameModel getModel() {
      return myModel;
    }

    @Override
    public boolean isSearchInAnyPlace() {
      return myModel.useMiddleMatching();
    }

    @Override
    public @NotNull String transformPattern(@NotNull String pattern) {
      return ChooseByNamePopup.getTransformedPattern(pattern, myModel);
    }

    @Override
    public boolean canShowListForEmptyPattern() {
      return false;
    }

    @Override
    public int getMaximumListSizeLimit() {
      return 0;
    }
  }
}

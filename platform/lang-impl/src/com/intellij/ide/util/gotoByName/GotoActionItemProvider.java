// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.DataManager;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.WordPrefixMatcher;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.CollectConsumer;
import com.intellij.util.DefaultBundleService;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.ide.util.gotoByName.GotoActionModel.*;

/**
 * @author peter
 */
public final class GotoActionItemProvider implements ChooseByNameWeightedItemProvider {

  private static final int BONUS_FOR_SPACE_IN_PATTERN = 100;
  private static final int SETTINGS_PENALTY = 100;

  private final ActionManager myActionManager = ActionManager.getInstance();
  private final GotoActionModel myModel;
  private final ClearableLazyValue<Map<String, ApplyIntentionAction>> myIntentions;

  public GotoActionItemProvider(GotoActionModel model) {
    myModel = model;
    myIntentions = ClearableLazyValue.create(() -> ReadAction.compute(() -> myModel.getAvailableIntentions()));
  }

  @Override
  public @NotNull List<String> filterNames(@NotNull ChooseByNameBase base, String @NotNull [] names, @NotNull String pattern) {
    return filterNames((ChooseByNameViewModel)base, names, pattern);
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameViewModel base, String @NotNull [] names, @NotNull String pattern) {
    return Collections.emptyList(); // no common prefix insertion in goto action
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameBase base,
                                @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator cancelled,
                                @NotNull Processor<Object> consumer) {
    return filterElements((ChooseByNameViewModel)base, pattern, everywhere, cancelled, consumer);
  }

  @Override
  public boolean filterElements(final @NotNull ChooseByNameViewModel base,
                                @NotNull final String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator cancelled,
                                @NotNull final Processor<Object> consumer) {
    return filterElementsWithWeights(base, pattern, everywhere, cancelled, descriptor -> consumer.process(descriptor.getItem()));
  }

  @Override
  public boolean filterElementsWithWeights(@NotNull ChooseByNameViewModel base,
                                           @NotNull String pattern,
                                           boolean everywhere,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Processor<? super FoundItemDescriptor<?>> consumer) {
    return filterElements(pattern, value -> {
      if (!everywhere && value.value instanceof ActionWrapper && !((ActionWrapper)value.value).isAvailable()) {
        return true;
      }
      return consumer.process(new FoundItemDescriptor<>(value, value.getMatchingDegree()));
    });
  }

  public boolean filterElements(@NotNull String pattern, @NotNull Processor<? super MatchedValue> consumer) {
    myModel.buildGroupMappings();

    if (!processAbbreviations(pattern, consumer)) return false;
    if (!processActions(pattern, consumer)) return false;
    if (!processTopHits(pattern, consumer)) return false;
    if (!processIntentions(pattern, consumer)) return false;
    if (!processOptions(pattern, consumer)) return false;

    return true;
  }

  private boolean processAbbreviations(@NotNull String pattern, Processor<? super MatchedValue> consumer) {
    MinusculeMatcher matcher = buildWeightMatcher(pattern);
    List<String> actionIds = AbbreviationManager.getInstance().findActions(pattern);
    JBIterable<MatchedValue> wrappers = JBIterable.from(actionIds)
      .filterMap(myActionManager::getAction)
      .transform(action -> {
        ActionWrapper wrapper = wrapAnAction(action);
        int degree = matcher.matchingDegree(pattern);
        return new MatchedValue(wrapper, pattern, degree) {
          @NotNull
          @Override
          public String getValueText() {
            return pattern;
          }
        };
      });
    return processItems(pattern, wrappers, consumer);
  }

  private boolean processTopHits(String pattern, Processor<? super MatchedValue> consumer) {
    Project project = myModel.getProject();
    final CollectConsumer<Object> collector = new CollectConsumer<>();
    String commandAccelerator = SearchTopHitProvider.getTopHitAccelerator();
    for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
      //noinspection deprecation
      if (provider instanceof OptionsTopHitProvider.CoveredByToggleActions) {
        continue;
      }

      if (provider instanceof OptionsSearchTopHitProvider && !StringUtil.startsWith(pattern, commandAccelerator)) {
        String prefix = commandAccelerator + ((OptionsSearchTopHitProvider)provider).getId() + " ";
        provider.consumeTopHits(prefix + pattern, collector, project);
      }
      else if (project != null && provider instanceof OptionsTopHitProvider.ProjectLevelProvidersAdapter) {
        ((OptionsTopHitProvider.ProjectLevelProvidersAdapter)provider).consumeAllTopHits(pattern, collector, project);
      }
      provider.consumeTopHits(pattern, collector, project);
    }
    Collection<Object> result = collector.getResult();
    JBIterable<?> wrappers = JBIterable.from(result)
      .transform(object -> object instanceof AnAction ? wrapAnAction((AnAction)object) : object);
    return processItems(pattern, wrappers, consumer);
  }

  private boolean processOptions(String pattern, Processor<? super MatchedValue> consumer) {
    Map<@NonNls String, @NlsContexts.ConfigurableName String> map = myModel.getConfigurablesNames();
    SearchableOptionsRegistrarImpl registrar = (SearchableOptionsRegistrarImpl)SearchableOptionsRegistrar.getInstance();

    List<Object> options = new ArrayList<>();
    final Set<String> words = registrar.getProcessedWords(pattern);
    Set<OptionDescription> optionDescriptions = null;
    boolean filterOutInspections = Registry.is("go.to.action.filter.out.inspections", true);
    for (String word : words) {
      final Set<OptionDescription> descriptions = registrar.getAcceptableDescriptions(word);
      if (descriptions != null) {
        descriptions.removeIf(description -> {
          return "ActionManager".equals(description.getPath()) ||
                 filterOutInspections && "Inspections".equals(description.getGroupName());
        });
        if (!descriptions.isEmpty()) {
          if (optionDescriptions == null) {
            optionDescriptions = descriptions;
          }
          else {
            optionDescriptions.retainAll(descriptions);
          }
        }
      }
      else {
        optionDescriptions = null;
        break;
      }
    }
    if (!Strings.isEmptyOrSpaces(pattern)) {
      Matcher matcher = buildMatcher(pattern);
      if (optionDescriptions == null) {
        optionDescriptions = new HashSet<>();
      }
      for (Map.Entry<@NonNls String, @NlsContexts.ConfigurableName String> entry : map.entrySet()) {
        if (matcher.matches(entry.getValue())) {
          optionDescriptions.add(new OptionDescription(null, entry.getKey(), entry.getValue(), null, entry.getValue()));
        }
      }
    }
    if (optionDescriptions != null && !optionDescriptions.isEmpty()) {
      Set<String> currentHits = new HashSet<>();
      for (Iterator<OptionDescription> iterator = optionDescriptions.iterator(); iterator.hasNext(); ) {
        OptionDescription description = iterator.next();
        final String hit = description.getHit();
        if (hit == null || !currentHits.add(hit.trim())) {
          iterator.remove();
        }
      }
      for (OptionDescription description : optionDescriptions) {
        for (ActionFromOptionDescriptorProvider converter : ActionFromOptionDescriptorProvider.EP.getExtensions()) {
          AnAction action = converter.provide(description);
          if (action != null) options.add(new ActionWrapper(action, null, MatchMode.NAME, myModel));
        }
        options.add(description);
      }
    }
    return processItems(pattern, JBIterable.from(options), consumer);
  }

  private boolean processActions(String pattern, Processor<? super MatchedValue> consumer) {
    Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
    JBIterable<AnAction> actions = JBIterable.from(ids).filterMap(myActionManager::getAction);
    Matcher matcher = buildMatcher(pattern);

    QuickActionProvider provider = myModel.getDataContext().getData(QuickActionProvider.KEY);
    if (provider != null) {
      actions = actions.append(provider.getActions(true));
    }

    Set<AnAction> seenActions = new HashSet<>();
    JBIterable<ActionWrapper> actionWrappers = actions.unique().filterMap(action -> {
      seenActions.add(action);
      if (action instanceof ActionGroup && !((ActionGroup)action).isSearchable()) return null;
      MatchMode mode = myModel.actionMatches(pattern, matcher, action);
      if (mode == MatchMode.NONE) return null;
      return new ActionWrapper(action, myModel.getGroupMapping(action), mode, myModel);
    });
    if (Registry.is("actionSystem.gotoAction.all.toolwindows")) {
      List<ActionWrapper> toolWindowActions = collectToolWindowQuickActionProviders(pattern, matcher, seenActions);
      actionWrappers = actionWrappers.append(toolWindowActions);
    }
    return processItems(pattern, actionWrappers, consumer);
  }

  @NotNull
  private List<ActionWrapper> collectToolWindowQuickActionProviders(String pattern, Matcher matcher, Set<AnAction> seenActions) {
    List<ActionWrapper> result = new ArrayList<>();
    Project project = myModel.getProject();
    if (project != null) {
      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      String[] toolWindowIds = toolWindowManager.getToolWindowIds();
      for (String toolWindowId : toolWindowIds) {
        ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
        if (toolWindow != null) {
          if (!toolWindow.isVisible()) continue;
          ContentManager contentManager = toolWindow.getContentManagerIfCreated();
          if (contentManager != null) {
            Content content = contentManager.getSelectedContent();
            if (content != null) {
              DataContext dataContext = DataManager.getInstance().getDataContext(content.getComponent());
              QuickActionProvider provider = QuickActionProvider.KEY.getData(dataContext);
              if (provider != null) {
                List<AnAction> providerActions = provider.getActions(true);
                String title = toolWindow.getTitle();
                if (StringUtil.isEmpty(title)) title = toolWindow.getStripeTitle();
                appendActionsFromProvider(pattern, matcher, result, seenActions, providerActions, dataContext, title);
              }
            }
          }
        }
      }
    }
    return result;
  }

  private void appendActionsFromProvider(String pattern,
                                         Matcher matcher,
                                         List<ActionWrapper> result,
                                         Set<AnAction> seenActions,
                                         List<AnAction> providerActions,
                                         DataContext dataContext,
                                         @NlsContexts.TabTitle @Nullable String title) {
    for (AnAction action : providerActions) {
      if (seenActions.contains(action)) continue;
      seenActions.add(action);
      if (action instanceof ActionGroup) {
        AnAction[] children = ((ActionGroup)action).getChildren(AnActionEvent.createFromDataContext(ActionPlaces.ACTION_SEARCH, null, dataContext));
        appendActionsFromProvider(pattern, matcher, result, seenActions, Arrays.asList(children), dataContext, title);
      }
      else {
        MatchMode mode = myModel.actionMatches(pattern, matcher, action);
        if (mode != MatchMode.NONE) {
          result.add(new ActionWrapper(action, GroupMapping.createFromText(title, true), mode, myModel));
        }
      }
    }
  }

  public void clearIntentions() {
    myIntentions.drop();
  }

  @NotNull
  static Matcher buildMatcher(String pattern) {
    return pattern.contains(" ") ? new WordPrefixMatcher(pattern) : NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  private boolean processIntentions(String pattern, Processor<? super MatchedValue> consumer) {
    Matcher matcher = buildMatcher(pattern);
    Map<@ActionText String, ApplyIntentionAction> intentionMap = myIntentions.getValue();
    JBIterable<ActionWrapper> intentions = JBIterable.from(intentionMap.keySet())
      .filterMap(intentionText -> {
        ApplyIntentionAction intentionAction = intentionMap.get(intentionText);
        if (myModel.actionMatches(pattern, matcher, intentionAction) == MatchMode.NONE) return null;
        GroupMapping groupMapping = GroupMapping.createFromText(intentionText, false);
        return new ActionWrapper(intentionAction, groupMapping, MatchMode.INTENTION, myModel);
      });
    return processItems(pattern, intentions, consumer);
  }

  @NotNull
  private ActionWrapper wrapAnAction(@NotNull AnAction action) {
    return new ActionWrapper(action, myModel.getGroupMapping(action), MatchMode.NAME, myModel);
  }

  private static final Logger LOG = Logger.getInstance(GotoActionItemProvider.class);

  private static boolean processItems(String pattern, JBIterable<?> items, Processor<? super MatchedValue> consumer) {
    MinusculeMatcher matcher = buildWeightMatcher(pattern);
    List<MatchedValue> matched = ContainerUtil.newArrayList(items.map(o -> {
      if (o instanceof MatchedValue) return (MatchedValue)o;

      Integer weight = calcElementWeight(o, pattern, matcher);
      return weight != null ? new MatchedValue(o, pattern, weight) : new MatchedValue(o, pattern);
    }));
    try {
      matched.sort((o1, o2) -> o1.compareWeights(o2));
    }
    catch (IllegalArgumentException e) {
      LOG.error("Comparison method violates its general contract with pattern '" + pattern + "'", e);
    }
    return ContainerUtil.process(matched, consumer);
  }

  @Nullable
  private static Integer calcElementWeight(Object element, String pattern, MinusculeMatcher matcher) {
    Integer degree = calculateDegree(matcher, getActionText(element));
    if (degree == null) return null;

    if (Experiments.getInstance().isFeatureEnabled("i18n.match.actions")) {
      if (degree == 0) {
        degree = calculateDegree(matcher, DefaultBundleService.getInstance().compute(() -> getAnActionOriginalText(getAction(element))));
        if (degree == null) return null;
      }
    }

    if (pattern.trim().contains(" ")) degree += BONUS_FOR_SPACE_IN_PATTERN;
    if (element instanceof OptionDescription && degree > 0) degree -= SETTINGS_PENALTY;

    return Math.max(degree, 0);
  }

  @Nullable
  private static Integer calculateDegree(MinusculeMatcher matcher, @Nullable String text) {
    if (text == null) return null;
    return matcher.matchingDegree(text);
  }

  private static MinusculeMatcher buildWeightMatcher(String pattern) {
    return NameUtil.buildMatcher("*" + pattern)
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build();
  }

  @Nullable
  @Nls
  public static String getActionText(Object value) {
    if (value instanceof OptionDescription) return ((OptionDescription)value).getHit();
    if (value instanceof AnAction) return getAnActionText((AnAction)value);
    if (value instanceof ActionWrapper) return getAnActionText(((ActionWrapper)value).getAction());
    return null;
  }

  @Nullable
  private static AnAction getAction(Object value) {
    if (value instanceof AnAction) {
      return (AnAction)value;
    }
    else if (value instanceof ActionWrapper) {
      return ((ActionWrapper)value).getAction();
    }
    return null;
  }

  @Nullable
  @Nls
  private static String getAnActionText(AnAction value) {
    Presentation presentation = value.getTemplatePresentation().clone();
    value.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation);
    return presentation.getText();
  }

  private static @Nullable String getAnActionOriginalText(@Nullable AnAction value) {
    if (value == null) return null;
    Presentation presentation = value.getTemplatePresentation().clone();
    value.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation);
    TextWithMnemonic mnemonic = presentation.getTextWithPossibleMnemonic().get();
    if (mnemonic == null) return null;

    return mnemonic.getText();
  }
}

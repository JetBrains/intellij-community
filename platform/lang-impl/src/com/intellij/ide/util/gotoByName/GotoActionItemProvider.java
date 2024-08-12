// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Processor;
import com.intellij.util.text.Matcher;
import kotlin.Unit;
import kotlin.sequences.Sequence;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.ide.util.gotoByName.GotoActionModel.*;

/**
 * @deprecated This class is marked for removal and should not be used. Please use {@link ActionAsyncProvider} instead
 */
@Deprecated(forRemoval = true)
public final class GotoActionItemProvider implements ChooseByNameWeightedItemProvider {

  private final ActionManager myActionManager = ActionManager.getInstance();
  private final GotoActionModel myModel;
  private final ClearableLazyValue<Map<String, ApplyIntentionAction>> myIntentions;

  public GotoActionItemProvider(GotoActionModel model) {
    myModel = model;
    myIntentions = ClearableLazyValue.create(() -> ReadAction.nonBlocking(() -> myModel.getAvailableIntentions()).executeSynchronously());
  }

  @Override
  public @NotNull List<String> filterNames(@NotNull ChooseByNameViewModel base, String @NotNull [] names, @NotNull String pattern) {
    return Collections.emptyList(); // no common prefix insertion in goto action
  }

  @Override
  public boolean filterElements(final @NotNull ChooseByNameViewModel base,
                                final @NotNull String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator cancelled,
                                final @NotNull Processor<Object> consumer) {
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

  public boolean filterElements(@NotNull String pattern, @NotNull Predicate<? super MatchedValue> consumer) {
    myModel.buildGroupMappings();

    return processAbbreviations(pattern, consumer) &&
           processActions(pattern, consumer) &&
           processTopHits(pattern, consumer) &&
           processIntentions(pattern, consumer) &&
           processOptions(pattern, consumer);
  }

  private boolean processAbbreviations(@NotNull String pattern, Predicate<? super MatchedValue> consumer) {
    MinusculeMatcher matcher = buildWeightMatcher(pattern);
    List<String> actionIds = AbbreviationManager.getInstance().findActions(pattern);
    Stream<? extends MatchedValue> wrappers = actionIds.stream()
      .map(actionId -> {
        AnAction action = myActionManager.getAction(actionId);
        if (action == null) {
          return null;
        }

        ActionWrapper wrapper = wrapAnAction(action);
        int degree = matcher.matchingDegree(pattern);
        return new MatchedValue(wrapper, pattern, degree, MatchedValueType.ABBREVIATION) {
          @Override
          public @NotNull String getValueText() {
            return pattern;
          }
        };
      })
      .filter(Objects::nonNull);
    return processItems(pattern, MatchedValueType.ABBREVIATION, wrappers, consumer);
  }

  private boolean processTopHits(String pattern, Predicate<? super MatchedValue> consumer) {
    Project project = myModel.getProject();
    CollectConsumer<Object> collector = new CollectConsumer<>();
    String commandAccelerator = SearchTopHitProvider.getTopHitAccelerator();
    for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensionList()) {
      //noinspection deprecation
      if (provider instanceof OptionsTopHitProvider.CoveredByToggleActions) {
        continue;
      }

      if (provider instanceof OptionsSearchTopHitProvider && !pattern.startsWith(commandAccelerator)) {
        String prefix = commandAccelerator + ((OptionsSearchTopHitProvider)provider).getId() + " ";
        provider.consumeTopHits(prefix + pattern, collector, project);
      }
      else if (project != null && provider instanceof OptionsTopHitProvider.ProjectLevelProvidersAdapter) {
        ((OptionsTopHitProvider.ProjectLevelProvidersAdapter)provider).blockingConsumeAllTopHits(pattern, it -> {
          collector.accept(it);
          return Unit.INSTANCE;
        }, project);
      }
      provider.consumeTopHits(pattern, collector, project);
    }

    Collection<Object> result = collector.getResult();
    Stream<?> wrappers = result.stream()
      .map(object -> object instanceof AnAction ? wrapAnAction((AnAction)object) : object);
    return processItems(pattern, MatchedValueType.TOP_HIT, wrappers, consumer);
  }

  private boolean processOptions(String pattern, Predicate<? super MatchedValue> consumer) {
    Map<@NonNls String, @NlsContexts.ConfigurableName String> map = myModel.getConfigurablesNames();
    SearchableOptionsRegistrarImpl registrar = (SearchableOptionsRegistrarImpl)SearchableOptionsRegistrar.getInstance();

    List<Object> options = new ArrayList<>();
    final Set<String> words = registrar.getProcessedWords(pattern);
    Set<OptionDescription> optionDescriptions = null;
    boolean filterOutInspections = Registry.is("go.to.action.filter.out.inspections", true);
    for (String word : words) {
      List<OptionDescription> descriptions = null;
      Sequence<@NotNull OptionDescription> s = registrar.findAcceptableDescriptions(word);
      if (s != null) {
        descriptions = new ArrayList<>();
        Iterator<OptionDescription> iterator = s.iterator();
        while (iterator.hasNext()) {
          descriptions.add(iterator.next());
        }
      }

      if (descriptions == null || descriptions.isEmpty()) {
        descriptions = List.of();
      }
      else {
        descriptions.removeIf(description -> {
          return "ActionManager".equals(description.getPath()) || (filterOutInspections && "Inspections".equals(description.getGroupName()));
        });
      }

      if (descriptions.isEmpty()) {
        optionDescriptions = null;
        break;
      }
      else if (optionDescriptions == null) {
        optionDescriptions = new HashSet<>(descriptions);
      }
      else {
        optionDescriptions.retainAll(descriptions);
      }
    }
    if (!Strings.isEmptyOrSpaces(pattern)) {
      Matcher matcher = ActionSearchUtilKt.buildMatcher(pattern);
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
        for (ActionFromOptionDescriptorProvider converter : ActionFromOptionDescriptorProvider.EP.getExtensionList()) {
          AnAction action = converter.provide(description);
          if (action != null) {
            options.add(createActionWrapper(action, null, MatchMode.NAME, myModel));
          }
        }
        options.add(description);
      }
    }
    return processItems(pattern, MatchedValueType.OPTION, options.stream(), consumer);
  }

  private boolean processActions(String pattern, Predicate<? super MatchedValue> consumer) {
    return processActions(pattern, consumer, ((ActionManagerImpl)myActionManager).getActionIds());
  }

  public boolean processActions(String pattern, Predicate<? super MatchedValue> consumer, @NotNull Set<String> ids) {
    Stream<AnAction> actions = ids.stream().map(myActionManager::getAction).filter(Objects::nonNull);
    Matcher matcher = ActionSearchUtilKt.buildMatcher(pattern);

    QuickActionProvider provider = myModel.getDataContext().getData(QuickActionProvider.KEY);
    if (provider != null) {
      actions = Stream.concat(actions, provider.getActions(true).stream());
    }

    Stream<ActionWrapper> actionWrappers = actions
      .distinct()
      .map(action -> {
        if (action instanceof ActionGroup && !((ActionGroup)action).isSearchable()) {
          return null;
        }

        MatchMode mode = myModel.actionMatches(pattern, matcher, action);
        if (mode == MatchMode.NONE) {
          return null;
        }
        return createActionWrapper(action, myModel.getGroupMapping(action), mode, myModel);
      })
      .filter(Objects::nonNull);
    return processItems(pattern, MatchedValueType.ACTION, actionWrappers, consumer);
  }

  public void clearIntentions() {
    myIntentions.drop();
  }

  private boolean processIntentions(String pattern, Predicate<? super MatchedValue> consumer) {
    Matcher matcher = ActionSearchUtilKt.buildMatcher(pattern);
    Map<@ActionText String, ApplyIntentionAction> intentionMap = myIntentions.getValue();
    Stream<ActionWrapper> intentions = intentionMap.keySet().stream()
      .map(intentionText -> {
        ApplyIntentionAction intentionAction = intentionMap.get(intentionText);
        if (myModel.actionMatches(pattern, matcher, intentionAction) == MatchMode.NONE) {
          return null;
        }
        GroupMapping groupMapping = GroupMapping.createFromText(intentionText, false);
        return createActionWrapper(intentionAction, groupMapping, MatchMode.INTENTION, myModel);
      })
      .filter(Objects::nonNull);
    return processItems(pattern, MatchedValueType.INTENTION, intentions, consumer);
  }

  private @NotNull ActionWrapper wrapAnAction(@NotNull AnAction action) {
    return createActionWrapper(action, myModel.getGroupMapping(action), MatchMode.NAME, myModel);
  }

  private static final Logger LOG = Logger.getInstance(GotoActionItemProvider.class);

  private static boolean processItems(String pattern, @NotNull MatchedValueType type,
                                      @NotNull Stream<?> items, Predicate<? super MatchedValue> consumer) {
    MinusculeMatcher matcher = buildWeightMatcher(pattern);
    List<MatchedValue> matched = items.map(o -> {
      if (o instanceof MatchedValue) {
        return (MatchedValue)o;
      }

      Integer weight = ActionSearchUtilKt.calcElementWeight(o, pattern, matcher);
      return weight == null ? new MatchedValue(o, pattern, type) : new MatchedValue(o, pattern, weight, type);
    }).collect(Collectors.toList());

    try {
      matched.sort((o1, o2) -> o1.compareWeights(o2));
    }
    catch (IllegalArgumentException e) {
      LOG.error("Comparison method violates its general contract with pattern '" + pattern + "'", e);
    }

    for (MatchedValue t : matched) {
      if (!consumer.test(t)) {
        return false;
      }
    }
    return true;
  }

  private static MinusculeMatcher buildWeightMatcher(String pattern) {
    return NameUtil.buildMatcher("*" + pattern)
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build();
  }

  public static @NotNull ActionWrapper createActionWrapper(@NotNull AnAction action,
                                                           @Nullable GroupMapping groupMapping,
                                                           @NotNull MatchMode mode, GotoActionModel model) {
    Presentation presentation = ReadAction.nonBlocking(() -> {
        if (groupMapping != null) {
          groupMapping.updateBeforeShow(model.getUpdateSession());
        }
        return model.getUpdateSession().presentation(action);
      })
      .executeSynchronously();
    return new ActionWrapper(action, groupMapping, mode, presentation);
  }
}

/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.DataManager;
import com.intellij.ide.SearchTopHitProvider;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.ide.util.gotoByName.GotoActionModel.*;

/**
 * @author peter
 */
@SuppressWarnings("TestOnlyProblems")
public class GotoActionItemProvider implements ChooseByNameItemProvider {
  private final ActionManager myActionManager = ActionManager.getInstance();
  private final GotoActionModel myModel;
  private final NotNullLazyValue<Map<String, ApplyIntentionAction>> myIntentions;

  public GotoActionItemProvider(GotoActionModel model) {
    myModel = model;
    myIntentions = NotNullLazyValue.createValue(() -> myModel.getAvailableIntentions());
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    return Collections.emptyList(); // no common prefix insertion in goto action
  }

  @Override
  public boolean filterElements(@NotNull final ChooseByNameBase base,
                                @NotNull final String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator cancelled,
                                @NotNull final Processor<Object> consumer) {
    return filterElements(pattern, value -> {
      if (!everywhere && value.value instanceof ActionWrapper && !((ActionWrapper)value.value).isAvailable()) {
        return true;
      }
      return consumer.process(value);
    });
  }

  public boolean filterElements(String pattern, Processor<MatchedValue> consumer) {
    DataContext dataContext = DataManager.getInstance().getDataContext(myModel.getContextComponent());

    if (!processAbbreviations(pattern, consumer, dataContext)) return false;
    if (!processIntentions(pattern, consumer, dataContext)) return false;
    if (!processActions(pattern, consumer, dataContext)) return false;
    if (Registry.is("goto.action.skip.tophits.and.options")) return true;
    if (!processTopHits(pattern, consumer, dataContext)) return false;
    if (!processOptions(pattern, consumer, dataContext)) return false;

    return true;
  }

  private boolean processAbbreviations(final String pattern, Processor<MatchedValue> consumer, DataContext context) {
    List<String> actionIds = AbbreviationManager.getInstance().findActions(pattern);
    JBIterable<MatchedValue> wrappers = JBIterable.from(actionIds)
      .transform(myActionManager::getAction)
      .filter(Condition.NOT_NULL)
      .transform(action -> {
        ActionWrapper wrapper = new ActionWrapper(action, myModel.myActionGroups.get(action), MatchMode.NAME, context, myModel);
        return new MatchedValue(wrapper, pattern) {
          @NotNull
          @Override
          public String getValueText() {
            return pattern;
          }
        };
      });
    return processItems(pattern, wrappers, consumer);
  }

  private static boolean processTopHits(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final CollectConsumer<Object> collector = new CollectConsumer<>();
    for (SearchTopHitProvider provider : SearchTopHitProvider.EP_NAME.getExtensions()) {
      //noinspection deprecation
      if (provider instanceof OptionsTopHitProvider.CoveredByToggleActions) continue;
      if (provider instanceof OptionsTopHitProvider && !((OptionsTopHitProvider)provider).isEnabled(project)) continue;
      if (provider instanceof OptionsTopHitProvider && !StringUtil.startsWith(pattern, "#")) {
        String prefix = "#" + ((OptionsTopHitProvider)provider).getId() + " ";
        provider.consumeTopHits(prefix + pattern, collector, project);
      }
      provider.consumeTopHits(pattern, collector, project);
    }
    Collection<Object> result = collector.getResult();
    return processItems(pattern, JBIterable.from(result).filter(Comparable.class), consumer);
  }

  private boolean processOptions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    Map<String, String> map = myModel.getConfigurablesNames();
    SearchableOptionsRegistrarImpl registrar = (SearchableOptionsRegistrarImpl)SearchableOptionsRegistrar.getInstance();

    List<Comparable> options = ContainerUtil.newArrayList();
    final Set<String> words = registrar.getProcessedWords(pattern);
    Set<OptionDescription> optionDescriptions = null;
    final String actionManagerName = myActionManager.getComponentName();
    for (String word : words) {
      final Set<OptionDescription> descriptions = registrar.getAcceptableDescriptions(word);
      if (descriptions != null) {
        for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
          OptionDescription description = iterator.next();
          if (actionManagerName.equals(description.getPath())) {
            iterator.remove();
          }
        }
        if (!descriptions.isEmpty()) {
          if (optionDescriptions == null) {
            optionDescriptions = descriptions;
          }
          else {
            optionDescriptions.retainAll(descriptions);
          }
        }
      } else {
        optionDescriptions = null;
        break;
      }
    }
    if (!StringUtil.isEmptyOrSpaces(pattern)) {
      Matcher matcher = NameUtil.buildMatcher("*" + pattern).build();
      if (optionDescriptions == null) optionDescriptions = ContainerUtil.newTroveSet();
      for (Map.Entry<String, String> entry : map.entrySet()) {
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
          if (action != null) options.add(new ActionWrapper(action, null, MatchMode.NAME, dataContext, myModel));
          options.add(description);
        }
      }
    }
    return processItems(pattern, JBIterable.from(options), consumer);
  }

  private boolean processActions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
    JBIterable<AnAction> actions = JBIterable.from(ids).transform(myActionManager::getAction).filter(Condition.NOT_NULL);
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);

    QuickActionProvider provider = dataContext.getData(QuickActionProvider.KEY);
    if (provider != null) {
      actions = actions.append(provider.getActions(true));
    }

    JBIterable<ActionWrapper> actionWrappers = actions.unique().transform(action -> {
      MatchMode mode = myModel.actionMatches(pattern, matcher, action);
      if (mode == MatchMode.NONE) return null;
      return new ActionWrapper(action, myModel.myActionGroups.get(action), mode, dataContext, myModel);
    }).filter(Condition.NOT_NULL);
    return processItems(pattern, actionWrappers, consumer);
  }

  private boolean processIntentions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
    Map<String, ApplyIntentionAction> intentionMap = myIntentions.getValue();
    JBIterable<ActionWrapper> intentions = JBIterable.from(intentionMap.keySet())
      .transform(intentionText -> {
        ApplyIntentionAction intentionAction = intentionMap.get(intentionText);
        if (myModel.actionMatches(pattern, matcher, intentionAction) == MatchMode.NONE) return null;
        return new ActionWrapper(intentionAction, intentionText, MatchMode.INTENTION, dataContext, myModel);
      })
      .filter(Condition.NOT_NULL);
    return processItems(pattern, intentions, consumer);
  }

  private static boolean processItems(String pattern, JBIterable<? extends Comparable> items, Processor<MatchedValue> consumer) {
    List<MatchedValue> matched = ContainerUtil.newArrayList(items.map(o -> o instanceof MatchedValue ? (MatchedValue)o : new MatchedValue(o, pattern)));
    Collections.sort(matched);
    return ContainerUtil.process(matched, consumer);
  }
}

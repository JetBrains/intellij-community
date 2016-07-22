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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.ide.util.gotoByName.GotoActionModel.*;

/**
 * @author peter
 */
@SuppressWarnings("TestOnlyProblems")
public class GotoActionItemProvider implements ChooseByNameItemProvider {
  private final ActionManager myActionManager = ActionManager.getInstance();
  protected final SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
  private final GotoActionModel myModel;

  public GotoActionItemProvider(GotoActionModel model) {
    myModel = model;
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
    return filterElements(pattern, everywhere, consumer::process);
  }

  public boolean filterElements(String pattern, boolean everywhere, Processor<MatchedValue> consumer) {
    DataContext dataContext = DataManager.getInstance().getDataContext(myModel.getContextComponent());

    if (!processAbbreviations(pattern, consumer, dataContext)) return false;
    if (!processIntentions(pattern, consumer, dataContext)) return false;
    if (!processActions(pattern, everywhere, consumer, dataContext)) return false;
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
        ActionWrapper wrapper = new ActionWrapper(action, myModel.myActionGroups.get(action), MatchMode.NAME, context);
        return new MatchedValue(wrapper, pattern) {
          @Nullable
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
    List<Comparable> options = ContainerUtil.newArrayList();
    final Set<String> words = myIndex.getProcessedWords(pattern);
    Set<OptionDescription> optionDescriptions = null;
    final String actionManagerName = myActionManager.getComponentName();
    for (String word : words) {
      final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
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
          if (action != null) options.add(new ActionWrapper(action, null, MatchMode.NAME, dataContext));
          options.add(description);
        }
      }
    }
    return processItems(pattern, JBIterable.from(options), consumer);
  }

  private boolean processActions(String pattern, boolean everywhere, Processor<MatchedValue> consumer, DataContext dataContext) {
    JBIterable<AnAction> actions;
    if (everywhere) {
      Set<String> ids = ((ActionManagerImpl)myActionManager).getActionIds();
      actions = JBIterable.from(ids).transform(myActionManager::getAction).filter(Condition.NOT_NULL);
    }
    else {
      actions = JBIterable.from(myModel.myActionGroups.keySet());
    }
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
    JBIterable<ActionWrapper> actionWrappers = actions.transform(action -> {
      MatchMode mode = myModel.actionMatches(pattern, matcher, action);
      if (mode == MatchMode.NONE) return null;
      return new ActionWrapper(action, myModel.myActionGroups.get(action), mode, dataContext);
    }).filter(Condition.NOT_NULL);
    return processItems(pattern, actionWrappers, consumer);
  }

  private boolean processIntentions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    MinusculeMatcher matcher = NameUtil.buildMatcher("*" + pattern, NameUtil.MatchingCaseSensitivity.NONE);
    JBIterable<ActionWrapper> intentions = JBIterable.from(myModel.myIntentions.keySet())
      .transform(intentionText -> {
        ApplyIntentionAction intentionAction = myModel.myIntentions.get(intentionText);
        if (myModel.actionMatches(pattern, matcher, intentionAction) == MatchMode.NONE) return null;
        return new ActionWrapper(intentionAction, intentionText, MatchMode.INTENTION, dataContext);
      })
      .filter(Condition.NOT_NULL);
    return processItems(pattern, intentions, consumer);
  }

  private static boolean processItems(String pattern, JBIterable<? extends Comparable> items, Processor<MatchedValue> consumer) {
    ArrayList<MatchedValue> matched = ContainerUtil.newArrayList();
    items.transform(o -> {
      ProgressManager.checkCanceled();
      return o instanceof MatchedValue ? (MatchedValue)o : new MatchedValue(o, pattern);
    }).addAllTo(matched);
    Collections.sort(matched);
    return ContainerUtil.process(matched, consumer);
  }

}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.telemetry.IJTracer;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.Weigher;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.diagnostic.telemetry.TraceKt.runWithSpan;

/**
 * @author peter
 */
public final class CompletionServiceImpl extends BaseCompletionService {
  private static final Logger LOG = Logger.getInstance(CompletionServiceImpl.class);

  private static final CompletionPhaseHolder DEFAULT_PHASE_HOLDER = new CompletionPhaseHolder(CompletionPhase.NoCompletion, null);
  private static final Map<ClientId, CompletionPhaseHolder> clientId2Holders = new ConcurrentHashMap<>();

  private final IJTracer myCompletionTracer = TraceManager.INSTANCE.getTracer("codeCompletion");

  public CompletionServiceImpl() {
    super();
    SimpleMessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().simpleConnect();
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        List<ClientId> clientIds = new ArrayList<>(clientId2Holders.keySet());  // original set might be modified during iteration
        for (ClientId clientId : clientIds) {
          try (AccessToken ignored = ClientId.withClientId(clientId)) {
            CompletionProgressIndicator indicator = getCurrentCompletionProgressIndicator(clientId);
            if (indicator != null && indicator.getProject() == project) {
              indicator.closeAndFinish(true);
              setCompletionPhase(clientId, CompletionPhase.NoCompletion);
            }
            else if (indicator == null) {
              setCompletionPhase(clientId, CompletionPhase.NoCompletion);
            }
          }
        }
      }
    });
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        List<ClientId> clientIds = new ArrayList<>(clientId2Holders.keySet());  // original set might be modified during iteration
        for (ClientId clientId : clientIds) {
          try (AccessToken ignored = ClientId.withClientId(clientId)) {
            setCompletionPhase(clientId, CompletionPhase.NoCompletion);
          }
        }
      }
    });
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public void setAdvertisementText(@NlsContexts.PopupAdvertisement @Nullable final String text) {
    setAdvertisementText(ClientId.getCurrent(), text);
  }

  private static void setAdvertisementText(@NotNull ClientId clientId, @NlsContexts.PopupAdvertisement @Nullable final String text) {
    if (text == null) return;
    final CompletionProgressIndicator completion = getCurrentCompletionProgressIndicator(clientId);
    if (completion != null) {
      completion.addAdvertisement(text, null);
    }
  }

  @Override
  protected CompletionResultSet createResultSet(@NotNull CompletionParameters parameters,
                                                @NotNull Consumer<? super CompletionResult> consumer,
                                                @NotNull CompletionContributor contributor,
                                                @NotNull PrefixMatcher matcher) {
    return new CompletionResultSetImpl(consumer, matcher, contributor, parameters, null, null);
  }

  @Override
  public CompletionProcess getCurrentCompletion() {
    return getCurrentCompletion(ClientId.getCurrent());
  }

  private CompletionProcess getCurrentCompletion(@NotNull ClientId clientId) {
    CompletionProgressIndicator indicator = getCurrentCompletionProgressIndicator(clientId);
    if (indicator != null) {
      return indicator;
    }
    return clientId.equals(ClientId.getLocalId()) ? myApiCompletionProcess : null;
  }

  public static CompletionProgressIndicator getCurrentCompletionProgressIndicator() {
    return getCurrentCompletionProgressIndicator(ClientId.getCurrent());
  }

  private static CompletionProgressIndicator getCurrentCompletionProgressIndicator(@NotNull ClientId clientId) {
    return getCurrentCompletionProgressIndicator(getCompletionPhase(clientId));
  }

  private static CompletionProgressIndicator getCurrentCompletionProgressIndicator(@NotNull CompletionPhase phase) {
    if (isPhase(phase, CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class,
                CompletionPhase.CommittingDocuments.class, CompletionPhase.Synchronous.class)) {
      return phase.indicator;
    }
    return null;
  }

  private static class CompletionResultSetImpl extends BaseCompletionResultSet {
    CompletionResultSetImpl(Consumer<? super CompletionResult> consumer, PrefixMatcher prefixMatcher,
                            CompletionContributor contributor, CompletionParameters parameters,
                            @Nullable CompletionSorter sorter, @Nullable CompletionResultSetImpl original) {
      super(consumer, prefixMatcher, contributor, parameters, sorter, original);
    }

    @Override
    public void addAllElements(@NotNull Iterable<? extends LookupElement> elements) {
      CompletionThreadingBase.withBatchUpdate(() -> super.addAllElements(elements), myParameters.getProcess());
    }

    @Override
    public void passResult(@NotNull CompletionResult result) {
      LookupElement element = result.getLookupElement();
      if (element != null && element.getUserData(LOOKUP_ELEMENT_CONTRIBUTOR) == null) {
        element.putUserData(LOOKUP_ELEMENT_CONTRIBUTOR, myContributor);
      }
      super.passResult(result);
    }

    @Override
    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      if (matcher.equals(getPrefixMatcher())) {
        return this;
      }

      return new CompletionResultSetImpl(getConsumer(), matcher, myContributor, myParameters, mySorter, this);
    }

    @NotNull
    @Override
    public CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
      return new CompletionResultSetImpl(getConsumer(), getPrefixMatcher(), myContributor, myParameters, sorter, this);
    }

    @Override
    public void addLookupAdvertisement(@NotNull String text) {
      getCompletionService().setAdvertisementText(text);
    }

    @Override
    public void restartCompletionOnPrefixChange(ElementPattern<String> prefixCondition) {
      CompletionProcess process = myParameters.getProcess();
      if (process instanceof CompletionProcessBase) {
        ((CompletionProcessBase)process)
          .addWatchedPrefix(myParameters.getOffset() - getPrefixMatcher().getPrefix().length(), prefixCondition);
      }
    }

    @Override
    public void restartCompletionWhenNothingMatches() {
      CompletionProcess process = myParameters.getProcess();
      if (process instanceof CompletionProgressIndicator) {
        ((CompletionProgressIndicator)process).getLookup().setStartCompletionWhenNothingMatches(true);
      }
    }
  }

  @SafeVarargs
  public static void assertPhase(Class<? extends CompletionPhase> @NotNull ... possibilities) {
    assertPhase(ClientId.getCurrent(), possibilities);
  }

  @SafeVarargs
  private static void assertPhase(@NotNull ClientId clientId, Class<? extends CompletionPhase> @NotNull ... possibilities) {
    assertPhase(clientId, getCompletionPhaseHolder(clientId), possibilities);
  }

  @SafeVarargs
  private static void assertPhase(@NotNull ClientId clientId,
                                  @NotNull CompletionPhaseHolder phaseHolder,
                                  Class<? extends CompletionPhase> @NotNull ... possibilities) {
    if (!isPhase(phaseHolder.getPhase(), possibilities)) {
      reportPhase(clientId, phaseHolder);
    }
  }

  private static void reportPhase(@NotNull ClientId clientId, @NotNull CompletionPhaseHolder phaseHolder) {
    Throwable phaseTrace = phaseHolder.getPhaseTrace();
    String traceText = phaseTrace != null ? "; set at " + ExceptionUtil.getThrowableText(phaseTrace) : "";
    LOG.error(phaseHolder.getPhase() + "; " + clientId + traceText);
  }

  @SafeVarargs
  public static boolean isPhase(Class<? extends CompletionPhase> @NotNull ... possibilities) {
    return isPhase(ClientId.getCurrent(), possibilities);
  }

  @SafeVarargs
  private static boolean isPhase(@NotNull ClientId clientId, Class<? extends CompletionPhase> @NotNull ... possibilities) {
    return isPhase(getCompletionPhase(clientId), possibilities);
  }

  @SafeVarargs
  private static boolean isPhase(@NotNull CompletionPhase phase, Class<? extends CompletionPhase> @NotNull ... possibilities) {
    for (Class<? extends CompletionPhase> possibility : possibilities) {
      if (possibility.isInstance(phase)) {
        return true;
      }
    }
    return false;
  }

  public static void setCompletionPhase(@NotNull CompletionPhase phase) {
    setCompletionPhase(ClientId.getCurrent(), phase);
  }

  private static void setCompletionPhase(@NotNull ClientId clientId, @NotNull CompletionPhase phase) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CompletionPhase oldPhase = getCompletionPhase(clientId);
    CompletionProgressIndicator oldIndicator = oldPhase.indicator;
    if (oldIndicator != null &&
        !(phase instanceof CompletionPhase.BgCalculation) &&
        oldIndicator.isRunning() &&
        !oldIndicator.isCanceled()) {
      LOG.error("don't change phase during running completion: oldPhase=" + oldPhase);
    }
    boolean wasCompletionRunning = isRunningPhase(oldPhase);
    boolean isCompletionRunning = isRunningPhase(phase);
    if (isCompletionRunning != wasCompletionRunning) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(CompletionPhaseListener.TOPIC)
        .completionPhaseChanged(isCompletionRunning);
    }

    Disposer.dispose(oldPhase);
    if (isPhase(phase, CompletionPhase.NoCompletion.getClass()) && !ClientId.isValid(clientId)) {
      clientId2Holders.remove(clientId);
      return;
    }
    Throwable phaseTrace = new Throwable();
    CompletionPhaseHolder holder = new CompletionPhaseHolder(phase, phaseTrace);
    CompletionPhaseHolder previous = clientId2Holders.put(clientId, holder);
    if (previous == null) {
      Disposer.register(ClientId.toDisposable(clientId), () -> {
        if (isPhase(clientId, CompletionPhase.NoCompletion.getClass())) {
          clientId2Holders.remove(clientId);
        }
        // Otherwise it's still used (e.g. by CompletionProgressIndicator)
      });
    }
  }

  private static boolean isRunningPhase(@NotNull CompletionPhase phase) {
    return phase != CompletionPhase.NoCompletion && !(phase instanceof CompletionPhase.ZombiePhase) &&
           !(phase instanceof CompletionPhase.ItemsCalculated);
  }

  public static @NotNull CompletionPhase getCompletionPhase() {
    return getCompletionPhase(ClientId.getCurrent());
  }

  private static @NotNull CompletionPhase getCompletionPhase(@NotNull ClientId clientId) {
    return getCompletionPhaseHolder(clientId).getPhase();
  }

  private static @NotNull CompletionPhaseHolder getCompletionPhaseHolder(@NotNull ClientId clientId) {
    return clientId2Holders.getOrDefault(clientId, DEFAULT_PHASE_HOLDER);
  }

  @NotNull
  @Override
  protected CompletionSorterImpl addWeighersBefore(@NotNull CompletionSorterImpl sorter) {
    CompletionSorterImpl processed = super.addWeighersBefore(sorter);
    return processed.withClassifier(CompletionSorterImpl.weighingFactory(new LiveTemplateWeigher()));
  }

  @NotNull
  @Override
  protected CompletionSorterImpl processStatsWeigher(@NotNull CompletionSorterImpl sorter,
                                                     @NotNull Weigher weigher,
                                                     @NotNull CompletionLocation location) {
    CompletionSorterImpl processedSorter = super.processStatsWeigher(sorter, weigher, location);
    return processedSorter.withClassifier(new ClassifierFactory<>("stats") {
      @Override
      public Classifier<LookupElement> createClassifier(Classifier<LookupElement> next) {
        return new StatisticsWeigher.LookupStatisticsWeigher(location, next);
      }
    });
  }

  @Override
  protected void getVariantsFromContributor(CompletionParameters params, CompletionContributor contributor, CompletionResultSet result) {
    runWithSpan(myCompletionTracer, contributor.getClass().getSimpleName(), span -> {
      super.getVariantsFromContributor(params, contributor, result);
    });
  }

  @Override
  public void performCompletion(CompletionParameters parameters, Consumer<? super CompletionResult> consumer) {
    runWithSpan(myCompletionTracer, "performCompletion", span -> {
      var countingConsumer = new Consumer<CompletionResult>() {
        int count = 0;

        @Override
        public void consume(CompletionResult result) {
          count++;
          consumer.consume(result);
        }
      };

      super.performCompletion(parameters, countingConsumer);

      span.setAttribute("lookupsFound", countingConsumer.count);
    });
  }

  private static class CompletionPhaseHolder {
    private final @NotNull CompletionPhase ourPhase;
    private final @Nullable Throwable ourPhaseTrace;

    CompletionPhaseHolder(@NotNull CompletionPhase phase, @Nullable Throwable phaseTrace) {
      ourPhase = phase;
      ourPhaseTrace = phaseTrace;
    }

    @NotNull CompletionPhase getPhase() {
      return ourPhase;
    }

    @Nullable Throwable getPhaseTrace() {
      return ourPhaseTrace;
    }
  }
}

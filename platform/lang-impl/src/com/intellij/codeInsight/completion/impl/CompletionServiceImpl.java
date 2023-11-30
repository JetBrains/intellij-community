// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.Classifier;
import com.intellij.codeInsight.lookup.ClassifierFactory;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.client.ClientKind;
import com.intellij.openapi.client.ClientSessionsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.patterns.ElementPattern;
import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.psi.Weigher;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.util.CodeCompletionKt.CodeCompletion;
import static com.intellij.platform.diagnostic.telemetry.helpers.TraceKt.runWithSpan;

/**
 * @author peter
 */
public class CompletionServiceImpl extends BaseCompletionService {
  private static final Logger LOG = Logger.getInstance(CompletionServiceImpl.class);

  private static final CompletionPhaseHolder DEFAULT_PHASE_HOLDER = new CompletionPhaseHolder(CompletionPhase.NoCompletion, null);
  private final IJTracer myCompletionTracer = TelemetryManager.getInstance().getTracer(CodeCompletion);

  private static final class ClientCompletionService implements Disposable {
    public static @Nullable ClientCompletionService tryGetInstance(@Nullable ClientAppSession session) {
      if (session == null) {
        return null;
      }
      return session.getService(ClientCompletionService.class);
    }

    private final @NotNull ClientAppSession myAppSession;

    private volatile @NotNull CompletionPhaseHolder myPhaseHolder = DEFAULT_PHASE_HOLDER;

    ClientCompletionService(@NotNull ClientAppSession appSession) {
      myAppSession = appSession;
    }

    @Override
    public void dispose() {
      Disposer.dispose(myPhaseHolder.phase);
    }

    public void setCompletionPhase(@NotNull CompletionPhase phase) {
      // wrap explicitly with client id for the case when some called API depends on ClientId.current
      try (AccessToken ignored = ClientId.withClientId(myAppSession.getClientId())) {
        ThreadingAssertions.assertEventDispatchThread();
        CompletionPhase oldPhase = getCompletionPhase();
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
        Throwable phaseTrace = new Throwable();
        myPhaseHolder = new CompletionPhaseHolder(phase, phaseTrace);
      }
    }

    public @NotNull CompletionPhase getCompletionPhase() {
      return getCompletionPhaseHolder().phase;
    }

    public @NotNull CompletionPhaseHolder getCompletionPhaseHolder() {
      return myPhaseHolder;
    }

    public CompletionProgressIndicator getCurrentCompletionProgressIndicator() {
      return getCurrentCompletionProgressIndicator(getCompletionPhase());
    }

    public CompletionProgressIndicator getCurrentCompletionProgressIndicator(@NotNull CompletionPhase phase) {
      if (isPhase(phase, CompletionPhase.BgCalculation.class, CompletionPhase.ItemsCalculated.class,
                  CompletionPhase.CommittingDocuments.class, CompletionPhase.Synchronous.class)) {
        return phase.indicator;
      }
      return null;
    }
  }

  public CompletionServiceImpl() {
    super();
    SimpleMessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().simpleConnect();
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        List<ClientAppSession> sessions = ClientSessionsManager.getAppSessions(ClientKind.ALL);
        for (ClientAppSession session : sessions) {
          ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(session);
          if (clientCompletionService == null)
            continue;

          CompletionProgressIndicator indicator = clientCompletionService.getCurrentCompletionProgressIndicator();
          if (indicator != null && indicator.getProject() == project) {
            indicator.closeAndFinish(true);
            clientCompletionService.setCompletionPhase(CompletionPhase.NoCompletion);
          }
          else if (indicator == null) {
            clientCompletionService.setCompletionPhase(CompletionPhase.NoCompletion);
          }
        }
      }
    });
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        List<ClientAppSession> sessions = ClientSessionsManager.getAppSessions(ClientKind.ALL);
        for (ClientAppSession session : sessions) {
          ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(session);
          if (clientCompletionService == null)
            continue;
          clientCompletionService.setCompletionPhase(CompletionPhase.NoCompletion);
        }
      }
    });
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public void setAdvertisementText(final @NlsContexts.PopupAdvertisement @Nullable String text) {
    if (text == null) return;
    final CompletionProgressIndicator completion = getCurrentCompletionProgressIndicator();
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
    CompletionProgressIndicator indicator = getCurrentCompletionProgressIndicator();
    if (indicator != null) {
      return indicator;
    }
    // TODO we have to move myApiCompletionProcess inside per client service somehow
    // also shouldn't we delegate here to base method instead of accessing the field of the base class?
    return ClientId.isCurrentlyUnderLocalId() ? myApiCompletionProcess : null;
  }

  public static @Nullable CompletionProgressIndicator getCurrentCompletionProgressIndicator() {
    ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(ClientSessionsManager.getAppSession());
    if (clientCompletionService == null)
      return null;
    return clientCompletionService.getCurrentCompletionProgressIndicator();
  }

  private static final class CompletionResultSetImpl extends BaseCompletionResultSet {
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
    public @NotNull CompletionResultSet withPrefixMatcher(final @NotNull PrefixMatcher matcher) {
      if (matcher.equals(getPrefixMatcher())) {
        return this;
      }

      return new CompletionResultSetImpl(getConsumer(), matcher, myContributor, myParameters, mySorter, this);
    }

    @Override
    public @NotNull CompletionResultSet withRelevanceSorter(@NotNull CompletionSorter sorter) {
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
    ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(ClientSessionsManager.getAppSession());
    CompletionPhaseHolder holder =
      clientCompletionService != null ? clientCompletionService.getCompletionPhaseHolder() : DEFAULT_PHASE_HOLDER;
    assertPhase(holder, possibilities);
  }

  @SafeVarargs
  private static void assertPhase(@NotNull CompletionPhaseHolder phaseHolder,
                           Class<? extends CompletionPhase> @NotNull ... possibilities) {
    if (!isPhase(phaseHolder.phase(), possibilities)) {
      reportPhase(phaseHolder);
    }
  }

  private static void reportPhase(@NotNull CompletionPhaseHolder phaseHolder) {
    Throwable phaseTrace = phaseHolder.phaseTrace();
    String traceText = phaseTrace != null ? "; set at " + ExceptionUtil.getThrowableText(phaseTrace) : "";
    LOG.error(phaseHolder.phase() + "; " + ClientId.getCurrent() + traceText);
  }

  @SafeVarargs
  public static boolean isPhase(Class<? extends CompletionPhase> @NotNull ... possibilities) {
    return isPhase(getCompletionPhase(), possibilities);
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
    ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(ClientSessionsManager.getAppSession());
    if (clientCompletionService == null)
      return;
    clientCompletionService.setCompletionPhase(phase);
  }

  private static boolean isRunningPhase(@NotNull CompletionPhase phase) {
    return phase != CompletionPhase.NoCompletion && !(phase instanceof CompletionPhase.ZombiePhase) &&
           !(phase instanceof CompletionPhase.ItemsCalculated);
  }

  public static @NotNull CompletionPhase getCompletionPhase() {
    ClientCompletionService clientCompletionService = ClientCompletionService.tryGetInstance(ClientSessionsManager.getAppSession());
    if (clientCompletionService == null)
      return DEFAULT_PHASE_HOLDER.phase;
    return clientCompletionService.getCompletionPhase();
  }

  @Override
  protected @NotNull CompletionSorterImpl addWeighersBefore(@NotNull CompletionSorterImpl sorter) {
    CompletionSorterImpl processed = super.addWeighersBefore(sorter);
    return processed.withClassifier(CompletionSorterImpl.weighingFactory(new LiveTemplateWeigher()));
  }

  @Override
  protected @NotNull CompletionSorterImpl processStatsWeigher(@NotNull CompletionSorterImpl sorter,
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
      span.setAttribute("avoid_null_value", true);
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

  private record CompletionPhaseHolder(@NotNull CompletionPhase phase, @Nullable Throwable phaseTrace) {
  }
}

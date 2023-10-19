// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores CPU profiler data for inspection run session and retrieve it later during the inspection pass
 * to optimize "run inspection tool-to-warning onscreen" latency
 */
@Service(Service.Level.PROJECT)
final class InspectionProfilerDataHolder {
  private static final Logger LOG = Logger.getInstance(InspectionProfilerDataHolder.class);
  // store all data locally to be able to clear fast
  private final Map<PsiFile, InspectionFileData> data = Collections.synchronizedMap(new FixedHashMap<>(10));

  /**
   * @param latencies                 array of 3 elements for (ERROR,WARNING,OTHER) latency infos
   * @param favoriteElements  a map (tool id -> {@link PsiElement} which produced some diagnostics during last run)
   */
  private record InspectionFileData(@NotNull Latencies @NotNull [] latencies,
                                    @NotNull Map<String, ? extends PsiElement> favoriteElements) {
  }

  static InspectionProfilerDataHolder getInstance(@NotNull Project project) {
    return project.getService(InspectionProfilerDataHolder.class);
  }

  private InspectionProfilerDataHolder(@NotNull Project project) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileChanged(@NotNull InspectionProfile profile) {
        clearProfileData();
      }

      @Override
      public void profileActivated(@Nullable InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        clearProfileData();
      }

      @Override
      public void profilesInitialized() {
        clearProfileData();
      }
    });
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        clearProfileData();
      }

      @Override
      public void projectClosing(@NotNull Project project) {
        clearProfileData();
      }
    });
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        clearProfileData();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        clearProfileData();
      }
    });
  }

  private void clearProfileData() {
    data.clear();
  }

  static final class Latencies {
    final Object2LongMap<String> idToLatency = new Object2LongOpenHashMap<>();
    // true if the passed latency won over the other saved in the map, and was saved as the new record
    void save(@NotNull InspectionRunner.InspectionContext context, long stampOf1st) {
      if (stampOf1st != 0) {
        long latency = stampOf1st - context.holder().initTimeStamp;
        idToLatency.mergeLong(context.tool().getID(), latency, (oldLatency, newLatency) -> Math.min(oldLatency, newLatency));
      }
    }

    String topSmallestLatenciesStat(@NotNull String mySeverity) {
      List<Pair<String, Long>> result = new ArrayList<>();
      int REPORT_TOP_N = 5;
      //noinspection unchecked
      Object2LongMap.Entry<String>[] entries = idToLatency.object2LongEntrySet().toArray(new Object2LongMap.Entry[0]);
      Arrays.sort(entries, Comparator.comparingLong(Object2LongMap.Entry::getLongValue));
      for (int i = 0; i < Math.min(REPORT_TOP_N, entries.length); i++) {
        Object2LongMap.Entry<String> entry = entries[i];
        result.add(Pair.create(entry.getKey(), entry.getLongValue()));
      }
      if (result.isEmpty()) return null; // no diagnostics at all at this severity
      String s = StringUtil.join(result, p -> String.format("    % 4dms (%s)", p.second/1_000_000, p.first), "\n");
      return "\n" +
             idToLatency.size() + " inspections reported at least one " +mySeverity+"; "+
             "top " + result.size() + " smallest latencies to the first "+mySeverity+":\n"+s;
    }
  }

  /**
   * after inspections completed, save their latencies (from corresponding {@link InspectionRunner.InspectionContext#holder})
   * to use later in {@link #sortAndRetrieveFavoriteElement(PsiFile, List)}
   */
  void saveStats(@NotNull PsiFile psiFile, @NotNull List<? extends InspectionRunner.InspectionContext> contexts, long totalHighlightingNanos) {
    if (!psiFile.getViewProvider().isPhysical()) {
      // ignore editor text fields/consoles etc
      return;
    }
    Latencies[] latencies = new Latencies[3]; // ERROR,WARNING,OTHER
    Map<String, PsiElement> favoriteElements = new HashMap<>();
    Arrays.setAll(latencies, __ -> new Latencies());
    for (InspectionRunner.InspectionContext context : contexts) {
      InspectionRunner.InspectionProblemHolder holder = context.holder();
      latencies[0].save(context, holder.errorStamp);
      latencies[1].save(context, holder.warningStamp);
      latencies[2].save(context, holder.otherStamp);
      PsiElement favoriteElement = holder.myFavoriteElement;
      if (favoriteElement != null) {
        // The first are visible contexts, then invisible contexts. Prefer the favorite element from the visible context.
        favoriteElements.putIfAbsent(context.tool().getID(), favoriteElement);
      }
    }
    data.put(psiFile, new InspectionFileData(latencies, favoriteElements));
    if (LOG.isTraceEnabled()) {
      String s0 = latencies[0].topSmallestLatenciesStat("ERROR");
      String s1 = latencies[1].topSmallestLatenciesStat("WARNING");
      String s2 = latencies[2].topSmallestLatenciesStat("INFO");
      LOG.trace(String.format("Inspections latencies stat: total tools: %4d; total highlighting time: %4dms; in %s:",
                              contexts.size(),
                              totalHighlightingNanos / 1_000_000, psiFile.getName()) +
                              StringUtil.notNullize(s0)+
                              StringUtil.notNullize(s1)+
                              StringUtil.notNullize(s2));
    }
  }

  /**
   * rearrange contexts in 'init' according to their inspection tools statistics gathered earlier:
   * - first, contexts with inspection tools which produced errors in previous run, ordered by latency to the 1st created error
   * - second, contexts with inspection tools which produced warnings in previous run, ordered by latency to the 1st created warning
   * - last, contexts with inspection tools which produced all other problems in previous run, ordered by latency to the 1st created problem
   * store the favorite element (i.e., the one with the lowest latency saved from the previous inspection run) to the {@link InspectionRunner.InspectionProblemHolder#myFavoriteElement}
   */
  void sortAndRetrieveFavoriteElement(@NotNull PsiFile psiFile, @NotNull List<InspectionRunner.InspectionContext> init) {
    InspectionFileData data = this.data.get(psiFile);
    if (data == null) {
      // no statistics => do nothing
      return;
    }
    init.sort((context1, context2) -> {
      String id1 = context1.tool().getID();
      String id2 = context2.tool().getID();
      for (int i = 0; i < data.latencies.length; i++) {
        Latencies l = data.latencies[i];
        int err = compareLatencies(id1, id2, l.idToLatency);
        if (err != 0) return err;
      }
      return 0;
    });

    for (InspectionRunner.InspectionContext context : init) {
      InspectionRunner.InspectionProblemHolder holder = context.holder();
      PsiElement element = data.favoriteElements().get(context.tool().getID());
      if (element != null && !element.isValid()) {
        element = null;
      }
      if (element != null) {
        holder.myFavoriteElement = element;
      }
    }
  }

  private static int compareLatencies(String id1, String id2, @NotNull Object2LongMap<String> latencies) {
    long latency1 = latencies.getOrDefault(id1, Long.MAX_VALUE);
    long latency2 = latencies.getOrDefault(id2, Long.MAX_VALUE);
    return Long.compare(latency1, latency2);
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
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

@Service(Service.Level.PROJECT)
// stores CPU profiler data for inspections run session
final class InspectionProfilerDataHolder {
  private static final Logger LOG = Logger.getInstance(InspectionProfilerDataHolder.class);

  private static class InspectionFileData {
    private final @NotNull Latencies @NotNull [/*3*/] latencies; // ERROR,WARNING,OTHER
    private final Map<String, PsiElement> favoriteElement; // tool id -> PsiElement which produced some diagnostics during last run

    private InspectionFileData(@NotNull Latencies @NotNull [] latencies, @NotNull Map<String, PsiElement> favoriteElement) {
      this.latencies = latencies;
      this.favoriteElement = favoriteElement;
    }
  }

  // store all data locally to be able to clear fast
  private final Map<PsiFile, InspectionFileData> data = Collections.synchronizedMap(new FixedHashMap<>(10));

  static InspectionProfilerDataHolder getInstance(@NotNull Project project) {
    return project.getService(InspectionProfilerDataHolder.class);
  }

  private InspectionProfilerDataHolder(Project project) {
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
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
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

  static class Latencies {
    final Object2LongMap<String> idToLatency = new Object2LongOpenHashMap<>();
    long minLatency = Long.MAX_VALUE;
    String minId;
    void save(@NotNull InspectionRunner.InspectionContext context, long stampOf1st) {
      long latency;
      if (stampOf1st != 0) {
        latency = stampOf1st - context.holder.initTimeStamp;
        idToLatency.put(context.tool.getID(), latency);
      }
      else {
        latency = Long.MAX_VALUE;
      }
      if (latency < minLatency) {
        minLatency = latency;
        minId = context.tool.getID();
      }
    }
    String topSmallestLatenciesStat(String mySeverity) {
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
   * after inspections completed, save their latencies (from corresponding {@link InspectionRunner.InspectionContext#holder}) to use later in {@link #sort(PsiFile, List)}
   */
  public void saveStats(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> contexts, long totalHighlightingNanos) {
    Latencies[] latencies = new Latencies[3]; // ERROR,WARNING,OTHER
    Map<String, PsiElement> favoriteElement = new HashMap<>();
    Arrays.setAll(latencies, __ -> new Latencies());
    for (InspectionRunner.InspectionContext context : contexts) {
      latencies[0].save(context, context.holder.errorStamp);
      latencies[1].save(context, context.holder.warningStamp);
      latencies[2].save(context, context.holder.otherStamp);
      if (context.myFavoriteElement != null) {
        favoriteElement.putIfAbsent(context.tool.getID(), context.myFavoriteElement);
      }
    }
    data.put(file, new InspectionFileData(latencies, favoriteElement));
    if (LOG.isTraceEnabled()) {
      String s0 = latencies[0].topSmallestLatenciesStat("ERROR");
      String s1 = latencies[1].topSmallestLatenciesStat("WARNING");
      String s2 = latencies[2].topSmallestLatenciesStat("INFO");
      LOG.trace(String.format("Inspections latencies stat: total tools: %4d; total highlighting time: %4dms; in %s:",
                              contexts.size(),
                              totalHighlightingNanos / 1_000_000, file.getName()) +
                              StringUtil.notNullize(s0)+
                              StringUtil.notNullize(s1)+
                              StringUtil.notNullize(s2));
    }
  }

  // rearrange contexts in 'init' according to their inspection tools statistics gathered earlier:
  // - first, contexts with inspection tools which produced errors in previous run, ordered by latency to the 1st created error
  // - second, contexts with inspection tools which produced warnings in previous run, ordered by latency to the 1st created warning
  // - last, contexts with inspection tools which produced all other problems in previous run, ordered by latency to the 1st created problem
  void sort(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> init) {
    InspectionFileData data = this.data.get(file);
    if (data == null) {
      // no statistics => do nothing
      return;
    }
    init.sort((context1, context2) -> {
      String id1 = context1.tool.getID();
      String id2 = context2.tool.getID();
      for (int i = 0; i < data.latencies.length; i++) {
        Latencies l = data.latencies[i];
        int err = compareLatencies(id1, id2, l.idToLatency);
        if (err != 0) return err;
      }
      return 0;
    });
  }

  private static int compareLatencies(String id1, String id2, @NotNull Object2LongMap<String> latencies) {
    long latency1 = latencies.getOrDefault(id1, Long.MAX_VALUE);
    long latency2 = latencies.getOrDefault(id2, Long.MAX_VALUE);
    return Long.compare(latency1, latency2);
  }

  void retrieveFavoriteElements(@NotNull PsiFile file, @NotNull List<? extends InspectionRunner.InspectionContext> init) {
    InspectionFileData data = this.data.get(file);
    if (data == null) {
      // no statistics => do nothing
      return;
    }
    Map<String, PsiElement> favoriteElement = data.favoriteElement;
    for (InspectionRunner.InspectionContext context : init) {
      PsiElement element = favoriteElement.get(context.tool.getID());
      if (element != null && !element.isValid()) {
        element = null;
      }
      if (element != null) {
        context.myFavoriteElement = element;
      }
    }
  }
}

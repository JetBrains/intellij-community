// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

public final class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  public static final ExtensionPointName<TextEditorHighlightingPassFactoryRegistrar> EP_NAME = new ExtensionPointName<>("com.intellij.highlightingPassFactory");

  private final Int2ObjectMap<PassConfig> myRegisteredPassFactories = new Int2ObjectOpenHashMap<>();
  private volatile PassConfig[] myFrozenPassConfigs; // passId -> PassConfig; contents is immutable, updated by COW
  private final List<DirtyScopeTrackingHighlightingPassFactory> myDirtyScopeTrackingFactories = ContainerUtil.createConcurrentList();
  private final AtomicInteger nextAvailableId = new AtomicInteger();
  private boolean checkedForCycles; // guarded by this
  private final Project myProject;
  private boolean serializeCodeInsightPasses;

  public TextEditorHighlightingPassRegistrarImpl(@NotNull Project project) {
    myProject = project;

    reRegisterFactories();

    EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull TextEditorHighlightingPassFactoryRegistrar factoryRegistrar,
                                 @NotNull PluginDescriptor pluginDescriptor) {
        synchronized (TextEditorHighlightingPassRegistrarImpl.this) {
          checkedForCycles = false;
        }
        factoryRegistrar.registerHighlightingPassFactory(TextEditorHighlightingPassRegistrarImpl.this, project);
      }

      @Override
      public void extensionRemoved(@NotNull TextEditorHighlightingPassFactoryRegistrar factoryRegistrar,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        reRegisterFactories();
      }
    }, project);
  }

  private void reRegisterFactories() {
    synchronized (this) {
      checkedForCycles = false;
      myRegisteredPassFactories.clear();
      myFrozenPassConfigs = null;
      nextAvailableId.set(Pass.LAST_PASS + 1);
      myDirtyScopeTrackingFactories.clear();
    }
    EP_NAME.forEachExtensionSafe(registrar -> registrar.registerHighlightingPassFactory(this, myProject));
  }

  private synchronized PassConfig @NotNull [] freezeRegisteredPassFactories() {
    PassConfig[] configs = myFrozenPassConfigs;
    if (configs == null) {
      int maxId = myRegisteredPassFactories.keySet().intStream().max().getAsInt();
      configs = new PassConfig[maxId + 1];
      for (Int2ObjectMap.Entry<PassConfig> entry : myRegisteredPassFactories.int2ObjectEntrySet()) {
        int id = entry.getIntKey();
        PassConfig config = entry.getValue();
        configs[id] = config;
      }
      myFrozenPassConfigs = configs;
    }
    return configs;
  }

  @ApiStatus.Internal
  void serializeCodeInsightPasses(boolean flag) {
    serializeCodeInsightPasses = flag;
    reRegisterFactories();
  }

  @ApiStatus.Internal
  public boolean isSerializeCodeInsightPasses() {
    return serializeCodeInsightPasses;
  }

  private static final class PassConfig {
    private final TextEditorHighlightingPassFactory passFactory;
    private final int[] startingPredecessorIds;
    private final int[] completionPredecessorIds;

    private PassConfig(@NotNull TextEditorHighlightingPassFactory passFactory, int @NotNull [] completionPredecessorIds, int @NotNull [] startingPredecessorIds) {
      this.completionPredecessorIds = completionPredecessorIds;
      this.startingPredecessorIds = startingPredecessorIds;
      this.passFactory = passFactory;
    }
  }

  @Override
  public synchronized int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory,
                                                             int @Nullable [] runAfterCompletionOf,
                                                             int @Nullable [] runAfterOfStartingOf,
                                                             boolean runIntentionsPassAfter,
                                                             int forcedPassId) {
    assert !checkedForCycles;
    PassConfig info = new PassConfig(factory,
             runAfterCompletionOf == null || runAfterCompletionOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY : runAfterCompletionOf,
             runAfterOfStartingOf == null || runAfterOfStartingOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY : runAfterOfStartingOf);
    int passId = forcedPassId == -1 ? nextAvailableId.incrementAndGet() : forcedPassId;
    PassConfig registered = myRegisteredPassFactories.get(passId);
    assert registered == null: "Pass id "+passId +" has already been registered in: "+ registered.passFactory;
    myRegisteredPassFactories.put(passId, info);
    myFrozenPassConfigs = null; // clear cache
    if (factory instanceof DirtyScopeTrackingHighlightingPassFactory) {
      myDirtyScopeTrackingFactories.add((DirtyScopeTrackingHighlightingPassFactory) factory);
    }
    return passId;
  }

  @NotNull
  AtomicInteger getNextAvailableId() {
    return nextAvailableId;
  }

  @Override
  public @NotNull List<@NotNull TextEditorHighlightingPass> instantiatePasses(@NotNull PsiFile psiFile,
                                                                              @NotNull Editor editor,
                                                                              int @NotNull [] passesToIgnore) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new IllegalStateException("Must not instantiate passes in EDT");
    }
    synchronized (this) {
      if (!checkedForCycles) {
        checkedForCycles = true;
        checkForCycles();
      }
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = editor.getDocument();
    PsiFile fileFromDoc = documentManager.getPsiFile(document);
    if (!(fileFromDoc instanceof PsiCompiledElement)) {
      assert fileFromDoc == psiFile : "Files are different: " + psiFile + ";" + fileFromDoc;
      Document documentFromFile = documentManager.getDocument(psiFile);
      assert documentFromFile == document : "Documents are different. Doc: " + document + "; Doc from file: " + documentFromFile +"; File: "+psiFile +"; Virtual file: "+
                                            PsiUtilCore.getVirtualFile(psiFile);
    }
    PassConfig[] frozenPassConfigs = freezeRegisteredPassFactories();
    List<TextEditorHighlightingPass> result = new ArrayList<>(frozenPassConfigs.length);
    IntList passesRefusedToCreate = new IntArrayList();
    boolean isDumb = DumbService.getInstance(myProject).isDumb();
    for (int passId = 0; passId < frozenPassConfigs.length; passId++) {
      PassConfig passConfig = frozenPassConfigs[passId];
      if (passConfig == null) continue;
      if (ArrayUtil.find(passesToIgnore, passId) != -1) {
        continue;
      }
      TextEditorHighlightingPassFactory factory = passConfig.passFactory;
      TextEditorHighlightingPass pass = isDumb && !DumbService.isDumbAware(factory) ? null : factory.createHighlightingPass(psiFile, editor);
      if (pass == null || isDumb && !DumbService.isDumbAware(pass)) {
        passesRefusedToCreate.add(passId);
      }
      else {
        // init with editor's colors scheme
        pass.setColorsScheme(editor.getColorsScheme());

        IntList ids = new IntArrayList(passConfig.completionPredecessorIds.length);
        for (int id : passConfig.completionPredecessorIds) {
          if (id < frozenPassConfigs.length && frozenPassConfigs[id] != null) {
            ids.add(id);
          }
        }
        pass.setCompletionPredecessorIds(ids.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : ids.toIntArray());
        ids = new IntArrayList(passConfig.startingPredecessorIds.length);
        for (int id : passConfig.startingPredecessorIds) {
          if (id < frozenPassConfigs.length && frozenPassConfigs[id] != null) {
            ids.add(id);
          }
        }
        pass.setStartingPredecessorIds(ids.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : ids.toIntArray());
        pass.setId(passId);
        result.add(pass);
      }
    }

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    FileStatusMap statusMap = daemonCodeAnalyzer.getFileStatusMap();
    for (IntListIterator iterator = passesRefusedToCreate.iterator(); iterator.hasNext(); ) {
      statusMap.markFileUpToDate(document, iterator.nextInt());
    }
    return result;
  }

  @Override
  public @NotNull List<@NotNull TextEditorHighlightingPass> instantiateMainPasses(@NotNull PsiFile psiFile,
                                                                                  @NotNull Document document,
                                                                                  @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    Set<TextEditorHighlightingPass> ids = new HashSet<>();
    PassConfig[] frozenPassConfigs = freezeRegisteredPassFactories();
    for (int passId = 0; passId < frozenPassConfigs.length; passId++) {
      PassConfig passConfig = frozenPassConfigs[passId];
      if (passConfig == null) continue;
      TextEditorHighlightingPassFactory factory = passConfig.passFactory;
      if (factory instanceof MainHighlightingPassFactory) {
        TextEditorHighlightingPass pass = ((MainHighlightingPassFactory)factory).createMainHighlightingPass(psiFile, document, highlightInfoProcessor);
        if (pass != null) {
          ids.add(pass);
          pass.setId(passId);
        }
      }
    }
    return new ArrayList<>(ids);
  }

  private void checkForCycles() {
    Int2ObjectMap<IntSet> transitivePredecessors = new Int2ObjectOpenHashMap<>();

    PassConfig[] frozenPassConfigs = freezeRegisteredPassFactories();
    for (int passId = 0; passId < frozenPassConfigs.length; passId++) {
      PassConfig passConfig = frozenPassConfigs[passId];
      if (passConfig == null) continue;

      IntSet allPredecessors = new IntOpenHashSet(passConfig.completionPredecessorIds);
      allPredecessors.addAll(IntArrayList.wrap(passConfig.startingPredecessorIds));
      transitivePredecessors.put(passId, allPredecessors);
      allPredecessors.forEach((IntConsumer)predecessorId -> {
        PassConfig predecessor = predecessorId < frozenPassConfigs.length ? frozenPassConfigs[predecessorId] : null;
        if (predecessor == null) {
          return;
        }

        IntSet transitives = transitivePredecessors.get(predecessorId);
        if (transitives == null) {
          transitives = new IntOpenHashSet();
          transitivePredecessors.put(predecessorId, transitives);
        }
        transitives.addAll(IntArrayList.wrap(predecessor.completionPredecessorIds));
        transitives.addAll(IntArrayList.wrap(predecessor.startingPredecessorIds));
      });
    }
    transitivePredecessors.keySet().forEach((IntConsumer)passId -> {
      if (transitivePredecessors.get(passId).contains(passId)) {
        throw new IllegalArgumentException("There is a cycle introduced involving pass " + passId +": "+frozenPassConfigs[passId].passFactory);
      }
    });
  }

  @NotNull
  @Override
  public Iterable<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories() {
    return myDirtyScopeTrackingFactories;
  }
}

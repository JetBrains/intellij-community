// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.ProblemHighlightFilter;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ClientEditorManager;
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
import java.util.stream.IntStream;

public final class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  public static final ExtensionPointName<TextEditorHighlightingPassFactoryRegistrar> EP_NAME = new ExtensionPointName<>("com.intellij.highlightingPassFactory");

  private final Int2ObjectMap<PassConfig> myRegisteredPassFactories = new Int2ObjectOpenHashMap<>(); // guarded by this
  private volatile PassConfig[] myFrozenPassConfigs; // passId -> PassConfig; contents is immutable, updated by COW
  private final List<DirtyScopeTrackingHighlightingPassFactory> myDirtyScopeTrackingFactories = ContainerUtil.createConcurrentList();
  private final AtomicInteger nextAvailableId = new AtomicInteger();
  private final Project myProject;
  private boolean serializeCodeInsightPasses;

  public TextEditorHighlightingPassRegistrarImpl(@NotNull Project project) {
    myProject = project;

    reRegisterFactories();

    EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull TextEditorHighlightingPassFactoryRegistrar factoryRegistrar,
                                 @NotNull PluginDescriptor pluginDescriptor) {
        factoryRegistrar.registerHighlightingPassFactory(TextEditorHighlightingPassRegistrarImpl.this, project);
      }

      @Override
      public void extensionRemoved(@NotNull TextEditorHighlightingPassFactoryRegistrar factoryRegistrar,
                                   @NotNull PluginDescriptor pluginDescriptor) {
        reRegisterFactories();
      }
    }, project);
  }

  void reRegisterFactories() {
    synchronized (this) {
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
      int maxId = myRegisteredPassFactories.keySet().intStream().max().orElse(0);
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

  private record PassConfig(@NotNull TextEditorHighlightingPassFactory passFactory,
                            int @NotNull [] completionPredecessorIds,
                            int @NotNull [] startingPredecessorIds) {
  }

  @Override
  public synchronized int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory,
                                                             int @Nullable [] runAfterCompletionOf,
                                                             int @Nullable [] runAfterOfStartingOf,
                                                             boolean runIntentionsPassAfter,
                                                             int forcedPassId) {
    int[] afterCompletionOf = runAfterCompletionOf == null || runAfterCompletionOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY : runAfterCompletionOf;
    int[] afterStartingOf = runAfterOfStartingOf == null || runAfterOfStartingOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY : runAfterOfStartingOf;
    if (IntStream.of(afterCompletionOf).anyMatch(id->ArrayUtil.indexOf(afterStartingOf, id) != -1)) {
      throw new IllegalArgumentException("Pass id must not be contained in both 'runAfterCompletionOf' and 'runAfterOfStartingOf' arguments but got " +
                                         Arrays.toString(afterCompletionOf) + " and " + Arrays.toString(afterStartingOf));
    }
    if (ArrayUtil.indexOf(afterCompletionOf, forcedPassId) != -1 || ArrayUtil.indexOf(afterStartingOf, forcedPassId) != -1) {
      throw new IllegalArgumentException("Neither 'runAfterCompletionOf' nor 'runAfterOfStartingOf' arguments must contain 'forcedPassId'=" + forcedPassId+ " but got " +
                                         Arrays.toString(afterCompletionOf) + " and " + Arrays.toString(afterStartingOf));
    }
    assertPassIdsAreNotCrazy(afterStartingOf, "afterStartingOf");
    assertPassIdsAreNotCrazy(afterCompletionOf, "afterCompletionOf");
    PassConfig info = new PassConfig(factory, afterCompletionOf, afterStartingOf);
    int passId = forcedPassId == -1 ? getNextAvailableId() : forcedPassId;
    PassConfig registered = myRegisteredPassFactories.get(passId);
    assert registered == null: "Pass id "+passId +" has already been registered in: "+ registered.passFactory;
    myRegisteredPassFactories.put(passId, info);
    myFrozenPassConfigs = null; // clear cache
    if (factory instanceof DirtyScopeTrackingHighlightingPassFactory) {
      myDirtyScopeTrackingFactories.add((DirtyScopeTrackingHighlightingPassFactory) factory);
    }
    return passId;
  }

  private void assertPassIdsAreNotCrazy(int @NotNull [] ids, @NotNull String name) {
    for (int id : ids) {
      if (id == Pass.UPDATE_FOLDING
          || id == Pass.POPUP_HINTS
          || id == Pass.UPDATE_ALL
          || id == Pass.LOCAL_INSPECTIONS
          || id == Pass.EXTERNAL_TOOLS
          || id == Pass.WOLF
          || id == Pass.LINE_MARKERS
          || id == Pass.SLOW_LINE_MARKERS
          ) {
        continue;
      }
      PassConfig config = myRegisteredPassFactories.get(id);
      if (config == null) {
        throw new IllegalArgumentException("Argument '"+name+"' must not contain 0 or -1 or other crazy/unknown pass ids, but got " + Arrays.toString(ids));
      }
    }
  }

  int getNextAvailableId() {
    return nextAvailableId.incrementAndGet();
  }

  @Override
  public @NotNull List<@NotNull TextEditorHighlightingPass> instantiatePasses(@NotNull PsiFile psiFile,
                                                                              @NotNull Editor editor,
                                                                              int @NotNull [] passesToIgnore) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    GlobalInspectionContextBase.assertUnderDaemonProgress();
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
    try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.Companion.getClientId(editor))) {
      for (int passId = 1; passId < frozenPassConfigs.length; passId++) {
        PassConfig passConfig = frozenPassConfigs[passId];
        if (passConfig == null) continue;
        if (ArrayUtil.find(passesToIgnore, passId) != -1) {
          continue;
        }
        TextEditorHighlightingPassFactory factory = passConfig.passFactory;
        TextEditorHighlightingPass pass = DumbService.getInstance(myProject).isUsableInCurrentContext(factory)
          && ProblemHighlightFilter.shouldHighlightFile(psiFile) ? factory.createHighlightingPass(psiFile, editor) : null;
        if (pass == null || !DumbService.getInstance(myProject).isUsableInCurrentContext(pass)) {
          passesRefusedToCreate.add(passId);
        }
        else {
          // init with editor's color scheme
          pass.setColorsScheme(editor.getColorsScheme());

          IntList ids = passConfig.completionPredecessorIds.length == 0 ? IntList.of() : new IntArrayList(passConfig.completionPredecessorIds.length);
          for (int id : passConfig.completionPredecessorIds) {
            if (id < frozenPassConfigs.length && frozenPassConfigs[id] != null) {
              ids.add(id);
            }
          }
          pass.setCompletionPredecessorIds(ids.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : ids.toIntArray());
          ids = passConfig.startingPredecessorIds.length == 0 ? IntList.of() : new IntArrayList(passConfig.startingPredecessorIds.length);
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
    ApplicationManager.getApplication().assertIsNonDispatchThread();
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

  @Override
  public @NotNull Iterable<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories() {
    return myDirtyScopeTrackingFactories;
  }
}

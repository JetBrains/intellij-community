// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
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
import gnu.trove.THashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  public static final ExtensionPointName<TextEditorHighlightingPassFactoryRegistrar> EP_NAME = new ExtensionPointName<>("com.intellij.highlightingPassFactory");

  private final TIntObjectHashMap<PassConfig> myRegisteredPassFactories = new TIntObjectHashMap<>();
  private final List<DirtyScopeTrackingHighlightingPassFactory> myDirtyScopeTrackingFactories = new ArrayList<>();
  private final AtomicInteger nextAvailableId = new AtomicInteger();
  private boolean checkedForCycles;
  private final Project myProject;
  private boolean runInspectionsAfterCompletionOfGeneralHighlightPass;

  public TextEditorHighlightingPassRegistrarImpl(@NotNull Project project) {
    myProject = project;

    reRegisterFactories();

    EP_NAME.addExtensionPointListener(new ExtensionPointListener<TextEditorHighlightingPassFactoryRegistrar>() {
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
      nextAvailableId.set(Pass.LAST_PASS + 1);
      myDirtyScopeTrackingFactories.clear();
    }
    EP_NAME.forEachExtensionSafe(registrar -> {
      registrar.registerHighlightingPassFactory(this, myProject);
    });
  }

  @ApiStatus.Internal
  void runInspectionsAfterCompletionOfGeneralHighlightPass(boolean flag) {
    runInspectionsAfterCompletionOfGeneralHighlightPass = flag;
    reRegisterFactories();
  }

  @ApiStatus.Internal
  boolean isRunInspectionsAfterCompletionOfGeneralHighlightPass() {
    return runInspectionsAfterCompletionOfGeneralHighlightPass;
  }

  private static class PassConfig {
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
                                     runAfterCompletionOf == null || runAfterCompletionOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY
                                                                                                      : runAfterCompletionOf,
                                     runAfterOfStartingOf == null || runAfterOfStartingOf.length == 0 ? ArrayUtilRt.EMPTY_INT_ARRAY
                                                                                                      : runAfterOfStartingOf);
    int passId = forcedPassId == -1 ? nextAvailableId.incrementAndGet() : forcedPassId;
    PassConfig registered = myRegisteredPassFactories.get(passId);
    assert registered == null: "Pass id "+passId +" has already been registered in: "+ registered.passFactory;
    myRegisteredPassFactories.put(passId, info);
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
  @NotNull
  public List<TextEditorHighlightingPass> instantiatePasses(@NotNull final PsiFile psiFile, @NotNull final Editor editor, final int @NotNull [] passesToIgnore) {
    synchronized (this) {
      if (!checkedForCycles) {
        checkedForCycles = true;
        checkForCycles();
      }
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    final Document document = editor.getDocument();
    PsiFile fileFromDoc = documentManager.getPsiFile(document);
    if (!(fileFromDoc instanceof PsiCompiledElement)) {
      assert fileFromDoc == psiFile : "Files are different: " + psiFile + ";" + fileFromDoc;
      Document documentFromFile = documentManager.getDocument(psiFile);
      assert documentFromFile == document : "Documents are different. Doc: " + document + "; Doc from file: " + documentFromFile +"; File: "+psiFile +"; Virtual file: "+
                                            PsiUtilCore.getVirtualFile(psiFile);
    }
    List<TextEditorHighlightingPass> result = new ArrayList<>(myRegisteredPassFactories.size());
    final TIntArrayList passesRefusedToCreate = new TIntArrayList();
    boolean isDumb = DumbService.getInstance(myProject).isDumb();
    myRegisteredPassFactories.forEachKey(passId -> {
      if (ArrayUtil.find(passesToIgnore, passId) != -1) {
        return true;
      }
      PassConfig passConfig = myRegisteredPassFactories.get(passId);
      TextEditorHighlightingPassFactory factory = passConfig.passFactory;
      TextEditorHighlightingPass pass = isDumb && !DumbService.isDumbAware(factory)
                                              ? null : factory.createHighlightingPass(psiFile, editor);
      if (pass == null || isDumb && !DumbService.isDumbAware(pass)) {
        passesRefusedToCreate.add(passId);
      }
      else {
        // init with editor's colors scheme
        pass.setColorsScheme(editor.getColorsScheme());

        TIntArrayList ids = new TIntArrayList(passConfig.completionPredecessorIds.length);
        for (int id : passConfig.completionPredecessorIds) {
          if (myRegisteredPassFactories.containsKey(id)) ids.add(id);
        }
        pass.setCompletionPredecessorIds(ids.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : ids.toNativeArray());
        ids = new TIntArrayList(passConfig.startingPredecessorIds.length);
        for (int id : passConfig.startingPredecessorIds) {
          if (myRegisteredPassFactories.containsKey(id)) ids.add(id);
        }
        pass.setStartingPredecessorIds(ids.isEmpty() ? ArrayUtilRt.EMPTY_INT_ARRAY : ids.toNativeArray());
        pass.setId(passId);
        result.add(pass);
      }
      return true;
    });

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    final FileStatusMap statusMap = daemonCodeAnalyzer.getFileStatusMap();
    passesRefusedToCreate.forEach(passId -> {
      statusMap.markFileUpToDate(document, passId);
      return true;
    });

    return result;
  }

  @NotNull
  @Override
  public List<TextEditorHighlightingPass> instantiateMainPasses(@NotNull final PsiFile psiFile,
                                                                @NotNull final Document document,
                                                                @NotNull final HighlightInfoProcessor highlightInfoProcessor) {
    final THashSet<TextEditorHighlightingPass> ids = new THashSet<>();
    myRegisteredPassFactories.forEachKey(passId -> {
      PassConfig passConfig = myRegisteredPassFactories.get(passId);
      TextEditorHighlightingPassFactory factory = passConfig.passFactory;
      if (factory instanceof MainHighlightingPassFactory) {
        final TextEditorHighlightingPass pass = ((MainHighlightingPassFactory)factory).createMainHighlightingPass(psiFile, document, highlightInfoProcessor);
        if (pass != null) {
          ids.add(pass);
          pass.setId(passId);
        }
      }
      return true;
    });
    return new ArrayList<>(ids);
  }

  private void checkForCycles() {
    final TIntObjectHashMap<TIntHashSet> transitivePredecessors = new TIntObjectHashMap<>();

    myRegisteredPassFactories.forEachEntry((passId, config) -> {
      TIntHashSet allPredecessors = new TIntHashSet(config.completionPredecessorIds);
      allPredecessors.addAll(config.startingPredecessorIds);
      transitivePredecessors.put(passId, allPredecessors);
      allPredecessors.forEach(predecessorId -> {
        PassConfig predecessor = myRegisteredPassFactories.get(predecessorId);
        if (predecessor == null) return  true;
        TIntHashSet transitives = transitivePredecessors.get(predecessorId);
        if (transitives == null) {
          transitives = new TIntHashSet();
          transitivePredecessors.put(predecessorId, transitives);
        }
        transitives.addAll(predecessor.completionPredecessorIds);
        transitives.addAll(predecessor.startingPredecessorIds);
        return true;
      });
      return true;
    });
    transitivePredecessors.forEachKey(passId -> {
      if (transitivePredecessors.get(passId).contains(passId)) {
        throw new IllegalArgumentException("There is a cycle introduced involving pass " + myRegisteredPassFactories.get(passId).passFactory);
      }
      return true;
    });
  }

  @NotNull
  @Override
  public List<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories() {
    return myDirtyScopeTrackingFactories;
  }
}

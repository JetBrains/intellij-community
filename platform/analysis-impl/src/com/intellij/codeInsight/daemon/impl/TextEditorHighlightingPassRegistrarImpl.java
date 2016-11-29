/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: anna
 * Date: 19-Apr-2006
 */
public class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  private final TIntObjectHashMap<PassConfig> myRegisteredPassFactories = new TIntObjectHashMap<>();
  private final List<DirtyScopeTrackingHighlightingPassFactory> myDirtyScopeTrackingFactories = new ArrayList<>();
  private int nextAvailableId = Pass.LAST_PASS + 1;
  private boolean checkedForCycles;
  private final Project myProject;

  public TextEditorHighlightingPassRegistrarImpl(Project project) {
    myProject = project;
  }

  private static class PassConfig {
    private final TextEditorHighlightingPassFactory passFactory;
    private final int[] startingPredecessorIds;
    private final int[] completionPredecessorIds;

    private PassConfig(@NotNull TextEditorHighlightingPassFactory passFactory, @NotNull int[] completionPredecessorIds, @NotNull int[] startingPredecessorIds) {
      this.completionPredecessorIds = completionPredecessorIds;
      this.startingPredecessorIds = startingPredecessorIds;
      this.passFactory = passFactory;
    }
  }

  @Override
  public void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, int anchor, int anchorPass) {
    Anchor anc = Anchor.FIRST;
    switch (anchor) {
      case FIRST : anc = Anchor.FIRST;
        break;
      case LAST : anc = Anchor.LAST;
        break;
      case BEFORE : anc = Anchor.BEFORE;
        break;
      case AFTER : anc = Anchor.AFTER;
        break;
    }
    registerTextEditorHighlightingPass(factory, anc, anchorPass, true, true);
  }

  @Override
  public synchronized int registerTextEditorHighlightingPass(@NotNull TextEditorHighlightingPassFactory factory,
                                                             @Nullable int[] runAfterCompletionOf,
                                                             @Nullable int[] runAfterOfStartingOf,
                                                             boolean runIntentionsPassAfter,
                                                             int forcedPassId) {
    assert !checkedForCycles;
    PassConfig info = new PassConfig(factory,
                                     runAfterCompletionOf == null || runAfterCompletionOf.length == 0 ? ArrayUtil.EMPTY_INT_ARRAY : runAfterCompletionOf,
                                     runAfterOfStartingOf == null || runAfterOfStartingOf.length == 0 ? ArrayUtil.EMPTY_INT_ARRAY : runAfterOfStartingOf);
    int passId = forcedPassId == -1 ? nextAvailableId++ : forcedPassId;
    PassConfig registered = myRegisteredPassFactories.get(passId);
    assert registered == null: "Pass id "+passId +" has already been registered in: "+ registered.passFactory;
    myRegisteredPassFactories.put(passId, info);
    if (factory instanceof DirtyScopeTrackingHighlightingPassFactory) {
      myDirtyScopeTrackingFactories.add((DirtyScopeTrackingHighlightingPassFactory) factory);
    }
    return passId;
  }

  @Override
  @NotNull
  public List<TextEditorHighlightingPass> instantiatePasses(@NotNull final PsiFile psiFile, @NotNull final Editor editor, @NotNull final int[] passesToIgnore) {
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
    final TIntObjectHashMap<TextEditorHighlightingPass> id2Pass = new TIntObjectHashMap<>();
    final TIntArrayList passesRefusedToCreate = new TIntArrayList();
    myRegisteredPassFactories.forEachKey(new TIntProcedure() {
      @Override
      public boolean execute(int passId) {
        if (ArrayUtil.find(passesToIgnore, passId) != -1) {
          return true;
        }
        PassConfig passConfig = myRegisteredPassFactories.get(passId);
        TextEditorHighlightingPassFactory factory = passConfig.passFactory;
        final TextEditorHighlightingPass pass = factory.createHighlightingPass(psiFile, editor);

        if (pass == null) {
          passesRefusedToCreate.add(passId);
        }
        else {
          // init with editor's colors scheme
          pass.setColorsScheme(editor.getColorsScheme());

          TIntArrayList ids = new TIntArrayList(passConfig.completionPredecessorIds.length);
          for (int id : passConfig.completionPredecessorIds) {
            if (myRegisteredPassFactories.containsKey(id)) ids.add(id);
          }
          pass.setCompletionPredecessorIds(ids.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : ids.toNativeArray());
          ids = new TIntArrayList(passConfig.startingPredecessorIds.length);
          for (int id : passConfig.startingPredecessorIds) {
            if (myRegisteredPassFactories.containsKey(id)) ids.add(id);
          }
          pass.setStartingPredecessorIds(ids.isEmpty() ? ArrayUtil.EMPTY_INT_ARRAY : ids.toNativeArray());
          pass.setId(passId);
          id2Pass.put(passId, pass);
        }
        return true;
      }
    });

    DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
    final FileStatusMap statusMap = daemonCodeAnalyzer.getFileStatusMap();
    passesRefusedToCreate.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int passId) {
        statusMap.markFileUpToDate(document, passId);
        return true;
      }
    });

    return (List)Arrays.asList(id2Pass.getValues());
  }

  @NotNull
  @Override
  public List<TextEditorHighlightingPass> instantiateMainPasses(@NotNull final PsiFile psiFile,
                                                                @NotNull final Document document,
                                                                @NotNull final HighlightInfoProcessor highlightInfoProcessor) {
    final THashSet<TextEditorHighlightingPass> ids = new THashSet<>();
    myRegisteredPassFactories.forEachKey(new TIntProcedure() {
      @Override
      public boolean execute(int passId) {
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
      }
    });
    return new ArrayList<>(ids);
  }

  private void checkForCycles() {
    final TIntObjectHashMap<TIntHashSet> transitivePredecessors = new TIntObjectHashMap<>();

    myRegisteredPassFactories.forEachEntry(new TIntObjectProcedure<PassConfig>() {
      @Override
      public boolean execute(int passId, PassConfig config) {
        TIntHashSet allPredecessors = new TIntHashSet(config.completionPredecessorIds);
        allPredecessors.addAll(config.startingPredecessorIds);
        transitivePredecessors.put(passId, allPredecessors);
        allPredecessors.forEach(new TIntProcedure() {
          @Override
          public boolean execute(int predecessorId) {
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
          }
        });
        return true;
      }
    });
    transitivePredecessors.forEachKey(new TIntProcedure() {
      @Override
      public boolean execute(int passId) {
        if (transitivePredecessors.get(passId).contains(passId)) {
          throw new IllegalArgumentException("There is a cycle introduced involving pass " + myRegisteredPassFactories.get(passId).passFactory);
        }
        return true;
      }
    });
  }

  @NotNull
  @Override
  public List<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories() {
    return myDirtyScopeTrackingFactories;
  }
}

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

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 19-Apr-2006
 */
public class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  private final TIntObjectHashMap<PassConfig> myRegisteredPassFactories = new TIntObjectHashMap<PassConfig>();
  private final List<DirtyScopeTrackingHighlightingPassFactory> myDirtyScopeTrackingFactories = new ArrayList<DirtyScopeTrackingHighlightingPassFactory>();
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
      assert documentFromFile == document : "Documents are different: " + document + ";" + documentFromFile;
    }
    final TIntObjectHashMap<TextEditorHighlightingPass> id2Pass = new TIntObjectHashMap<TextEditorHighlightingPass>();
    final TIntArrayList passesRefusedToCreate = new TIntArrayList();
    myRegisteredPassFactories.forEachKey(new TIntProcedure() {
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

    final FileStatusMap statusMap = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap();
    passesRefusedToCreate.forEach(new TIntProcedure() {
      public boolean execute(int passId) {
        statusMap.markFileUpToDate(document, psiFile, passId);
        return true;
      }
    });

    //sort is mainly for tests which expect passes to be run sequentially and produce correct results
    return topoSort(id2Pass);
  }

  private static List<TextEditorHighlightingPass> topoSort(final TIntObjectHashMap<TextEditorHighlightingPass> id2Pass) {
    final Set<TextEditorHighlightingPass> topPasses = new THashSet<TextEditorHighlightingPass>(id2Pass.size());
    id2Pass.forEachValue(new TObjectProcedure<TextEditorHighlightingPass>() {
      public boolean execute(TextEditorHighlightingPass object) {
        topPasses.add(object);
        return true;
      }
    });
    id2Pass.forEachValue(new TObjectProcedure<TextEditorHighlightingPass>() {
      public boolean execute(TextEditorHighlightingPass pass) {
        for (int id : pass.getCompletionPredecessorIds()) {
          TextEditorHighlightingPass pred = id2Pass.get(id);
          if (pred != null) {  //can be null if filtered out by passesToIgnore
            topPasses.remove(pred);
          }
        }
        for (int id : pass.getStartingPredecessorIds()) {
          TextEditorHighlightingPass pred = id2Pass.get(id);
          if (pred != null) {  //can be null if filtered out by passesToIgnore
            topPasses.remove(pred);
          }
        }
        return true;
      }
    });
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>();
    for (TextEditorHighlightingPass topPass : topPasses) {
      layout(topPass, result, id2Pass);
    }
    return result;
  }

  private static void layout(@NotNull final TextEditorHighlightingPass pass,
                             @NotNull final List<TextEditorHighlightingPass> result,
                             @NotNull final TIntObjectHashMap<TextEditorHighlightingPass> id2Pass) {
    if (result.contains(pass)) return;
    for (int id : pass.getCompletionPredecessorIds()) {
      TextEditorHighlightingPass pred = id2Pass.get(id);
      if (pred != null) {
        layout(pred, result, id2Pass);
      }
    }
    for (int id : pass.getStartingPredecessorIds()) {
      TextEditorHighlightingPass pred = id2Pass.get(id);
      if (pred != null) {
        layout(pred, result, id2Pass);
      }
    }
    result.add(pass);
  }

  private void checkForCycles() {
    final TIntObjectHashMap<TIntHashSet> transitivePredecessors = new TIntObjectHashMap<TIntHashSet>();

    myRegisteredPassFactories.forEachEntry(new TIntObjectProcedure<PassConfig>() {
      public boolean execute(int passId, PassConfig config) {
        TIntHashSet allPredecessors = new TIntHashSet(config.completionPredecessorIds);
        allPredecessors.addAll(config.startingPredecessorIds);
        transitivePredecessors.put(passId, allPredecessors);
        allPredecessors.forEach(new TIntProcedure() {
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
      public boolean execute(int passId) {
        if (transitivePredecessors.get(passId).contains(passId)) {
          throw new IllegalArgumentException("There is a cycle introduced involving pass " + myRegisteredPassFactories.get(passId).passFactory);
        }
        return true;
      }
    });
  }

  public List<DirtyScopeTrackingHighlightingPassFactory> getDirtyScopeTrackingFactories() {
    return myDirtyScopeTrackingFactories;
  }
}

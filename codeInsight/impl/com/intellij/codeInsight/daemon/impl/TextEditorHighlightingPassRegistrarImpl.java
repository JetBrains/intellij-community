/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
  private int nextAvailableId = Pass.EXTERNAL_TOOLS+1;
  private boolean checkedForCycles;
  private Project myProject;

  public TextEditorHighlightingPassRegistrarImpl(Project project) {
    myProject = project;
  }

  private static class PassConfig {
    private final TextEditorHighlightingPassFactory passFactory;
    private final int[] startingPredecessorIds;
    private final int[] completionPredecessorIds;
    private final boolean runIntentionsPassAfter;

    public PassConfig(@NotNull TextEditorHighlightingPassFactory passFactory,
                      boolean runIntentionsPassAfter,
                      @NotNull int[] completionPredecessorIds,
                      @NotNull int[] startingPredecessorIds) {
      this.runIntentionsPassAfter = runIntentionsPassAfter;
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
    PassConfig info = new PassConfig(factory, runIntentionsPassAfter,
                                     runAfterCompletionOf == null ? ArrayUtil.EMPTY_INT_ARRAY : runAfterCompletionOf,
                                     runAfterOfStartingOf == null ? ArrayUtil.EMPTY_INT_ARRAY : runAfterOfStartingOf);
    int passId = forcedPassId == -1 ? nextAvailableId++ : forcedPassId;
    myRegisteredPassFactories.put(passId, info);
    return passId;
  }

  @NotNull
  public List<TextEditorHighlightingPass> instantiatePasses(@NotNull final PsiFile psiFile, @NotNull final Editor editor, @NotNull final int[] passesToIgnore) {
    if (!checkedForCycles) {
      checkedForCycles = true;
      checkForCycles();
    }
    final TIntObjectHashMap<TextEditorHighlightingPass> id2Pass = new TIntObjectHashMap<TextEditorHighlightingPass>();
    myRegisteredPassFactories.forEachKey(new TIntProcedure() {
      public boolean execute(int passId) {
        if (ArrayUtil.find(passesToIgnore, passId) != -1) return true;
        PassConfig passConfig = myRegisteredPassFactories.get(passId);
        TextEditorHighlightingPassFactory factory = passConfig.passFactory;
        final TextEditorHighlightingPass pass = factory.createHighlightingPass(psiFile, editor);

        if (pass == null) {
          ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).getFileStatusMap().markFileUpToDate(editor.getDocument(), passId);
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
          if (passConfig.runIntentionsPassAfter && !(pass instanceof ProgressableTextEditorHighlightingPass.EmptyPass)) {
            Project project = psiFile.getProject();
            ShowIntentionsPass intentionsPass = new ShowIntentionsPass(project, editor, passId, new IntentionAction[] {new QuickFixAction()});
            intentionsPass.setCompletionPredecessorIds(new int[]{passId});
            int id = nextAvailableId++;
            intentionsPass.setId(id);
            id2Pass.put(id, intentionsPass);
          }
        }
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

}

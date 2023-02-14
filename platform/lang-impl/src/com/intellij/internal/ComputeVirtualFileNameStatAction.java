// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("SSBasedInspection")
final class ComputeVirtualFileNameStatAction extends AnAction implements DumbAware {

  ComputeVirtualFileNameStatAction() {
    super(ActionsBundle.messagePointer("action.ComputeVirtualFileNameStatAction.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    long start = System.currentTimeMillis();

    suffixes.clear();
    nameCount.clear();
    VirtualFile[] roots = ManagingFS.getInstance().getRoots(LocalFileSystem.getInstance());
    for (VirtualFile root : roots) {
      compute(root);
    }

    final List<Pair<String,Integer>> names = new ArrayList<>(nameCount.size());
    for (Object2IntMap.Entry<String> entry : nameCount.object2IntEntrySet()) {
      names.add(new Pair<>(entry.getKey(), entry.getIntValue()));
    }
    names.sort((o1, o2) -> o2.second - o1.second);

    System.out.println("Most frequent names ("+names.size()+" total):");
    int saveByIntern = 0;
    for (Pair<String, Integer> pair : names) {
      int count = pair.second;
      String name = pair.first;
      System.out.println(name + " -> " + count);
      saveByIntern += count * name.length();
      if (count == 1) break;
    }
    System.out.println("Total save if names were interned: "+saveByIntern+"; ------------");

    //System.out.println("Prefixes: ("+prefixes.size()+" total)");
    //show(prefixes);
    System.out.println("Suffix counts:("+suffixes.size()+" total)");
    show(suffixes);


    final Object2IntMap<String> save = new Object2IntOpenHashMap<>();
    // compute economy
    for (Object2IntMap.Entry<String> entry : suffixes.object2IntEntrySet()) {
      save.put(entry.getKey(), entry.getIntValue() * entry.getKey().length());
    }

    System.out.println("Supposed save by stripping suffixes: ("+save.size()+" total)");
    final List<Pair<String, Integer>> saveSorted = show(save);


    final List<String> picked = new ArrayList<>();
    //List<String> candidates = new ArrayList<String>();
    //int i =0;
    //for (Pair<String, Integer> pair : sorted) {
    //  if (i++>1000) break;
    //  candidates.add(pair.first);
    //}

    //final TObjectIntHashMap<String> counts = new TObjectIntHashMap<String>();
    //suffixes.forEachEntry(new TObjectIntProcedure<String>() {
    //  @Override
    //  public boolean execute(String a, int b) {
    //    counts.put(a, b);
    //    return true;
    //  }
    //});

    while (picked.size() != 15) {
      Pair<String, Integer> cp = saveSorted.get(0);
      final String candidate = cp.first;
      picked.add(candidate);
      System.out.println("Candidate: '"+candidate+"', save = "+cp.second);
      picked.sort((o1, o2) -> {
        return o2.length() - o1.length(); // longer first
      });
      saveSorted.clear();

      // adjust
      for (Object2IntMap.Entry<String> entry : suffixes.object2IntEntrySet()) {
        String s = entry.getKey();
        int count = entry.getIntValue();
        for (int i = picked.size() - 1; i >= 0; i--) {
          String pick = picked.get(i);
          if (pick.endsWith(s)) {
            count -= suffixes.getInt(pick);
            break;
          }
        }
        saveSorted.add(Pair.create(s, s.length() * count));
      }
      saveSorted.sort((o1, o2) -> o2.second.compareTo(o1.second));
    }

    System.out.println("Picked: "+ StringUtil.join(picked, s -> "\"" + s + "\"", ","));
    picked.sort((o1, o2) -> {
      return o2.length() - o1.length(); // longer first
    });

    int saved = 0;
    for (int i = 0; i < picked.size(); i++) {
      String s = picked.get(i);
      int count = suffixes.getInt(s);
      for (int k=0; k<i;k++) {
        String prev = picked.get(k);
        if (prev.endsWith(s)) {
          count -= suffixes.getInt(prev);
          break;
        }
      }
      saved += count * s.length();
    }
    System.out.println("total saved = " + saved);
    System.out.println("Time spent: " + (System.currentTimeMillis() - start));
  }

  private static List<Pair<String,Integer>> show(final Object2IntMap<String> prefixes) {
    final List<Pair<String,Integer>> prefs = new ArrayList<>(prefixes.size());
    for (Object2IntMap.Entry<String> entry : prefixes.object2IntEntrySet()) {
      prefs.add(new Pair<>(entry.getKey(), entry.getIntValue()));
    }
    prefs.sort((o1, o2) -> o2.second.compareTo(o1.second));
    int i =0;
    for (Pair<String, Integer> pref : prefs) {
      Integer count = pref.second;
      System.out.printf("%60.60s : %d\n", pref.first, count);
      if (/*count<500 || */i++ > 100) {
        System.out.println("\n.......<" + count + "...\n");
        break;
      }
    }
    return prefs;
  }

  //TObjectIntHashMap<String> prefixes = new TObjectIntHashMap<String>();
  Object2IntOpenHashMap<String> suffixes = new Object2IntOpenHashMap<>();
  Object2IntOpenHashMap<String> nameCount = new Object2IntOpenHashMap<>();
  private void compute(VirtualFile root) {
    String name = root.getName();
    nameCount.addTo(name, 1);
    for (int i=1; i<=name.length(); i++) {
      //String prefix = name.substring(0, i);
      //if (!prefixes.increment(prefix)) prefixes.put(prefix, 1);

      String suffix = name.substring(name.length()-i);
      suffixes.addTo(suffix, 1);
    }
    Collection<VirtualFile> cachedChildren = ((VirtualFileSystemEntry)root).getCachedChildren();
    //VirtualFile[] cachedChildren = ((VirtualFileSystemEntry)root).getChildren();
    for (VirtualFile cachedChild : cachedChildren) {
      compute(cachedChild);
    }
  }
}

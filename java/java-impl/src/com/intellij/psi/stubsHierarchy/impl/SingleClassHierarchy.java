/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntStack;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Compact representation of total hierarchy of JVM classes.
 */
public class SingleClassHierarchy {
  public final SmartClassAnchor[] myClassAnchors;
  private final SmartClassAnchor[] myClassAnchorsByFileIds;
  private int[] mySubtypes;
  private int[] mySubtypeStarts;

  public SingleClassHierarchy(ClassSymbol[] classSymbols, int symbolCount) {
    this.myClassAnchors = mkAnchors(classSymbols, symbolCount);
    this.myClassAnchorsByFileIds = mkByFileId(this.myClassAnchors);
  }

  // under read actions
  public SmartClassAnchor[] getSubtypes(PsiClass psiClass) {
    int fileId = Math.abs(FileBasedIndex.getFileId(psiClass.getContainingFile().getVirtualFile()));
    SmartClassAnchor anchor = forPsiClass(fileId, psiClass);
    if (anchor == null) {
      return SmartClassAnchor.EMPTY_ARRAY;
    }
    int symbolId = anchor.myId;
    int start = subtypeStart(symbolId);
    int end = subtypeEnd(symbolId);
    int length = end - start;
    if (length == 0) {
      return SmartClassAnchor.EMPTY_ARRAY;
    }
    SmartClassAnchor[] result = new SmartClassAnchor[length];
    for (int i = 0; i < length; i++) {
      result[i] = myClassAnchors[mySubtypes[start + i]];
    }
    return result;
  }

  public SmartClassAnchor[] getAllSubtypes(PsiClass base) {
    int fileId = Math.abs(FileBasedIndex.getFileId(base.getContainingFile().getVirtualFile()));

    TIntHashSet resultIds = new TIntHashSet();
    TIntHashSet processed = new TIntHashSet();
    TIntStack queue = new TIntStack();

    SmartClassAnchor baseAnchor = forPsiClass(fileId, base);
    if (baseAnchor == null) {
      return SmartClassAnchor.EMPTY_ARRAY;
    }

    queue.push(baseAnchor.myId);
    while (queue.size() > 0) {
      int id = queue.pop();
      if (processed.add(id)) {
        int start = subtypeStart(id);
        int end = subtypeEnd(id);
        for (int i = start; i < end; i++) {
          int subtypeId = mySubtypes[i];
          resultIds.add(subtypeId);
          if (!processed.contains(subtypeId)) {
            queue.push(subtypeId);
          }
        }
      }
    }
    int[] allIds = resultIds.toArray();
    SmartClassAnchor[] result = new SmartClassAnchor[allIds.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = myClassAnchors[allIds[i]];
    }
    return result;
  }

  private static SmartClassAnchor[] mkAnchors(ClassSymbol[] classSymbols, int symbolCount) {
    SmartClassAnchor[] anchors = new SmartClassAnchor[symbolCount];
    for (int i = 0; i < symbolCount; i++) {
      anchors[i] = classSymbols[i].myClassAnchor;
    }
    return anchors;
  }

  private static SmartClassAnchor[] mkByFileId(final SmartClassAnchor[] classAnchors) {
    SmartClassAnchor[] result = new SmartClassAnchor[classAnchors.length];
    SmartClassAnchor lastProcessedAnchor = null;
    int i = 0;
    for (SmartClassAnchor classAnchor : classAnchors) {
      if (lastProcessedAnchor == null || lastProcessedAnchor.myFileId != classAnchor.myFileId) {
        result[i++] = classAnchor;
      }
      lastProcessedAnchor = classAnchor;
    }
    // compacting
    result = Arrays.copyOf(result, i);

    Arrays.sort(result, new Comparator<SmartClassAnchor>() {
      @Override
      public int compare(SmartClassAnchor o1, SmartClassAnchor o2) {
        int i1 = o1.myFileId;
        int i2 = o2.myFileId;
        if (i1 < i2) {
          return -1;
        } else if (i1 > i2) {
          return +1;
        } else {
          return 0;
        }
      }
    });
    return result;
  }

  void connectSubTypes(ClassSymbol[] classSymbols, int n) {
    int[] sizes = calculateSizes(classSymbols, n);

    int[] starts = new int[n];
    int count = 0;

    for (int i = 0; i < sizes.length; i++) {
      starts[i] = count;
      count += sizes[i];
    }
    int[] subtypes = new int[count];
    int[] filled = new int[sizes.length];

    for (int subTypeId = 0; subTypeId < n; subTypeId++) {
      ClassSymbol subType = classSymbols[subTypeId];
      for (ClassSymbol superType : subType.getSuperClasses()) {
        int superTypeId = superType.myClassAnchor.myId;
        subtypes[starts[superTypeId] + filled[superTypeId]] = subTypeId;
        filled[superTypeId] += 1;
      }
    }

    this.mySubtypes = subtypes;
    this.mySubtypeStarts = starts;
  }

  private static int[] calculateSizes(ClassSymbol[] classSymbols, int n) {
    int[] sizes = new int[n];
    for (int i = 1; i < n; i++) {
      ClassSymbol subType = classSymbols[i];
      for (ClassSymbol superType : subType.getSuperClasses()) {
        int superTypeId = superType.myClassAnchor.myId;
        sizes[superTypeId] += 1;
      }
    }
    return sizes;
  }

  private int subtypeStart(int nameId) {
    return mySubtypeStarts[nameId];
  }

  private int subtypeEnd(int nameId) {
    return (nameId + 1 >= mySubtypeStarts.length) ? mySubtypes.length : mySubtypeStarts[nameId + 1];
  }

  private SmartClassAnchor forPsiClass(int fileId, PsiClass psiClass) {
    SmartClassAnchor anchor = getFirst(fileId, myClassAnchorsByFileIds);
    if (anchor == null) {
      return null;
    }
    int id = anchor.myId;
    while (id < myClassAnchors.length) {
      SmartClassAnchor candidate = myClassAnchors[id];
      if (candidate.myFileId != fileId)
        return null;
      if (psiClass.isEquivalentTo(ClassAnchorUtil.retrieveInReadAction(psiClass.getProject(), candidate)))
        return candidate;
      id++;
    }
    return null;
  }

  private static SmartClassAnchor getFirst(int fileId, SmartClassAnchor[] byFileIds) {
    int lo = 0;
    int hi = byFileIds.length - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      if      (fileId < byFileIds[mid].myFileId) hi = mid - 1;
      else if (fileId > byFileIds[mid].myFileId) lo = mid + 1;
      else return byFileIds[mid];
    }
    return null;
  }
}

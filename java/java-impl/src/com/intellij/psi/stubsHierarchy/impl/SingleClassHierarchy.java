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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubsHierarchy.ClassHierarchy;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Compact representation of total hierarchy of JVM classes.
 */
public class SingleClassHierarchy extends ClassHierarchy {
  private final BitSet myCoveredFiles;
  private final BitSet myAmbiguousSupers;
  private final BitSet myAnonymous;
  private final AnchorRepository myClassAnchors;
  private final int[] myClassAnchorsByFileIds;
  private int[] mySubtypes;
  private int[] mySubtypeStarts;

  public SingleClassHierarchy(ClassSymbol[] classSymbols, AnchorRepository classAnchors) {
    classAnchors.trimToSize();
    myClassAnchors = classAnchors;
    myCoveredFiles = calcCoveredFiles(classSymbols);
    myClassAnchorsByFileIds = mkByFileId();
    excludeUncoveredFiles(classSymbols);
    connectSubTypes(classSymbols);
    myAmbiguousSupers = calcAmbiguousSupers(classSymbols);
    myAnonymous = calcAnonymous(classSymbols);
  }

  @NotNull
  private static BitSet calcAmbiguousSupers(ClassSymbol[] classSymbols) {
    BitSet ambiguousSupers = new BitSet();
    for (ClassSymbol symbol : classSymbols) {
      if (!symbol.isHierarchyIncomplete() && symbol.hasAmbiguousSupers()) {
        ambiguousSupers.set(symbol.myAnchorId);
      }
    }
    return ambiguousSupers;
  }

  @NotNull
  private static BitSet calcAnonymous(ClassSymbol[] classSymbols) {
    BitSet answer = new BitSet();
    for (ClassSymbol symbol : classSymbols) {
      if (!symbol.isHierarchyIncomplete() && symbol.myShortName == NameEnvironment.NO_NAME) {
        answer.set(symbol.myAnchorId);
      }
    }
    return answer;
  }

  @NotNull
  private BitSet calcCoveredFiles(ClassSymbol[] classSymbols) {
    BitSet problematicFiles = new BitSet();
    BitSet coveredFiles = new BitSet();
    for (ClassSymbol symbol : classSymbols) {
      int fileId = myClassAnchors.getFileId(symbol.myAnchorId);
      coveredFiles.set(fileId);
      if (symbol.isHierarchyIncomplete()) {
        problematicFiles.set(fileId);
      }
    }
    coveredFiles.andNot(problematicFiles);
    return coveredFiles;
  }

  private boolean isCovered(@NotNull StubClassAnchor anchor) {
    return myCoveredFiles.get(anchor.myFileId);
  }

  @Override
  @NotNull
  public List<StubClassAnchor> getCoveredClasses() {
    return ContainerUtil.filter(getAllClasses(), this::isCovered);
  }

  @Override
  @NotNull
  public List<StubClassAnchor> getAllClasses() {
    return IntStream.range(0, myClassAnchors.size()).boxed().map(myClassAnchors::getAnchor).collect(Collectors.toList());
  }

  @NotNull
  @Override
  public SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull PsiClass psiClass) {
    SmartClassAnchor anchor = findAnchor(psiClass);
    return anchor == null ? StubClassAnchor.EMPTY_ARRAY : getDirectSubtypeCandidates(anchor);
  }

  @Override
  @Nullable
  public SmartClassAnchor findAnchor(@NotNull PsiClass psiClass) {
    VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
    return vFile instanceof VirtualFileWithId ? forPsiClass(((VirtualFileWithId)vFile).getId(), psiClass) : null;
  }

  @NotNull
  @Override
  public SmartClassAnchor[] getDirectSubtypeCandidates(@NotNull SmartClassAnchor anchor) {
    int symbolId = ((StubClassAnchor)anchor).myId;
    int start = subtypeStart(symbolId);
    int end = subtypeEnd(symbolId);
    int length = end - start;
    if (length == 0) {
      return StubClassAnchor.EMPTY_ARRAY;
    }
    StubClassAnchor[] result = new StubClassAnchor[length];
    for (int i = 0; i < length; i++) {
      result[i] = myClassAnchors.getAnchor(mySubtypes[start + i]);
    }
    return result;
  }

  @Override
  public boolean hasAmbiguousSupers(@NotNull SmartClassAnchor anchor) {
    return myAmbiguousSupers.get(((StubClassAnchor)anchor).myId);
  }

  @Override
  public boolean isAnonymous(@NotNull SmartClassAnchor anchor) {
    return myAnonymous.get(((StubClassAnchor)anchor).myId);
  }

  @NotNull
  @Override
  public GlobalSearchScope restrictToUncovered(@NotNull GlobalSearchScope scope) {
    if (myCoveredFiles.isEmpty()) {
      return scope;
    }

    return new DelegatingGlobalSearchScope(scope, this) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof VirtualFileWithId && myCoveredFiles.get(((VirtualFileWithId)file).getId())) {
          return false;
        }

        return super.contains(file);
      }
    };
  }

  @NotNull
  private Integer[] getAnchorsFromDistinctFiles() {
    if (myClassAnchors.size() == 0) return new Integer[0];

    Integer[] result = new Integer[myClassAnchors.size()];
    result[0] = 0;
    int i = 1;
    for (int classAnchor = 1; classAnchor < result.length; classAnchor++) {
      if (myClassAnchors.getFileId(classAnchor - 1) != myClassAnchors.getFileId(classAnchor)) {
        result[i++] = classAnchor;
      }
    }
    return Arrays.copyOf(result, i);
  }

  private int[] mkByFileId() {
    // using a boxed array since there seems to be no easy way to sort int[] with custom comparator
    Integer[] ids = getAnchorsFromDistinctFiles();
    Arrays.sort(ids, (a1, a2) -> Integer.compare(myClassAnchors.getFileId(a1), myClassAnchors.getFileId(a2)));
    return ArrayUtils.toPrimitive(ids);
  }

  private void connectSubTypes(ClassSymbol[] classSymbols) {
    int[] sizes = calculateSizes(classSymbols);

    int[] starts = new int[classSymbols.length];
    int count = 0;

    for (int i = 0; i < sizes.length; i++) {
      starts[i] = count;
      count += sizes[i];
    }
    int[] subtypes = new int[count];
    int[] filled = new int[sizes.length];

    for (int subTypeId = 0; subTypeId < classSymbols.length; subTypeId++) {
      ClassSymbol subType = classSymbols[subTypeId];
      for (ClassSymbol superType : subType.rawSuperClasses()) {
        int superTypeId = superType.myAnchorId;
        subtypes[starts[superTypeId] + filled[superTypeId]] = subTypeId;
        filled[superTypeId] += 1;
      }
    }

    this.mySubtypes = subtypes;
    this.mySubtypeStarts = starts;
  }

  private void excludeUncoveredFiles(ClassSymbol[] classSymbols) {
    for (ClassSymbol symbol : classSymbols) {
      if (!myCoveredFiles.get(myClassAnchors.getFileId(symbol.myAnchorId))) {
        symbol.markHierarchyIncomplete();
      }
    }
  }

  private static int[] calculateSizes(ClassSymbol[] classSymbols) {
    int[] sizes = new int[classSymbols.length];
    for (ClassSymbol subType : classSymbols) {
      for (ClassSymbol superType : subType.rawSuperClasses()) {
        sizes[superType.myAnchorId] += 1;
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

  private StubClassAnchor forPsiClass(int fileId, PsiClass psiClass) {
    int id = getFirst(fileId);
    if (id == -1) {
      return null;
    }
    while (id < myClassAnchors.size() && myClassAnchors.getFileId(id) == fileId) {
      if (psiClass.isEquivalentTo(myClassAnchors.retrieveClass(psiClass.getProject(), id))) {
        return myClassAnchors.getAnchor(id);
      }
      id++;
    }
    return null;
  }

  private int getFirst(int fileId) {
    int lo = 0;
    int hi = myClassAnchorsByFileIds.length - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      int midFileId = myClassAnchors.getFileId(myClassAnchorsByFileIds[mid]);
      if      (fileId < midFileId) hi = mid - 1;
      else if (fileId > midFileId) lo = mid + 1;
      else return myClassAnchorsByFileIds[mid];
    }
    return -1;
  }
}

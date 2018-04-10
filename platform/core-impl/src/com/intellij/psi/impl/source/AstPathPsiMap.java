/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.AstPath;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * A weak cache for all instantiated stub-based PSI to allow {@link CompositeElement#getPsi()} return it when AST is reloaded.<p/>
 * 
 * All methods should be called under an external lock (view provider's PsiLock), except for 
 * ({@link #getCachedPsi(AstPath)} which can be called without lock.
 * 
 * @author peter
 */
class AstPathPsiMap {
  /**
   * Not using ConcurrentWeakValueMap because we need to clean each of them up separately, when ASTs in thousands of files are created and gc-ed.
   * So we have a per-project single shared reference queue {@link #myQueue} for that.
   * Otherwise the files end up retaining lots of maps with all-gc-ed stuff inside, but the maps are still very large.
   */
  private final ConcurrentMap<AstPath, MyReference> myMap = ContainerUtil.newConcurrentMap();
  private volatile Boolean myHasUnbindableCachedPsi = null;

  private static final Key<MyReferenceQueue> STUB_PSI_REFS = Key.create("STUB_PSI_REFS");
  private final MyReferenceQueue myQueue;

  AstPathPsiMap(@NotNull Project project) {
    MyReferenceQueue queue = project.getUserData(STUB_PSI_REFS);
    myQueue = queue != null ? queue : ((UserDataHolderEx)project).putUserDataIfAbsent(STUB_PSI_REFS, new MyReferenceQueue(project));
  }

  void invalidatePsi() {
    myQueue.cleanupStaleReferences();
    getAllCachedPsi().forEach(psi -> {
      DebugUtil.onInvalidated(psi);
      psi.setSubstrateRef(SubstrateRef.createInvalidRef(psi));
    });
    myMap.clear();
    myHasUnbindableCachedPsi = false;
  }

  void switchToStrongRefs() {
    myQueue.cleanupStaleReferences();
    getAllCachedPsi().forEach(psi -> {
      CompositeElement node = (CompositeElement)psi.getNode();
      node.setPsi(psi);
      psi.setSubstrateRef(SubstrateRef.createAstStrongRef(node));
    });
    myMap.clear();
    myHasUnbindableCachedPsi = false;
  }

  @Nullable
  StubBasedPsiElementBase<?> getCachedPsi(@NotNull AstPath ref) {
    return SoftReference.dereference(myMap.get(ref));
  }

  @NotNull
  StubBasedPsiElementBase<?> cachePsi(@NotNull AstPath key, @NotNull StubBasedPsiElementBase psi) {
    myQueue.cleanupStaleReferences();
    // ensure PSI will use AST path before making it available to other threads
    // otherwise another thread could invoke StubRef.getNode and fail since file's AST isn't set yet
    psi.setSubstrateRef(key);
    myMap.put(key, new MyReference(psi, key, myQueue));
    clearStubIndexCache();
    return psi;
  }

  Stream<? extends StubBasedPsiElementBase<?>> getAllCachedPsi() {
    myQueue.cleanupStaleReferences();
    if (myMap.isEmpty()) return Stream.empty();

    return myMap.values().stream().map(Reference::get).filter(Objects::nonNull);
  }

  boolean hasUnbindableCachedPsi() {
    Boolean answer = myHasUnbindableCachedPsi;
    if (answer == null) {
      myHasUnbindableCachedPsi = answer = calcHasUnbindableCachedPsi();
    }
    return answer;
  }

  private boolean calcHasUnbindableCachedPsi() {
    myQueue.cleanupStaleReferences();
    if (myMap.isEmpty()) return false;

    return getAllCachedPsi().anyMatch(e -> e.getStubIndex() < 0);
  }

  void clearStubIndexCache() {
    myHasUnbindableCachedPsi = null;
  }

  private static class MyReference extends WeakReference<StubBasedPsiElementBase<?>> {
    final AstPath pathRef;

    MyReference(StubBasedPsiElementBase psi, AstPath ref, ReferenceQueue<StubBasedPsiElementBase<?>> queue) {
      super(psi, queue);
      pathRef = ref;
    }
  }

  private static class MyReferenceQueue extends ReferenceQueue<StubBasedPsiElementBase<?>> {
    MyReferenceQueue(Project project) {
      LowMemoryWatcher.register(() -> cleanupStaleReferences(), project);
    }

    void cleanupStaleReferences() {
      while (true) {
        MyReference reference = (MyReference)poll();
        if (reference == null) break;

        AstPath key = reference.pathRef;
        AstPathPsiMap refToPsi = key.getContainingFile().getRefToPsi();
        refToPsi.myMap.remove(key, reference);
        refToPsi.clearStubIndexCache();
      }
    }

  }
}

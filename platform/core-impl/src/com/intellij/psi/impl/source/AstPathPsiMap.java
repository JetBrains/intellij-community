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

import com.intellij.concurrency.JobScheduler;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.psi.impl.source.tree.AstPath;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A weak cache for all instantiated stub-based PSI to allow {@link CompositeElement#getPsi()} return it when AST is reloaded.<p/>
 * @author peter
 */
class AstPathPsiMap {
  /**
   * Not using ConcurrentWeakValueMap because we need to clean them up as ASTs in thousands of files are created and gc-ed.
   * So we have a single shared reference queue {@link MyReference#ourQueue} for that.
   * Otherwise the files end up retaining lots of maps with all-gc-ed stuff inside, but the maps are still very large.
   */
  private final ConcurrentMap<AstPath, MyReference> myMap = ContainerUtil.newConcurrentMap();

  static {
    JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        // clean up AstPath objects when no PSI is accessed, e.g. after project closing
        processQueue();
      }
    }, 5, 5, TimeUnit.SECONDS);
  }

  void invalidatePsi() {
    processQueue();
    for (MyReference reference : myMap.values()) {
      StubBasedPsiElementBase<?> psi = SoftReference.dereference(reference);
      if (psi != null) {
        psi.setSubstrateRef(SubstrateRef.createInvalidRef(psi));
      }
    }
    myMap.clear();
  }

  void switchToStrongRefs() {
    processQueue();
    for (MyReference reference : myMap.values()) {
      StubBasedPsiElementBase<?> psi = SoftReference.dereference(reference);
      if (psi != null) {
        CompositeElement node = (CompositeElement)psi.getNode();
        node.setPsi(psi);
        psi.setSubstrateRef(SubstrateRef.createAstStrongRef(node));
      }
    }
    myMap.clear();
  }

  @Nullable
  StubBasedPsiElementBase<?> getCachedPsi(@NotNull AstPath ref) {
    return SoftReference.dereference(myMap.get(ref));
  }

  @NotNull
  StubBasedPsiElementBase<?> cachePsi(@NotNull AstPath key, @NotNull StubBasedPsiElementBase psi) {
    processQueue();
    myMap.put(key, new MyReference(psi, key));
    psi.setSubstrateRef(key);
    return psi;
  }

  private static void processQueue() {
    while (true) {
      MyReference reference = (MyReference)MyReference.ourQueue.poll();
      if (reference == null) break;

      AstPath key = reference.pathRef;
      key.getContainingFile().getRefToPsi().myMap.remove(key, reference);
    }
  }

  private static class MyReference extends WeakReference<StubBasedPsiElementBase<?>> {
    static final ReferenceQueue<StubBasedPsiElementBase<?>> ourQueue = new ReferenceQueue<StubBasedPsiElementBase<?>>();
    final AstPath pathRef;

    MyReference(StubBasedPsiElementBase psi, AstPath ref) {
      super(psi, ourQueue);
      pathRef = ref;
    }
  }

}

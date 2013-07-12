/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.SLRUCache;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author Kirill Likhodedov
 */
public class VcsCommitCache<T extends CommitParents> {

  private final SLRUCache<Hash, T> myCache = new SLRUCache<Hash, T>(5000, 5000) {
    @NotNull
    @Override
    public T createValue(Hash key) {
      try {
        // TODO load in background OR prove that this is a not normal situation when something is not in the cache and queried from the UI
        //assert !EventQueue.isDispatchThread();
        return (T)myLogProvider.readDetails(myRoot, Collections.singletonList(key.toStrHash())).get(0);
      }
      catch (VcsException e) {
        // TODO
        throw new RuntimeException(e);
      }
    }
  };
  private final VcsLogProvider myLogProvider;
  private final VirtualFile myRoot;

  public VcsCommitCache(VcsLogProvider logProvider, VirtualFile root) {
    myLogProvider = logProvider;
    myRoot = root;
  }

  public void put(Hash hash, T commit) {
    myCache.put(hash, commit);
  }

  public boolean isKeyCached(Hash hash) {
    return myCache.getIfCached(hash) != null;
  }

  public T get(Hash hash) {
    return myCache.get(hash);
  }
}

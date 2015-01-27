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
package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.util.diff.fragments.LineFragments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class LineFragmentCache {
  private final long myModificationStamp1;
  private final long myModificationStamp2;

  @NotNull private final Map<ComparisonPolicy, LineFragments> myFragments;

  public LineFragmentCache(@NotNull LineFragmentCache cache) {
    myModificationStamp1 = cache.myModificationStamp1;
    myModificationStamp2 = cache.myModificationStamp2;

    myFragments = new HashMap<ComparisonPolicy, LineFragments>(3);
    for (Map.Entry<ComparisonPolicy, LineFragments> entry : cache.myFragments.entrySet()) {
      myFragments.put(entry.getKey(), entry.getValue());
    }
  }

  public LineFragmentCache(long modificationStamp1,
                           long modificationStamp2) {
    myModificationStamp1 = modificationStamp1;
    myModificationStamp2 = modificationStamp2;
    myFragments = new HashMap<ComparisonPolicy, LineFragments>(3);
  }

  public long getStamp1() {
    return myModificationStamp1;
  }

  public long getStamp2() {
    return myModificationStamp2;
  }

  public boolean checkStamps(long stamp1, long stamp2) {
    return myModificationStamp1 == stamp1 && myModificationStamp2 == stamp2;
  }

  @Nullable
  public LineFragments getFragments(@NotNull ComparisonPolicy policy) {
    return myFragments.get(policy);
  }

  public void putFragments(@NotNull ComparisonPolicy policy, @NotNull LineFragments fragments) {
    myFragments.put(policy, fragments);
  }
}

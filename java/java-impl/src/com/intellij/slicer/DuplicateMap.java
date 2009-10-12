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
package com.intellij.slicer;

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.Map;

/**
* @author cdr
*/ // rehash map on each PSI modification since SmartPsiPointer's hashCode() and equals() are changed
class DuplicateMap {
  private final Map<SliceUsage, SliceNode> myDuplicates = new THashMap<SliceUsage, SliceNode>(USAGEINFO_EQUALITY);
  private static final TObjectHashingStrategy<SliceUsage> USAGEINFO_EQUALITY = new TObjectHashingStrategy<SliceUsage>() {
    public int computeHashCode(SliceUsage object) {
      return object.getUsageInfo().hashCode();
    }

    public boolean equals(SliceUsage o1, SliceUsage o2) {
      return o1.getUsageInfo().equals(o2.getUsageInfo());
    }
  };
  private static final TObjectHashingStrategy<SliceNode> SLICE_NODE_EQUALITY = new TObjectHashingStrategy<SliceNode>() {
    public int computeHashCode(SliceNode object) {
      return object.getValue().getUsageInfo().hashCode();
    }

    public boolean equals(SliceNode o1, SliceNode o2) {
      return o1.getValue().getUsageInfo().equals(o2.getValue().getUsageInfo());
    }
  };

  public SliceNode putNodeCheckDupe(SliceNode node) {
    SliceUsage usage = node.getValue();
    SliceNode eq = myDuplicates.get(usage);
    if (eq == null) {
      myDuplicates.put(usage, node);
    }
    return eq;
  }

  public void clear() {
    myDuplicates.clear();
  }
}

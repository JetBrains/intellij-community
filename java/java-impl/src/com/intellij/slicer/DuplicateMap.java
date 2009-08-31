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

package com.intellij.formatting;

import java.util.*;

class WrapImpl extends Wrap {
  private LeafBlockWrapper myFirstEntry = null;
  private int myFirstPosition = -1;
  private int myFlags;
  private static int ourId = 0;

  private static final Set<WrapImpl> emptyParentsSet = Collections.emptySet();
  private Set<WrapImpl> myParents = emptyParentsSet;
  private Map<WrapImpl, Collection<LeafBlockWrapper>> myIgnoredWraps;

  private static final int IGNORE_PARENT_WRAPS_MASK = 1;
  private static final int ACTIVE_MASK = 2;
  private static final int WRAP_FIRST_ELEMENT_MASK = 4;
  private static final int TYPE_MASK = 0x18;
  private static final int TYPE_SHIFT = 3;
  private static final int ID_SHIFT = 5;
  private static final int ID_MAX = 1 << 26;
  private static final Type[] myTypes = Type.values();

  public boolean isChildOf(final WrapImpl wrap, LeafBlockWrapper leaf) {
    if ((myFlags & IGNORE_PARENT_WRAPS_MASK) != 0) return false;
    if (leaf != null && myIgnoredWraps != null) {
      Collection<LeafBlockWrapper> leaves = myIgnoredWraps.get(wrap);
      if (leaves != null && leaves.contains(leaf)) {
        return false;
      }
    }
    for (WrapImpl parent : myParents) {
      if (parent == wrap) return true;
      if (parent.isChildOf(wrap, leaf)) return true;
    }
    return false;
  }

  void registerParent(WrapImpl parent) {
    if (parent == this) return;
    if (parent == null) return;
    if (parent.isChildOf(this, null)) return;
    if (myParents == emptyParentsSet) myParents = new HashSet<WrapImpl>(5);
    myParents.add(parent);
  }

  public void reset() {
    myFirstEntry = null;
    myFirstPosition = -1;
    myFlags &=~ ACTIVE_MASK;
  }

  public WrapImpl getParent(){
    if (myParents != null && myParents.size() == 1) {
      return myParents.iterator().next();
    }

    return null;
  }

  public final boolean getIgnoreParentWraps() {
    return (myFlags & IGNORE_PARENT_WRAPS_MASK) != 0;
  }

  public void ignoreParentWrap(final WrapImpl wrap, final LeafBlockWrapper currentBlock) {
    if (myIgnoredWraps == null) {
      myIgnoredWraps = new HashMap<WrapImpl, Collection<LeafBlockWrapper>>(5);
    }
    if (myIgnoredWraps.get(wrap) == null) {
      myIgnoredWraps.put(wrap, new HashSet<LeafBlockWrapper>(2));
    }
    myIgnoredWraps.get(wrap).add(currentBlock);
  }

  public boolean isFirstWrapped(final LeafBlockWrapper currentBlock) {
    return myFirstEntry != null && myFirstEntry == currentBlock;
  }

  static enum Type{
    DO_NOT_WRAP, WRAP_AS_NEEDED, CHOP_IF_NEEDED, WRAP_ALWAYS
  }

  LeafBlockWrapper getFirstEntry() {
    return myFirstEntry;
  }

  void markAsUsed() {
    myFirstEntry = null;
    myFlags |= ACTIVE_MASK;
  }

  void processNextEntry(final int startOffset) {
    if (myFirstPosition < 0) {
      myFirstPosition = startOffset;
    }
  }

  int getFirstPosition() {
    return myFirstPosition;
  }

  public WrapImpl(WrapType type, boolean wrapFirstElement) {
    Type myType;

    switch(type) {
        case NORMAL: myType = Type.WRAP_AS_NEEDED;break;
        case NONE: myType= Type.DO_NOT_WRAP;break;
        case ALWAYS: myType = Type.WRAP_ALWAYS; break;
        default: myType = Type.CHOP_IF_NEEDED;
    }

    int myId = ourId++;
    assert myId < ID_MAX;
    myFlags |= (wrapFirstElement ? WRAP_FIRST_ELEMENT_MASK:0) | (myType.ordinal() << TYPE_SHIFT) | (myId << ID_SHIFT);
  }

  final Type getType() {
    return myTypes[(myFlags & TYPE_MASK) >>> TYPE_SHIFT];
  }

  final boolean isWrapFirstElement() {
    return (myFlags & WRAP_FIRST_ELEMENT_MASK) != 0;
  }

  void saveFirstEntry(LeafBlockWrapper current) {
    if (myFirstEntry  == null) {
      myFirstEntry = current;
    }
  }

  final boolean isIsActive() {
    return (myFlags & ACTIVE_MASK) != 0;
  }

  public String toString() {
    return getType().toString();
  }

  public String getId() {
    return String.valueOf(myFlags >>> ID_SHIFT);
  }

  public void ignoreParentWraps() {
    myFlags |= IGNORE_PARENT_WRAPS_MASK;
  }
}

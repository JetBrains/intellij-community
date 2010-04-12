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
    if (getIgnoreParentWraps()) return false;
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

  /**
   * Allows to register given wrap as a parent of the current wrap.
   * <p/>
   * <code>'Parent'</code> wrap registration here means that {@link #isChildOf(WrapImpl, LeafBlockWrapper)} returns
   * <code>'true'</code> if given wrap is used as a <code>'parent'</code> argument.
   *
   * @param parent    parent wrap to register for the current wrap
   */
  void registerParent(WrapImpl parent) {
    if (parent == this) return;
    if (parent == null) return;
    if (parent.isChildOf(this, null)) return;
    if (myParents == emptyParentsSet) myParents = new HashSet<WrapImpl>(5);
    myParents.add(parent);
  }

  /**
   * Resets the following state of the current wrap object:
   * <ul>
   *   <li>'{@link #getFirstEntry() firstEntry}' property value is set to <code>null</code>;</li>
   *   <li>'{@link #getFirstPosition() firstPosition}' property value is set to <code>'-1'</code>;</li>
   *   <li>'{@link #isIsActive() isActive}' property value is set to <code>'false'</code>;</li>
   * </ul>
   */
  public void reset() {
    myFirstEntry = null;
    myFirstPosition = -1;
    myFlags &=~ ACTIVE_MASK;
  }

  /**
   * Allows to check if single wrap is {@link #registerParent(WrapImpl) registered} for the current wrap and return
   * it in case of success.
   *
   * @return    single wrap registered as a parent of the current wrap if any;
   *            <code>null</code> if no wraps or more than one wrap is registered as a parent for the current wrap
   */
  public WrapImpl getParent(){
    if (myParents != null && myParents.size() == 1) {
      return myParents.iterator().next();
    }

    return null;
  }

  public final boolean getIgnoreParentWraps() {
    return (myFlags & IGNORE_PARENT_WRAPS_MASK) != 0;
  }

  /**
   * Allows to mark given wrap as <code>'ignored'</code> for the given block. I.e. 'false' will be returned
   * for subsequent calls to {@link #isChildOf(WrapImpl, LeafBlockWrapper)} with the same arguments.
   *
   * @param wrap          target wrap
   * @param currentBlock  target block for which given wrap should be ignored
   */
  public void ignoreParentWrap(final WrapImpl wrap, final LeafBlockWrapper currentBlock) {
    if (myIgnoredWraps == null) {
      myIgnoredWraps = new HashMap<WrapImpl, Collection<LeafBlockWrapper>>(5);
    }
    if (myIgnoredWraps.get(wrap) == null) {
      myIgnoredWraps.put(wrap, new HashSet<LeafBlockWrapper>(2));
    }
    myIgnoredWraps.get(wrap).add(currentBlock);
  }

  /**
   * Allows to check if given block is used as a '{@link #getFirstEntry() firstEntry}' property value of the current wrap object.
   * <p/>
   * <b>Note:</b> object identity (<code>'=='</code> operator) is used during checking given block against the current
   * '{@link #getFirstEntry() firstEntry}' property value.
   *
   * @param currentBlock    block to check
   * @return                <code>true</code> if '{@link #getFirstEntry() firstEntry}' property value is defined
   *                        (not <code>null</code>) and is the same as the given block; <code>false</code> otherwise
   */
  public boolean isFirstWrapped(final LeafBlockWrapper currentBlock) {
    return myFirstEntry != null && myFirstEntry == currentBlock;
  }

  static enum Type{
    DO_NOT_WRAP, WRAP_AS_NEEDED, CHOP_IF_NEEDED, WRAP_ALWAYS
  }

  LeafBlockWrapper getFirstEntry() {
    return myFirstEntry;
  }

  /**
   * Performs the following changes at wrap object state:
   * <ul>
   *   <li>'{@link #getFirstEntry() firstEntry}' property value is dropped (set to <code>null</code>)</li>
   *   <li>'{@link #isIsActive() isActive}' property value is dropped (set to <code>false</code>)</li>
   * </ul>
   */
  void markAsUsed() {
    myFirstEntry = null;
    myFlags |= ACTIVE_MASK;
  }

  /**
   * Applies given value to the '{@link #getFirstPosition() firstPosition}' property value if it's value is undefined at the moment
   * (has negative value).
   *
   * @param startOffset   new '{@link #getFirstPosition() firstPosition}' property value to use if current value is undefined (negative)
   */
  void processNextEntry(final int startOffset) {
    if (myFirstPosition < 0) {
      myFirstPosition = startOffset;
    }
  }

  /**
   * @return    '{@link #getFirstPosition() firstPosition}' property value defined previously via {@link #processNextEntry(int)} if any;
   *            <code>'-1'</code> otherwise
   */
  int getFirstPosition() {
    return myFirstPosition;
  }

  public WrapImpl(WrapType type, boolean wrapFirstElement) {
    Type myType;

    switch(type) {
        case NORMAL: myType = Type.WRAP_AS_NEEDED;break;
        case NONE: myType= Type.DO_NOT_WRAP;break;
        case ALWAYS: myType = Type.WRAP_ALWAYS; break;
        case CHOP_DOWN_IF_LONG:
        default: myType = Type.CHOP_IF_NEEDED;
    }

    int myId = ourId++;
    assert myId < ID_MAX;
    myFlags |= (wrapFirstElement ? WRAP_FIRST_ELEMENT_MASK:0) | (myType.ordinal() << TYPE_SHIFT) | (myId << ID_SHIFT);
  }

  final Type getType() {
    return myTypes[(myFlags & TYPE_MASK) >>> TYPE_SHIFT];
  }

  /**
   * Allows to check if current wrap object is configured to wrap first element. This property is defined at
   * {@link #WrapImpl(WrapType, boolean) constructor} during object initialization and can't be changed later.
   *
   * @return    <code>'wrapFirstElement'</code> property value
   */
  final boolean isWrapFirstElement() {
    return (myFlags & WRAP_FIRST_ELEMENT_MASK) != 0;
  }

  /**
   * Allows to define given block as a <code>'first entry'</code> of the current wrap object. I.e. given block is returned on
   * subsequent {@link #getFirstEntry()} calls.
   * <p/>
   * <b>Note:</b> given block is applied only if '{@link #getFirstEntry() firstEntry}' property is undefined,
   * i.e. has a <code>null</code> value.
   *
   * @param current     block to remember as a first entry of the current wrap object
   */
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

  /**
   * Allows to instruct current wrap to ignore all parent wraps, i.e. all calls to {@link #isChildOf(WrapImpl, LeafBlockWrapper)}
   * return <code>'false'</code> after invocation of this method.
   */
  public void ignoreParentWraps() {
    myFlags |= IGNORE_PARENT_WRAPS_MASK;
  }
}

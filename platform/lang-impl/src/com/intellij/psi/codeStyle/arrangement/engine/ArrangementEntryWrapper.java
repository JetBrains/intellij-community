// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.engine;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Auxiliary data structure used {@link ArrangementEngine#arrange(PsiFile, Collection) arrangement}.
 * <p/>
 * The general idea is to provide the following:
 * <pre>
 * <ul>
 *   <li>'parent-child' and 'sibling' relations between the {@link ArrangementEntry entries};</li>
 *   <li>ability to reflect actual entry range (after its arrangement and/or blank lines addition/removal);</li>
 * </ul>
 * </pre>
 * <p/>
 * Not thread-safe.
 */
@ApiStatus.Internal
public final class ArrangementEntryWrapper<E extends ArrangementEntry> {

  private final @NotNull List<ArrangementEntryWrapper<E>> myChildren = new ArrayList<>();
  private final @NotNull E myEntry;

  private @Nullable ArrangementEntryWrapper<E> myParent;
  private @Nullable ArrangementEntryWrapper<E> myPrevious;
  private @Nullable ArrangementEntryWrapper<E> myNext;

  private int myStartOffset;
  private int myEndOffset;
  private int myBlankLinesBefore;

  @SuppressWarnings("unchecked")
  public ArrangementEntryWrapper(@NotNull E entry) {
    myEntry = entry;
    myStartOffset = entry.getStartOffset();
    myEndOffset = entry.getEndOffset();
    ArrangementEntryWrapper<E> previous = null;
    for (ArrangementEntry child : entry.getChildren()) {
      ArrangementEntryWrapper<E> childWrapper = new ArrangementEntryWrapper<>((E)child);
      childWrapper.setParent(this);
      if (previous != null) {
        previous.setNext(childWrapper);
        childWrapper.setPrevious(previous);
      }
      previous = childWrapper;
      myChildren.add(childWrapper);
    }
  }

  public @NotNull E getEntry() {
    return myEntry;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public void setEndOffset(int endOffset) {
    myEndOffset = endOffset;
  }

  public @Nullable ArrangementEntryWrapper<E> getParent() {
    return myParent;
  }

  public void setParent(@Nullable ArrangementEntryWrapper<E> parent) {
    myParent = parent;
  }

  public @Nullable ArrangementEntryWrapper<E> getPrevious() {
    return myPrevious;
  }

  public void setPrevious(@Nullable ArrangementEntryWrapper<E> previous) {
    myPrevious = previous;
  }

  public @Nullable ArrangementEntryWrapper<E> getNext() {
    return myNext;
  }

  public int getBlankLinesBefore() {
    return myBlankLinesBefore;
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  public void updateBlankLines(@NotNull Document document) {
    myBlankLinesBefore = 0;
    int lineFeeds = 0;
    CharSequence text = document.getCharsSequence();
    for (int current = getStartOffset() - 1; current >= 0; current--) {
      current = CharArrayUtil.shiftBackward(text, current, " \t");
      if (current > 0 && text.charAt(current) == '\n') lineFeeds++;
      else break;
    }
    if (lineFeeds > 0) myBlankLinesBefore = lineFeeds - 1;
  }

  public void setNext(@Nullable ArrangementEntryWrapper<E> next) {
    myNext = next;
  }

  public @NotNull List<ArrangementEntryWrapper<E>> getChildren() {
    return myChildren;
  }

  public void applyShift(int shift) {
    myStartOffset += shift;
    myEndOffset += shift;
    for (ArrangementEntryWrapper<E> child : myChildren) {
      child.applyShift(shift);
    }
  }

  @Override
  public int hashCode() {
    return myEntry.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrangementEntryWrapper wrapper = (ArrangementEntryWrapper)o;
    return myEntry.equals(wrapper.myEntry);
  }

  @Override
  public String toString() {
    return String.format("range: [%d; %d), entry: %s", myStartOffset, myEndOffset, myEntry.toString());
  }
}

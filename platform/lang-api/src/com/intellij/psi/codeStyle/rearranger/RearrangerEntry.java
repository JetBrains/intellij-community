package com.intellij.psi.codeStyle.rearranger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Represents a processing unit during 'rearrangement' operation. I.e. entry's position can be changed during the processing.
 * <p/>
 * Example: we can provide a grouping rule for class methods (e.g. getters and setters). Every such method will be represented by
 * a {@link RearrangerEntry} then.
 * <p/>
 * The entries can be organised into hierarchies where every sub-hierarchy is independent
 * (see {@link #getParent()}, {@link #getChildren()}).
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/16/12 3:31 PM
 */
public interface RearrangerEntry {

  /**
   * @return    parent entry, e.g. it would be a class entry for a method entry
   * @see #getChildren()
   */
  @Nullable
  RearrangerEntry getParent();

  /**
   * Entries can be organised into hierarchies, that means that only siblings position can be changed during the rearrangement.
   * <p/>
   * Example: there are two classes at file and every class has two methods. Rearranger should not swap positions of methods
   * from the different classes here.
   * 
   * @return    current entry's children. Empty collection if there are no children
   * @see #getParent() 
   */
  @NotNull
  Collection<? extends RearrangerEntry> getChildren();

  /**
   * @return    start offset of the current entry (inclusive) within the target document. Rearranger engine uses this information
   *            to move rearranged entries
   */
  int getStartOffset();

  /**
   * @return    end offset of the current entry (exclusive) within the target document. Rearranger engine uses this information
   *            to move rearranged entries
   */
  int getEndOffset();
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a processing unit during 'rearrangement' operation. I.e. entry's position can be changed during the processing.
 * <p/>
 * Example: we can provide a grouping rule for class methods (e.g. getters and setters). Every such method will be represented by
 * a {@link ArrangementEntry} then.
 * <p/>
 * The entries can be organised into hierarchies where every sub-hierarchy is independent
 * (see {@link #getParent()}, {@link #getChildren()}).
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/16/12 3:31 PM
 */
public interface ArrangementEntry {

  /**
   * @return    parent entry, e.g. it would be a class entry for a method entry
   * @see #getChildren()
   */
  @Nullable
  ArrangementEntry getParent();

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
  List<? extends ArrangementEntry> getChildren();

  /**
   * There is a possible case that particular entry position depends on another entries positions. E.g. static initialization
   * block which uses static fields from the same class must be declared after them.
   * <p/>
   * This method allows to answer what sibling entries must be located before the current one at the resulting arrangement algorithm.
   * <p/>
   * There is also a special case when a list with the single entry which is the current's entry {@link #getParent() parent}
   * is returned - that means that current entry should be arranged to be the first child.
   * 
   * @return    current entry's dependencies (if any)
   */
  @Nullable
  List<? extends ArrangementEntry> getDependencies();

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

  /**
   * Sometimes we want particular entry to serve just as another entries holder. For example, we might want to arrange
   * anonymous class entries but don't want the class itself, say, to be arranged with normal inner classes.
   * <p/>
   * That is achieved for entries which return <code>'false'</code> from this method call.
   * 
   * @return    <code>true</code> if current entry can be {@link ArrangementEntryMatcher#isMatched(ArrangementEntry) matched};
   *            <code>false</code> otherwise
   */
  boolean canBeMatched();
}

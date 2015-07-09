/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * An interface used to support tracking of common PSI modifications. It has three main usage patterns:
 * <ol>
 *   <li/> Get a stamp of current PSI state. This stamp is increased when PSI is modified, allowing other subsystems
 *   to check if PSI has changed since they accessed it last time. This can be used to flush and rebuild various internal caches.
 *   See {@link #getModificationCount()}, {@link #getJavaStructureModificationCount()}, {@link #getOutOfCodeBlockModificationCount()}
 *
 *   <li/> Make a {@link com.intellij.psi.util.CachedValue} instance dependent on a specific PSI modification tracker.
 *   To achieve that, one should can one of the constants in this interface as {@link com.intellij.psi.util.CachedValueProvider.Result}
 *   dependencies.
 *   See {@link #MODIFICATION_COUNT}, {@link #JAVA_STRUCTURE_MODIFICATION_COUNT}, {@link #OUT_OF_CODE_BLOCK_MODIFICATION_COUNT}
 *
 *   <li/> Subscribe to any PSI change (for example, to drop caches in the listener manually).
 *   See {@link com.intellij.psi.util.PsiModificationTracker.Listener}
 *
 * </ol>
 */
public interface PsiModificationTracker extends ModificationTracker {

  /**
   * Provides a way to get the instance of {@link com.intellij.psi.util.PsiModificationTracker} corresponding to a given project.
   * @see #getInstance(com.intellij.openapi.project.Project)
   */
  class SERVICE {
    private SERVICE() {
    }

    /**
     * @param project
     * @return The instance of {@link com.intellij.psi.util.PsiModificationTracker} corresponding to the given project.
     */
    public static PsiModificationTracker getInstance(Project project) {
      return ServiceManager.getService(project, PsiModificationTracker.class);
    }
  }
  
  /**
   * This key can be passed as a dependency in a {@link com.intellij.psi.util.CachedValueProvider}.
   * The corresponding {@link com.intellij.psi.util.CachedValue} will then be flushed on every physical PSI change.
   * @see #getModificationCount()
   */
  Key MODIFICATION_COUNT = Key.create("MODIFICATION_COUNT");

  /**
   * This key can be passed as a dependency in a {@link com.intellij.psi.util.CachedValueProvider}.
   * The corresponding {@link com.intellij.psi.util.CachedValue} will then be flushed on every physical PSI change that doesn't happen inside a Java code block.
   * This can include changes on Java class or file level, or changes in non-Java files, e.g. XML. Rarely needed.
   * @see #getOutOfCodeBlockModificationCount()
   */
  Key OUT_OF_CODE_BLOCK_MODIFICATION_COUNT = Key.create("OUT_OF_CODE_BLOCK_MODIFICATION_COUNT");

  /**
   * This key can be passed as a dependency in a {@link com.intellij.psi.util.CachedValueProvider}.
   * The corresponding {@link com.intellij.psi.util.CachedValue} will then be flushed on every physical PSI change that can affect Java structure and resolve.
   * @see #getJavaStructureModificationCount()
   */
  Key JAVA_STRUCTURE_MODIFICATION_COUNT = Key.create("JAVA_STRUCTURE_MODIFICATION_COUNT");

  /**
   * A topic to subscribe for all PSI modification count changes.
   * @see com.intellij.util.messages.MessageBus
   */
  Topic<Listener> TOPIC = new Topic<Listener>("modification tracker", Listener.class, Topic.BroadcastDirection.TO_PARENT);

  /**
   * Tracks any PSI modification.
   * @return current counter value. Increased whenever any physical PSI is changed.
   */
  @Override
  long getModificationCount();

  /**
   * @return Same as {@link #getJavaStructureModificationCount()}, but also includes changes in non-Java files, e.g. XML. Rarely needed.
   */
  long getOutOfCodeBlockModificationCount();

  /**
   * @return an object returning {@link #getOutOfCodeBlockModificationCount()}
   */
  @NotNull
  ModificationTracker getOutOfCodeBlockModificationTracker();

  /**
   * Tracks structural Java modifications, i.e. the ones on class/method/field/file level. Modifications inside method bodies are not tracked.
   * Useful to work with resolve caches that only depend on Java structure, and not the method code.
   * @return current counter value. Increased whenever any physical PSI in Java structure is changed.
   */
  long getJavaStructureModificationCount();

  /**
   * @return an object returning {@link #getJavaStructureModificationCount()}
   */
  @NotNull
  ModificationTracker getJavaStructureModificationTracker();

  /**
   * A listener to be notified on any PSI modification count change (which happens on any physical PSI change).
   * @see #TOPIC
   */
  interface Listener {

    /**
     * A method invoked on Swing EventDispatchThread each time any physical PSI change is detected
     */
    void modificationCountChanged();
  }
}

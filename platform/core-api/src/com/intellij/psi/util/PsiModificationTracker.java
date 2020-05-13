// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
 *   <li/> Make a {@link CachedValue} instance dependent on a specific PSI modification tracker.
 *   To achieve that, one should can one of the constants in this interface as {@link CachedValueProvider.Result}
 *   dependencies.
 *
 *   <li/> Subscribe to any PSI change (for example, to drop caches in the listener manually).
 *   See {@link PsiModificationTracker.Listener}
 *
 * </ol>
 */
public interface PsiModificationTracker extends ModificationTracker {
  /**
   * Provides a way to get the instance of {@link PsiModificationTracker} corresponding to a given project.
   * @see #getInstance(Project)
   */
  class SERVICE {
    private SERVICE() {
    }

    /**
     * @return The instance of {@link PsiModificationTracker} corresponding to the given project.
     */
    public static PsiModificationTracker getInstance(Project project) {
      return ServiceManager.getService(project, PsiModificationTracker.class);
    }
  }

  /**
   * This key can be passed as a dependency in a {@link CachedValueProvider}.
   * The corresponding {@link CachedValue} will then be flushed on every physical PSI change.
   * @see #getModificationCount()
   */
  Key MODIFICATION_COUNT = Key.create("MODIFICATION_COUNT");

  /**
   * This key can be passed as a dependency in a {@link CachedValueProvider}.
   * The corresponding {@link CachedValue} will then be flushed on every physical PSI change that doesn't happen inside a Java code block.
   * This can include changes on Java class or file level, or changes in non-Java files, e.g. XML. Rarely needed.
   * @see #getOutOfCodeBlockModificationCount()
   * @deprecated rarely supported by language plugins; also a wrong way for optimisations
   */
  @Deprecated
  Key OUT_OF_CODE_BLOCK_MODIFICATION_COUNT = Key.create("OUT_OF_CODE_BLOCK_MODIFICATION_COUNT");

  /**
   * This key can be passed as a dependency in a {@link CachedValueProvider}.
   * The corresponding {@link CachedValue} will then be flushed on every physical PSI change that can affect Java structure and resolve.
   * @see #getJavaStructureModificationCount()
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  Key JAVA_STRUCTURE_MODIFICATION_COUNT = Key.create("JAVA_STRUCTURE_MODIFICATION_COUNT");

  /**
   * A topic to subscribe for all PSI modification count changes.
   * @see com.intellij.util.messages.MessageBus
   */
  @Topic.ProjectLevel
  Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_PARENT);

  /**
   * Tracks any PSI modification.
   * @return current counter value. Increased whenever any physical PSI is changed.
   */
  @Override
  long getModificationCount();

  /**
   * @return Same as {@link #getJavaStructureModificationCount()}, but also includes changes in non-Java files, e.g. XML. Rarely needed.
   * @deprecated rarely supported by language plugins; also a wrong way for optimisations
   */
  @Deprecated
  long getOutOfCodeBlockModificationCount();

  /**
   * @return an object returning {@link #getOutOfCodeBlockModificationCount()}
   * @deprecated rarely supported by language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @NotNull
  ModificationTracker getOutOfCodeBlockModificationTracker();

  /**
   * Tracks structural Java modifications, i.e. the ones on class/method/field/file level. Modifications inside method bodies are not tracked.
   * Useful to work with resolve caches that only depend on Java structure, and not the method code.
   * @return current counter value. Increased whenever any physical PSI in Java structure is changed.
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  long getJavaStructureModificationCount();

  /**
   * @return an object returning {@link #getJavaStructureModificationCount()}
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @NotNull
  ModificationTracker getJavaStructureModificationTracker();

  /**
   * A listener to be notified on any PSI modification count change (which happens on any physical PSI change).
   * @see #TOPIC
   */
  @FunctionalInterface
  interface Listener {
    /**
     * A method invoked on Swing EventDispatchThread each time any physical PSI change is detected
     */
    void modificationCountChanged();
  }
}

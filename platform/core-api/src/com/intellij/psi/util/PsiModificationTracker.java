// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * An interface used to support tracking of common PSI modifications. It has three main usage patterns:
 * <ol>
 *   <li/> Get a stamp of current PSI state. This stamp is increased when PSI is modified, allowing other subsystems
 *   to check if PSI has changed since they accessed it last time. This can be used to flush and rebuild various internal caches.
 *   See {@link #getModificationCount()}
 *
 *   <li/> Make a {@link CachedValue} instance outdated on every physical PSI change.
 *   To achieve that, one should use {@link #MODIFICATION_COUNT} as {@link CachedValueProvider.Result} dependency.
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
  final class SERVICE {
    private SERVICE() {
    }

    /**
     * @return The instance of {@link PsiModificationTracker} corresponding to the given project.
     */
    public static PsiModificationTracker getInstance(Project project) {
      return project.getService(PsiModificationTracker.class);
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
   * @deprecated rarely supported by language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Key OUT_OF_CODE_BLOCK_MODIFICATION_COUNT = MODIFICATION_COUNT;

  /**
   * This key can be passed as a dependency in a {@link CachedValueProvider}.
   * The corresponding {@link CachedValue} will then be flushed on every physical PSI change that can affect Java structure and resolve.
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  Key JAVA_STRUCTURE_MODIFICATION_COUNT = MODIFICATION_COUNT;

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
   * @return an object returning {@link #getModificationCount()}
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull
  ModificationTracker getJavaStructureModificationTracker();

  /**
   * @return modification tracker incremented on changes in files with the passed language.
   */
  @NotNull ModificationTracker forLanguage(@NotNull Language language);

  /**
   * @return modification tracker incremented on changes in files with language that matches the passed condition.
   */
  @NotNull ModificationTracker forLanguages(@NotNull Predicate<? super Language> condition);

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

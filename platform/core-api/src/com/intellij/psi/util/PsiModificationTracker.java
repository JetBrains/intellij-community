// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * An interface used to support tracking of common PSI modifications. It has three main usage patterns:
 * <ol>
 *   <li>Get a stamp of the current PSI state. This stamp is incremented when any PSI is modified, allowing other subsystems
 *   to check if PSI has changed since they accessed it last time. This can be used to flush and rebuild various internal caches.
 *   See {@link #getModificationCount()}</li>
 *
 *   <li>Make a {@link CachedValue} instance outdated on every {@link PsiElement#isPhysical() physical} PSI change.
 *   To achieve that, use {@link #MODIFICATION_COUNT} as a dependency of {@link CachedValueProvider.Result}.</li>
 *
 *   <li>Subscribe to any PSI changes (for example, to drop caches in the listener manually).
 *   See {@link PsiModificationTracker.Listener}</li>
 *
 * </ol>
 */
public interface PsiModificationTracker extends ModificationTracker {

  /**
   * @deprecated use {@link PsiModificationTracker#getInstance(Project)} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  final class SERVICE {
    private SERVICE() {
    }

    public static PsiModificationTracker getInstance(Project project) {
      return PsiModificationTracker.getInstance(project);
    }
  }

  /**
   * @return The instance of {@link PsiModificationTracker} corresponding to the given project.
   * @see #MODIFICATION_COUNT
   */
  static PsiModificationTracker getInstance(Project project) {
    return project.getService(PsiModificationTracker.class);
  }

  /**
   * This key can be passed as a dependency to a {@link CachedValueProvider.Result} (or one of its static factory methods).
   * <p>
   * A {@link CachedValue} that has this key as a dependency becomes outdated when there is any change to {@link PsiElement#isPhysical() physical} PSI,
   * and when Dumb Mode is entered or exited.
   * <p>
   * This means <i>literally every PSI change</i>, which may be too broad for your specific use case.
   * For example, if you create a {@link CachedValue} with this key as a dependency,
   * and it will {@link CachedValue#getValue() be queried} whenever the user edits some code,
   * then such a cache won't improve performance at all (likely, it'll only make it worse).
   * <p>
   * If the above is a problem for your use case, consider using a {@link #forLanguage(Language) language-specific PsiModificationTracker}:
   * <pre>{@code
   * PsiModificationTracker.getInstance(project).forLanguage(JavaLanguage.INSTANCE)
   * }</pre>
   * This causes the {@link CachedValue} to become outdated whenever any PSI change happens in Java files.
   * <p>
   * Beware of possible tricky corner cases, though.
   * For example, you can't use Java's language modification tracker
   * for Java resolve, because Java resolve depends on other JVM languages.
   *
   * @see CachedValueProvider.Result#getDependencyItems()
   * @see #getModificationCount()
   */
  Key MODIFICATION_COUNT = Key.create("MODIFICATION_COUNT");

  /**
   * This key can be passed as a dependency in a {@link CachedValueProvider}.
   * The corresponding {@link CachedValue} will then be flushed on every physical PSI change that can affect Java structure and resolve.
   *
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated @ApiStatus.ScheduledForRemoval Key JAVA_STRUCTURE_MODIFICATION_COUNT = MODIFICATION_COUNT;

  /**
   * A topic to subscribe for all PSI modification count changes.
   *
   * @see com.intellij.util.messages.MessageBus
   */
  @Topic.ProjectLevel Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.TO_PARENT, true);

  /**
   * Tracks any PSI modification.
   *
   * @return The current value of the modification counter (aka "stamp").
   * Incremented whenever any {@link PsiElement#isPhysical() physical} PSI is changed.
   */
  @Override
  long getModificationCount();

  /**
   * @return an object returning {@link #getModificationCount()}
   * @deprecated rarely supported by JVM language plugins; also a wrong way for optimisations
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @NotNull ModificationTracker getJavaStructureModificationTracker();

  /**
   * @return modification tracker incremented on changes in files with the passed language.
   */
  @NotNull ModificationTracker forLanguage(@NotNull Language language);

  /**
   * @return modification tracker incremented on changes in files with language that matches the passed condition.
   */
  @NotNull ModificationTracker forLanguages(@NotNull Predicate<? super Language> condition);

  /**
   * A listener to be notified on any PSI modification count change
   * (which happens on any {@link PsiElement#isPhysical() physical} PSI change).
   *
   * @see #TOPIC
   */
  @FunctionalInterface
  interface Listener {
    /**
     * Invoked on EDT on each {@link PsiElement#isPhysical() physical} PSI change.
     */
    void modificationCountChanged();
  }
}

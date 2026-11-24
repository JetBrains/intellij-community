// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.util.messages.ReadOnlyTopic;
import com.intellij.util.messages.Topic;

/**
 * Same as {@link AnyPsiChangeListener}, but runs in place, on the thread that changes the PSI.
 * <p>
 * A physical change runs under the write lock, and the thread can be a background thread.
 * A non-physical change can run on any thread.
 * {@link #beforePsiChanged} and {@link #afterPsiChanged} can run on different threads, so keep no thread-affine state.
 * <p>
 * A subscriber of the legacy {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} gets a physical change on the EDT.
 */
public interface AnyPsiChangeListenerBackgroundable extends AnyPsiChangeListener {

  /**
   * Only {@link PsiManagerImpl} publishes on this topic. To report a PSI change, call {@link PsiManagerEx#beforeChange} and
   * {@link PsiManagerEx#afterChange}.
   */
  @Topic.ProjectLevel
  Topic<AnyPsiChangeListenerBackgroundable> TOPIC = new ReadOnlyTopic<>(PsiManagerImpl.PUBLISHING_ANY_PSI_CHANGE_BACKGROUNDABLE_TOPIC);
}

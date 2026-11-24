// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

/**
 * Tells a subscriber of {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} that some PSI changes. The event has no data, so a subscriber
 * usually drops a cache.
 * <p>
 * A subscriber gets a physical change on the EDT, inside the write action. A non-physical change runs in place, on any thread.
 * So a write action on a background thread must move to the EDT for each physical change.
 *
 * @deprecated Implement {@link AnyPsiChangeListenerBackgroundable} and subscribe to {@link AnyPsiChangeListenerBackgroundable#TOPIC}.
 * Its subscriber runs in place, on the thread that changes the PSI, and the write action does not move to the EDT for it.
 * A body that only drops a cache is usually safe on any thread. If a part of the body needs the EDT, run that part with
 * {@code invokeLater}.
 */
@Deprecated
public interface AnyPsiChangeListener {
  default void beforePsiChanged(boolean isPhysical) {
  }

  default void afterPsiChanged(boolean isPhysical) {
  }
}

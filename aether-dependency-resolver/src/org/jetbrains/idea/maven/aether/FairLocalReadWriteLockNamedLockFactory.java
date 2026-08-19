// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.support.ReadWriteLockNamedLock;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApiStatus.Internal
public final class FairLocalReadWriteLockNamedLockFactory extends LocalReadWriteLockNamedLockFactory {
  @Override
  protected ReadWriteLockNamedLock createLock(String name) {
    return new ReadWriteLockNamedLock(name, this, new ReentrantReadWriteLock(true));
  }
}

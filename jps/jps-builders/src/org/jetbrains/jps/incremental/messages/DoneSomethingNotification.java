// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 */
public final class DoneSomethingNotification extends BuildMessage {
  public static final DoneSomethingNotification INSTANCE = new DoneSomethingNotification();

  private DoneSomethingNotification() {
    super("", Kind.INFO);
  }
}

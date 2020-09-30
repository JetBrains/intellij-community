// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.messages;

/**
 * @author Eugene Zhuravlev
 */
public final class DoneSomethingNotification extends BuildMessage {
  public static DoneSomethingNotification INSTANCE = new DoneSomethingNotification();

  private DoneSomethingNotification() {
    super("", Kind.INFO);
  }
}

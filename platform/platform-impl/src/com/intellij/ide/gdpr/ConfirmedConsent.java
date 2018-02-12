/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringTokenizer;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Dec-17
 */
public final class ConfirmedConsent extends ConsentBase {
  private boolean myIsAccepted;
  private long myAcceptanceTime;

  public ConfirmedConsent(ConsentAttributes attributes) {
    this(attributes.consentId, Version.fromString(attributes.version), attributes.accepted, attributes.acceptanceTime);
  }

  public ConfirmedConsent(String id, Version version, boolean accepted, long acceptanceTime) {
    super(id, version);
    myIsAccepted = accepted;
    myAcceptanceTime = acceptanceTime;
  }

  public boolean isAccepted() {
    return myIsAccepted;
  }

  public long getAcceptanceTime() {
    return myAcceptanceTime;
  }

  public void setAccepted(boolean accepted) {
    myIsAccepted = accepted;
  }

  public void setAcceptanceTime(long acceptanceTime) {
    myAcceptanceTime = acceptanceTime;
  }

  public String toString() {
    return "AcceptedConsent{" +
      "id='" + getId() + '\'' +
      ", version='" + getVersion() + '\'' +
      ", accepted=" + myIsAccepted +
      ", acceptanceTime=" + myAcceptanceTime +
      '}';
  }

  public String toExternalString() {
    return getId() + ":" + getVersion().toString() + ":" + (isAccepted() ? "1" : "0") + ":" + Long.toString(myAcceptanceTime);
  }

  @Nullable
  public static ConfirmedConsent fromString(@NotNull String str) {
    final StringTokenizer tokenizer = new StringTokenizer(str, ":", false);
    if (tokenizer.hasMoreTokens()) {
      final String id = tokenizer.nextToken().trim();
      if (tokenizer.hasMoreTokens()) {
        final Version ver = Version.fromString(tokenizer.nextToken());
        if (tokenizer.hasMoreTokens()) {
          try {
            final int accepted = Integer.parseInt(tokenizer.nextToken());
            final Boolean _accepted = accepted == 1 ? Boolean.TRUE : accepted == 0 ? Boolean.FALSE : null;
            if (_accepted != null && tokenizer.hasMoreTokens()) {
              return new ConfirmedConsent(id, ver, _accepted, Long.parseLong(tokenizer.nextToken()));
            }
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
    }
    return null;
  }
}

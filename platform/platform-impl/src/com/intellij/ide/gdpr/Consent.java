// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 */
public final class Consent extends ConsentBase {
  private final @NlsSafe String myName;
  private final String myText;
  private final boolean myAccepted;
  private final boolean myDeleted;
  private final String myLocale;

  public Consent(ConsentAttributes attributes) {
    this(attributes.consentId, Version.fromString(attributes.version), attributes.printableName, attributes.text, attributes.accepted, attributes.deleted, attributes.locale);
  }

  public Consent(String id, Version version, @NlsSafe String name, String text, boolean isAccepted, boolean deleted, String locale) {
    super(id, version);
    myName = name;
    myText = text;
    myAccepted = isAccepted;
    myDeleted = deleted;
    myLocale = locale;
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public @NlsSafe String getText() {
    return myText;
  }

  @Override
  public boolean isAccepted() {
    return myAccepted;
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public String getLocale() {
    return myLocale;
  }

  public ConsentAttributes toConsentAttributes() {
    final ConsentAttributes attributes = new ConsentAttributes();
    attributes.consentId = getId();
    attributes.version = getVersion().toString();
    attributes.printableName = getName();
    attributes.text = getText();
    attributes.accepted = isAccepted();
    attributes.deleted = isDeleted();
    attributes.locale = getLocale();
    return attributes;
  }

  @Override
  public @NonNls String toString() {
    return "Consent{" +
      "id='" + getId() + '\'' +
      ", version='" + getVersion() + '\'' +
      ", name='" + myName + '\'' +
      ", accepted=" + myAccepted +
      ", deleted=" + myDeleted +
      ", locale=" + myLocale +
      '}';
  }

  public Consent derive(boolean accepted) {
    return accepted == isAccepted() ? this : new Consent(getId(), getVersion(), getName(), getText(), accepted, isDeleted(), getLocale());
  }
}

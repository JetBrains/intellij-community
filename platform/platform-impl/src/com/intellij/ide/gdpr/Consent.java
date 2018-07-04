/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.gdpr;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Dec-17
 */
public final class Consent extends ConsentBase {
  private final String myName;
  private final String myText;
  private final boolean myAccepted;
  private final boolean myDeleted;

  public Consent(ConsentAttributes attributes) {
    this(attributes.consentId, Version.fromString(attributes.version), attributes.printableName, attributes.text, attributes.accepted, attributes.deleted);
  }

  public Consent(String id, Version version, String name, String text, boolean isAccepted, boolean deleted) {
    super(id, version);
    myName = name;
    myText = text;
    myAccepted = isAccepted;
    myDeleted = deleted;
  }
  
  public String getName() {
    return myName;
  }

  public String getText() {
    return myText;
  }

  public boolean isAccepted() {
    return myAccepted;
  }

  public boolean isDeleted() {
    return myDeleted;
  }

  public ConsentAttributes toConsentAttributes() {
    final ConsentAttributes attributes = new ConsentAttributes();
    attributes.consentId = getId();
    attributes.version = getVersion().toString();
    attributes.printableName = getName();
    attributes.text = getText();
    attributes.accepted = isAccepted();
    attributes.deleted = isDeleted();
    return attributes;
  }

  public String toString() {
    return "Consent{" +
      "id='" + getId() + '\'' +
      ", version='" + getVersion() + '\'' +
      ", name='" + myName + '\'' +
      ", accepted=" + myAccepted +
      ", deleted=" + myDeleted +
      '}';
  }

  public Consent derive(boolean accepted) {
    return accepted == isAccepted()? this : new Consent(getId(), getVersion(), getName(), getText(), accepted, isDeleted());
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.messages;

/**
 * @author nik
 */
public class CustomBuilderMessage extends BuildMessage {
  private final String myBuilderId;
  private final String myMessageType;
  private final String myMessageText;

  public CustomBuilderMessage(String builderId, String messageType, String messageText) {
    super("", Kind.INFO);
    myBuilderId = builderId;
    myMessageType = messageType;
    myMessageText = messageText;
  }

  public String getBuilderId() {
    return myBuilderId;
  }

  public String getMessageType() {
    return myMessageType;
  }

  @Override
  public String getMessageText() {
    return myMessageText;
  }
}

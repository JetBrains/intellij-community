// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import java.io.Serializable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class Message implements Serializable {
  private String myUid;

  public Message() { }

  public Message(String uid) {
    myUid = uid;
  }

  public String getUid() {
    return myUid;
  }
}
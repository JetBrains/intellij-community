// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jshell.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class Request extends Message {
  private Command myCommand;
  private String myCodeText;
  private List<String> myClassPath;

  public enum Command {
    EVAL, DROP_STATE, EXIT
  }

  @SuppressWarnings("unused")
  public Request() { }

  public Request(String uid, Command cmd, String codeText) {
    super(uid);
    myCommand = cmd;
    myCodeText = codeText;
  }

  public Command getCommand() {
    return myCommand;
  }

  public String getCodeText() {
    return myCodeText;
  }

  public List<String> getClassPath() {
    return myClassPath;
  }

  public void addClasspathItem(String path) {
    List<String> cp = myClassPath;
    if (cp == null) {
      cp = new ArrayList<>();
      myClassPath = cp;
    }
    cp.add(path);
  }
}
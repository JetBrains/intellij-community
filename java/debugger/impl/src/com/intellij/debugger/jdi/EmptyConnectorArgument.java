// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.sun.jdi.connect.Connector;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class EmptyConnectorArgument implements Connector.Argument {
  private final String myName;

  public EmptyConnectorArgument(@NotNull String name) {
    myName = name;
  }

  @Override
  public String name() {
    return myName;
  }

  @Override
  public String label() {
    return "";
  }

  @Override
  public String description() {
    return "";
  }

  @Override
  public String value() {
    return "";
  }

  @Override
  public void setValue(String s) {
  }

  @Override
  public boolean isValid(String s) {
    return true;
  }

  @Override
  public boolean mustSpecify() {
    return false;
  }
}

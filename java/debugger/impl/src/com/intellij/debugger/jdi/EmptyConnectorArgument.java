// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.jdi;

import com.sun.jdi.connect.Connector;

/**
 * @author egor
 */
public class EmptyConnectorArgument implements Connector.Argument {
  @Override
  public String name() {
    return null;
  }

  @Override
  public String label() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public String value() {
    return null;
  }

  @Override
  public void setValue(String s) {

  }

  @Override
  public boolean isValid(String s) {
    return false;
  }

  @Override
  public boolean mustSpecify() {
    return false;
  }
}

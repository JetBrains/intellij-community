/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

public class NamedScope {
  private String myName;
  private PackageSet myValue;

  public NamedScope(String name, PackageSet value) {
    myName = name;
    myValue = value;
  }

  public String getName() {
    return myName;
  }

  public PackageSet getValue() {
    return myValue;
  }

  public NamedScope createCopy() {
    return new NamedScope(myName, myValue.createCopy());
  }
}
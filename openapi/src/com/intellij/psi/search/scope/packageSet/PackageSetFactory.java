/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.peer.PeerFactory;

public abstract class PackageSetFactory {
  public abstract PackageSet compile(String text) throws ParsingException;

  public static PackageSetFactory getInstance() {
    return PeerFactory.getInstance().getPackageSetFactory();
  }
}
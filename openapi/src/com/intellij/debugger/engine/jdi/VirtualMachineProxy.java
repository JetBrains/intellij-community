/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine.jdi;

import com.sun.jdi.ReferenceType;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 8:49:42 PM
 * To change this template use File | Settings | File Templates.
 */
public interface VirtualMachineProxy {
  public List<ReferenceType> allClasses();

  public boolean versionHigher(String version);

  boolean canWatchFieldModification();

  boolean canWatchFieldAccess();

  boolean canInvokeMethods();
}

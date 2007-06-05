package com.intellij.debugger.engine;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundPositionManager implements PositionManager{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.CompoundPositionManager");
  private final ArrayList<PositionManager> myPositionManagers = new ArrayList<PositionManager>();

  public CompoundPositionManager() {
  }

  public CompoundPositionManager(PositionManager manager) {
    appendPositionManager(manager);
  }

  public void appendPositionManager(PositionManager manager) {
    myPositionManagers.remove(manager);
    myPositionManagers.add(0, manager);
  }

  public SourcePosition getSourcePosition(Location location) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.getSourcePosition(location);
      }
      catch (NoDataException ignored) {
      }
    }
    return null;
  }

  @NotNull
  public List<ReferenceType> getAllClasses(SourcePosition classPosition) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.getAllClasses(classPosition);
      }
      catch (NoDataException ignored) {
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<Location> locationsOfLine(ReferenceType type, SourcePosition position) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.locationsOfLine(type, position);
      }
      catch (NoDataException ignored) {
      }
    }
    return Collections.emptyList();
  }

  public ClassPrepareRequest createPrepareRequest(ClassPrepareRequestor requestor, SourcePosition position) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.createPrepareRequest(requestor, position);
      }
      catch (NoDataException ignored) {
      }
    }

    return null;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundPositionManager extends PositionManagerEx {
  private final ArrayList<PositionManager> myPositionManagers = new ArrayList<PositionManager>();

  @SuppressWarnings("UnusedDeclaration")
  public CompoundPositionManager() {
  }

  public CompoundPositionManager(PositionManager manager) {
    appendPositionManager(manager);
  }

  public void appendPositionManager(PositionManager manager) {
    myPositionManagers.remove(manager);
    myPositionManagers.add(0, manager);
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
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

  @Nullable
  @Override
  public XStackFrame createStackFrame(@NotNull Location location) {
    for (PositionManager positionManager : myPositionManagers) {
      if (positionManager instanceof PositionManagerEx) {
        XStackFrame xStackFrame = ((PositionManagerEx)positionManager).createStackFrame(location);
        if (xStackFrame != null) {
          return xStackFrame;
        }
      }
    }
    return null;
  }
}

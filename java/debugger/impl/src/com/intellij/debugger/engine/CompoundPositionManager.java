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
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
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
  private static final Logger LOG = Logger.getInstance(CompoundPositionManager.class);

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
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @Override
  @NotNull
  public List<ReferenceType> getAllClasses(@NotNull SourcePosition classPosition) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.getAllClasses(classPosition);
      }
      catch (NoDataException ignored) {
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public List<Location> locationsOfLine(@NotNull ReferenceType type, @NotNull SourcePosition position) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.locationsOfLine(type, position);
      }
      catch (NoDataException ignored) {
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull ClassPrepareRequestor requestor, @NotNull SourcePosition position) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return positionManager.createPrepareRequest(requestor, position);
      }
      catch (NoDataException ignored) {
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    return null;
  }

  @Nullable
  @Override
  public XStackFrame createStackFrame(@NotNull StackFrameProxyImpl frame, @NotNull DebugProcessImpl debugProcess, @NotNull Location location) {
    for (PositionManager positionManager : myPositionManagers) {
      if (positionManager instanceof PositionManagerEx) {
        try {
          XStackFrame xStackFrame = ((PositionManagerEx)positionManager).createStackFrame(frame, debugProcess, location);
          if (xStackFrame != null) {
            return xStackFrame;
          }
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    return null;
  }

  @Override
  public ThreeState evaluateCondition(@NotNull EvaluationContext context,
                                      @NotNull StackFrameProxyImpl frame,
                                      @NotNull Location location,
                                      @NotNull String expression) {
    for (PositionManager positionManager : myPositionManagers) {
      if (positionManager instanceof PositionManagerEx) {
        try {
          ThreeState result = ((PositionManagerEx)positionManager).evaluateCondition(context, frame, location, expression);
          if (result != ThreeState.UNSURE) {
            return result;
          }
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    return ThreeState.UNSURE;
  }
}

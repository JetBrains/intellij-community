/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.MultiRequestPositionManager;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.InternalException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundPositionManager extends PositionManagerEx implements MultiRequestPositionManager{
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

  private Cache<Location, SourcePosition> mySourcePositionCache = new Cache<Location, SourcePosition>();

  private interface Processor<T> {
    T process(PositionManager positionManager) throws NoDataException;
  }

  private <T> T iterate(Processor<T> processor, T defaultValue) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return processor.process(positionManager);
      }
      catch (NoDataException ignored) {}
      catch (VMDisconnectedException e) {throw e;}
      catch (InternalException e) {LOG.info(e);}
      catch (ProcessCanceledException ignored) {}
      catch (Exception e) {LOG.error(e);}
      catch (AssertionError e) {LOG.error(e);}
    }
    return defaultValue;
  }

  @Nullable
  @Override
  public SourcePosition getSourcePosition(final Location location) {
    if (location == null) return null;
    SourcePosition res = mySourcePositionCache.get(location);
    if (res != null) return res;

    return iterate(new Processor<SourcePosition>() {
      @Override
      public SourcePosition process(PositionManager positionManager) throws NoDataException {
        SourcePosition res = positionManager.getSourcePosition(location);
        mySourcePositionCache.put(location, res);
        return res;
      }
    }, null);
  }

  @Override
  @NotNull
  public List<ReferenceType> getAllClasses(@NotNull final SourcePosition classPosition) {
    return iterate(new Processor<List<ReferenceType>>() {
      @Override
      public List<ReferenceType> process(PositionManager positionManager) throws NoDataException {
        return positionManager.getAllClasses(classPosition);
      }
    }, Collections.<ReferenceType>emptyList());
  }

  @Override
  @NotNull
  public List<Location> locationsOfLine(@NotNull final ReferenceType type, @NotNull SourcePosition position) {
    VirtualFile file = position.getFile().getVirtualFile();
    if (file != null) {
      LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
      if (mapping != null) {
        int line = mapping.sourceToBytecode(position.getLine() + 1);
        if (line > -1) {
          position = SourcePosition.createFromLine(position.getFile(), line - 1);
        }
      }
    }

    final SourcePosition finalPosition = position;
    return iterate(new Processor<List<Location>>() {
      @Override
      public List<Location> process(PositionManager positionManager) throws NoDataException {
        return positionManager.locationsOfLine(type, finalPosition);
      }
    }, Collections.<Location>emptyList());
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) {
    return iterate(new Processor<ClassPrepareRequest>() {
      @Override
      public ClassPrepareRequest process(PositionManager positionManager) throws NoDataException {
        return positionManager.createPrepareRequest(requestor, position);
      }
    }, null);
  }

  @NotNull
  @Override
  public List<ClassPrepareRequest> createPrepareRequests(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) {
    return iterate(new Processor<List<ClassPrepareRequest>>() {
      @Override
      public List<ClassPrepareRequest> process(PositionManager positionManager) throws NoDataException {
        if (positionManager instanceof MultiRequestPositionManager) {
          return ((MultiRequestPositionManager)positionManager).createPrepareRequests(requestor, position);
        }
        else {
          ClassPrepareRequest prepareRequest = positionManager.createPrepareRequest(requestor, position);
          if (prepareRequest == null) {
            return Collections.emptyList();
          }
          return Collections.singletonList(prepareRequest);
        }
      }
    }, Collections.<ClassPrepareRequest>emptyList());
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

  private static class Cache<K,V> {
    private K myKey;
    private V myValue;

    public V get(@NotNull K key) {
      if (key.equals(myKey)) {
        return myValue;
      }
      return null;
    }

    public void put(@NotNull K key, V value) {
      myKey = key;
      myValue = value;
    }
  }
}

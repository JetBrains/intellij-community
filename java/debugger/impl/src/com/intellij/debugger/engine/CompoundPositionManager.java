/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.*;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompoundPositionManager extends PositionManagerEx implements MultiRequestPositionManager{
  private static final Logger LOG = Logger.getInstance(CompoundPositionManager.class);

  private final ArrayList<PositionManager> myPositionManagers = new ArrayList<>();

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

  private final Cache<Location, SourcePosition> mySourcePositionCache = new Cache<>();

  private interface Processor<T> {
    T process(PositionManager positionManager) throws NoDataException;
  }

  private <T> T iterate(Processor<T> processor, T defaultValue) {
    for (PositionManager positionManager : myPositionManagers) {
      try {
        return processor.process(positionManager);
      }
      catch (NoDataException | ProcessCanceledException ignored) {}
      catch (VMDisconnectedException | ObjectCollectedException e) {throw e;}
      catch (InternalException e) {LOG.info(e);}
      catch (Exception | AssertionError e) {LOG.error(e);}
    }
    return defaultValue;
  }

  @Nullable
  @Override
  public SourcePosition getSourcePosition(final Location location) {
    if (location == null) return null;
    SourcePosition res = mySourcePositionCache.get(location);
    if (checkCacheEntry(res, location)) return res;

    return iterate(positionManager -> {
      SourcePosition res1 = positionManager.getSourcePosition(location);
      mySourcePositionCache.put(location, res1);
      return res1;
    }, null);
  }

  private static boolean checkCacheEntry(SourcePosition position, Location location) {
    if (position == null) return false;
    PsiFile psiFile = position.getFile();
    if (!psiFile.isValid()) return false;
    String url = DebuggerUtilsEx.getAlternativeSourceUrl(location.declaringType().name(), psiFile.getProject());
    if (url == null) return true;
    VirtualFile file = psiFile.getVirtualFile();
    return file != null && url.equals(file.getUrl());
  }

  @Override
  @NotNull
  public List<ReferenceType> getAllClasses(@NotNull final SourcePosition classPosition) {
    return iterate(positionManager -> positionManager.getAllClasses(classPosition), Collections.<ReferenceType>emptyList());
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
    return iterate(positionManager -> positionManager.locationsOfLine(type, finalPosition), Collections.<Location>emptyList());
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) {
    return iterate(positionManager -> positionManager.createPrepareRequest(requestor, position), null);
  }

  @NotNull
  @Override
  public List<ClassPrepareRequest> createPrepareRequests(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) {
    return iterate(positionManager -> {
      if (positionManager instanceof MultiRequestPositionManager) {
        return ((MultiRequestPositionManager)positionManager).createPrepareRequests(requestor, position);
      }
      else {
        ClassPrepareRequest prepareRequest = positionManager.createPrepareRequest(requestor, position);
        if (prepareRequest == null) {
          return Collections.<ClassPrepareRequest>emptyList();
        }
        return Collections.singletonList(prepareRequest);
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
        catch (VMDisconnectedException e) {
          throw e;
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

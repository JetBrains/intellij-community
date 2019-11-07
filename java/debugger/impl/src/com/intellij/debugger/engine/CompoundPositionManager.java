// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.MultiRequestPositionManager;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CompoundPositionManager extends PositionManagerEx implements MultiRequestPositionManager{
  private static final Logger LOG = Logger.getInstance(CompoundPositionManager.class);

  public static final CompoundPositionManager EMPTY = new CompoundPositionManager();

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
    clearCache();
  }

  public void clearCache() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    mySourcePositionCache.clear();
  }

  private final Map<Location, SourcePosition> mySourcePositionCache = new WeakHashMap<>();

  private interface Processor<T> {
    T process(PositionManager positionManager) throws NoDataException;
  }

  private <T> T iterate(Processor<? extends T> processor, T defaultValue, SourcePosition position) {
    return iterate(processor, defaultValue, position, true);
  }

  private <T> T iterate(Processor<? extends T> processor, T defaultValue, SourcePosition position, boolean ignorePCE) {
    FileType fileType = position != null ? position.getFile().getFileType() : null;
    for (PositionManager positionManager : myPositionManagers) {
      if (fileType != null) {
        Set<? extends FileType> types = positionManager.getAcceptedFileTypes();
        if (types != null && !types.contains(fileType)) {
          continue;
        }
      }
      try {
        if (!ignorePCE) {
          ProgressManager.checkCanceled();
        }
        return DebuggerUtilsImpl.suppressExceptions(() -> processor.process(positionManager), defaultValue, ignorePCE, NoDataException.class);
      }
      catch (NoDataException ignored) {
      }
    }
    return defaultValue;
  }

  @Nullable
  @Override
  public SourcePosition getSourcePosition(final Location location) {
    if (location == null) return null;
    return ReadAction.nonBlocking(() -> {
      SourcePosition res = null;
      try {
        res = mySourcePositionCache.get(location);
      }
      catch (IllegalArgumentException ignored) { // Invalid method id
      }
      if (checkCacheEntry(res, location)) return res;

      return iterate(positionManager -> {
        SourcePosition res1 = positionManager.getSourcePosition(location);
        try {
          mySourcePositionCache.put(location, res1);
        }
        catch (IllegalArgumentException ignored) { // Invalid method id
        }
        return res1;
      }, null, null, false);
    }).executeSynchronously();
  }

  private static boolean checkCacheEntry(@Nullable SourcePosition position, @NotNull Location location) {
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
    return iterate(positionManager -> positionManager.getAllClasses(classPosition), Collections.emptyList(), classPosition);
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
    return iterate(positionManager -> positionManager.locationsOfLine(type, finalPosition), Collections.emptyList(), position);
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) {
    return iterate(positionManager -> positionManager.createPrepareRequest(requestor, position), null, position);
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
          return Collections.emptyList();
        }
        return Collections.singletonList(prepareRequest);
      }
    }, Collections.emptyList(), position);
  }

  @Nullable
  @Override
  public XStackFrame createStackFrame(@NotNull StackFrameDescriptorImpl descriptor) {
    for (PositionManager positionManager : myPositionManagers) {
      if (positionManager instanceof PositionManagerEx) {
        try {
          XStackFrame xStackFrame = ((PositionManagerEx)positionManager).createStackFrame(descriptor);
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
}

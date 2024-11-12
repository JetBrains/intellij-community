// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.MultiRequestPositionManager;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

public class CompoundPositionManager implements PositionManagerWithConditionEvaluation, MultiRequestPositionManager, PositionManagerAsync {
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

  void removePositionManager(PositionManager manager) {
    myPositionManagers.remove(manager);
    clearCache();
  }

  public void clearCache() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    mySourcePositionCache.clear();
  }

  private final Map<Location, SourcePosition> mySourcePositionCache = new WeakHashMap<>();

  private interface Producer<T> {
    T produce(PositionManager positionManager) throws NoDataException;
  }

  private <T> T iterate(Producer<? extends T> processor, T defaultValue, @Nullable SourcePosition position) {
    FileType fileType = position != null ? position.getFile().getFileType() : null;
    return iterate(processor, defaultValue, fileType, true);
  }

  private static boolean acceptsFileType(@NotNull PositionManager positionManager, @Nullable FileType fileType) {
    if (fileType != null && fileType != UnknownFileType.INSTANCE) {
      Set<? extends FileType> types = positionManager.getAcceptedFileTypes();
      if (types != null && !types.contains(fileType)) {
        return false;
      }
    }
    return true;
  }

  private <T> T iterate(Producer<? extends T> processor, T defaultValue, @Nullable FileType fileType, boolean ignorePCE) {
    for (PositionManager positionManager : myPositionManagers) {
      if (acceptsFileType(positionManager, fileType)) {
        try {
          if (!ignorePCE) {
            ProgressManager.checkCanceled();
          }
          return DebuggerUtilsImpl.suppressExceptions(() -> processor.produce(positionManager), defaultValue, ignorePCE,
                                                      NoDataException.class);
        }
        catch (NoDataException ignored) {
        }
      }
    }
    return defaultValue;
  }

  private <T> CompletableFuture<T> iterateAsync(Function<PositionManager, CompletableFuture<T>> processor,
                                                @Nullable FileType fileType,
                                                boolean ignorePCE) {
    CompletableFuture<T> res = failedFuture(NoDataException.INSTANCE);
    for (PositionManager positionManager : myPositionManagers) {
      if (acceptsFileType(positionManager, fileType)) {
        res = res.exceptionallyCompose(e -> {
          Throwable unwrap = DebuggerUtilsAsync.unwrap(e);
          if (unwrap instanceof NoDataException) {
            if (!ignorePCE) {
              ProgressManager.checkCanceled();
            }
            return processor.apply(positionManager);
          }
          return failedFuture(unwrap);
        });
      }
    }
    return res;
  }

  private static CompletableFuture<SourcePosition> getSourcePositionAsync(PositionManager positionManager, Location location) {
    if (positionManager instanceof PositionManagerAsync positionManagerAsync) {
      return positionManagerAsync.getSourcePositionAsync(location);
    }
    else {
      try {
        return completedFuture(ReadAction.nonBlocking(() -> positionManager.getSourcePosition(location)).executeSynchronously());
      }
      catch (Exception e) {
        return failedFuture(DebuggerUtilsAsync.unwrap(e));
      }
    }
  }

  @Override
  public @NotNull CompletableFuture<SourcePosition> getSourcePositionAsync(final Location location) {
    return getCachedSourcePosition(location, (fileType) -> iterateAsync(positionManager -> getSourcePositionAsync(positionManager, location), fileType, false));
  }

  private CompletableFuture<SourcePosition> getCachedSourcePosition(Location location, Function<FileType, CompletableFuture<SourcePosition>> producer) {
    if (location == null) return completedFuture(null);
    SourcePosition res = null;
    try {
      res = mySourcePositionCache.get(location);
    }
    catch (IllegalArgumentException ignored) { // Invalid method id
    }
    if (checkCacheEntry(res, location)) return completedFuture(res);

    FileType fileType = ReadAction.compute(() -> {
      String sourceName = DebuggerUtilsEx.getSourceName(location, (String)null);
      return sourceName != null ? FileTypeManager.getInstance().getFileTypeByFileName(sourceName) : null;
    });
    return producer.apply(fileType)
      .thenApply(p -> {
        try {
          mySourcePositionCache.put(location, p);
        }
        catch (IllegalArgumentException ignored) { // Invalid method id
        }
        return p;
      });
  }

  @Nullable
  @Override
  public SourcePosition getSourcePosition(final Location location) {
    return getCachedSourcePosition(location, fileType -> {
      return completedFuture(ReadAction.nonBlocking(() -> {
        return iterate(positionManager -> positionManager.getSourcePosition(location), null, fileType, false);
      }).executeSynchronously());
    }).getNow(null);
  }

  private static boolean checkCacheEntry(@Nullable SourcePosition position, @NotNull Location location) {
    return ReadAction.compute(() -> {
      if (position == null) return false;
      PsiFile psiFile = position.getFile();
      if (!psiFile.isValid()) return false;
      String url = DebuggerUtilsEx.getAlternativeSourceUrl(location.declaringType().name(), psiFile.getProject());
      if (url == null) return true;
      VirtualFile file = psiFile.getVirtualFile();
      return file != null && url.equals(file.getUrl());
    });
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
  public List<XStackFrame> createStackFrames(@NotNull StackFrameDescriptorImpl descriptor) {
    return iterate(positionManager -> {
      if (positionManager instanceof PositionManagerWithMultipleStackFrames positionManagerWithMultipleStackFrames) {
        List<XStackFrame> stackFrames = positionManagerWithMultipleStackFrames.createStackFrames(descriptor);
        if (stackFrames != null) {
          return stackFrames;
        }
      }
      else if (positionManager instanceof PositionManagerEx positionManagerEx) {
        XStackFrame xStackFrame = positionManagerEx.createStackFrame(descriptor);
        if (xStackFrame != null) {
          return Collections.singletonList(xStackFrame);
        }
      }
      throw NoDataException.INSTANCE;
    }, null, null, false);
  }

  @Override
  public ThreeState evaluateCondition(@NotNull EvaluationContext context,
                                      @NotNull StackFrameProxyImpl frame,
                                      @NotNull Location location,
                                      @NotNull String expression) {
    for (PositionManager positionManager : myPositionManagers) {
      if (positionManager instanceof PositionManagerWithConditionEvaluation) {
        try {
          PositionManagerWithConditionEvaluation manager = (PositionManagerWithConditionEvaluation)positionManager;
          ThreeState result = manager.evaluateCondition(context, frame, location, expression);
          if (result != ThreeState.UNSURE) {
            return result;
          }
        }
        catch (Throwable e) {
          DebuggerUtilsImpl.logError(e);
        }
      }
    }
    return ThreeState.UNSURE;
  }
}

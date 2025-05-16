// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.SimpleStackFrameContext;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.breakpoints.BreakpointIntentionAction;
import com.intellij.debugger.ui.tree.StackFrameDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Nodes of this type cannot be updated, because StackFrame objects become invalid as soon as VM has been resumed
 */
public class StackFrameDescriptorImpl extends NodeDescriptorImpl implements StackFrameDescriptor {
  private final StackFrameProxyImpl myFrame;
  private int myUiIndex;
  private String myName = null;
  private Location myLocation;
  private MethodsTracker.MethodOccurrence myMethodOccurrence;
  private boolean myIsSynthetic;
  private boolean myIsInLibraryContent;
  private boolean myIsFiltered;
  private ObjectReference myThisObject;
  private SourcePosition mySourcePosition;

  private Icon myIcon = EmptyIcon.ICON_16;

  public StackFrameDescriptorImpl(@NotNull StackFrameProxyImpl frame,
                                  @NotNull MethodsTracker tracker) {
    this(frame, false, null, tracker,
         ContextUtil.getSourcePosition(new SimpleStackFrameContext(frame, frame.getVirtualMachine().getDebugProcess())));
  }

  private StackFrameDescriptorImpl(@NotNull StackFrameProxyImpl frame,
                                   boolean useMethod,
                                   @Nullable Method method,
                                   @NotNull MethodsTracker tracker,
                                   SourcePosition sourcePosition) {
    myFrame = frame;

    try {
      myUiIndex = frame.getFrameIndex();
      myLocation = frame.location();
      if (!getValueMarkers().isEmpty()) {
        getThisObject(); // init this object for markup
      }
      myMethodOccurrence = tracker.getMethodOccurrence(myUiIndex,
                                                       useMethod ? method : DebuggerUtilsEx.getMethod(myLocation));
      myIsSynthetic = DebuggerUtils.isSynthetic(myMethodOccurrence.getMethod());
      mySourcePosition = sourcePosition;
      PsiFile psiFile = mySourcePosition != null ? mySourcePosition.getFile() : null;
      myIsInLibraryContent =
        DebuggerUtilsEx.isInLibraryContent(psiFile != null ? psiFile.getVirtualFile() : null, getDebugProcess().getProject());
      myIsFiltered = DebugProcessImpl.isPositionFiltered(myLocation);
    }
    catch (InternalException | EvaluateException e) {
      LOG.info(e);
      myLocation = null;
      myMethodOccurrence = tracker.getMethodOccurrence(0, null);
      myIsSynthetic = false;
      myIsInLibraryContent = false;
      myIsFiltered = false;
    }
  }

  private static CompletableFuture<SourcePosition> getSourcePositionAsync(@NotNull Location location, @NotNull StackFrameProxyImpl frame) {
    try {
      CompoundPositionManager positionManager = frame.getVirtualMachine().getDebugProcess().getPositionManager();
      return positionManager.getSourcePositionFuture(location);
    }
    catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  public static CompletableFuture<StackFrameDescriptorImpl> createAsync(@NotNull StackFrameProxyImpl frame,
                                                                        @NotNull MethodsTracker tracker) {
    CompletableFuture<Location> locationAsync = frame.locationAsync();
    CompletableFuture<SourcePosition> positionAsync =
      locationAsync.thenCompose(location -> getSourcePositionAsync(location, frame));
    return locationAsync
      .thenCompose(DebuggerUtilsAsync::method)
      .thenCombine(positionAsync, (method, position) -> {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        return new StackFrameDescriptorImpl(frame, true, method, tracker, position);
      })
      .exceptionally(throwable -> {
        Throwable exception = DebuggerUtilsAsync.unwrap(throwable);
        if (exception instanceof EvaluateException) {
          // TODO: simplify when only async method left
          if (!(exception.getCause() instanceof InvalidStackFrameException)) {
            LOG.error(new Exception(exception));
          }
          DebuggerManagerThreadImpl.assertIsManagerThread();
          return new StackFrameDescriptorImpl(frame, tracker); // fallback to sync
        }
        throw (RuntimeException)throwable;
      });
  }

  public int getUiIndex() {
    return myUiIndex;
  }

  @Override
  public @NotNull StackFrameProxyImpl getFrameProxy() {
    return myFrame;
  }

  @Override
  public @NotNull DebugProcess getDebugProcess() {
    return myFrame.getVirtualMachine().getDebugProcess();
  }

  public @Nullable Method getMethod() {
    return myMethodOccurrence.getMethod();
  }

  public int getOccurrenceIndex() {
    return myMethodOccurrence.getIndex();
  }

  public boolean isRecursiveCall() {
    return myMethodOccurrence.isRecursive();
  }

  public ThreeState canDrop() {
    if (myFrame.isBottom()) return ThreeState.NO;
    return myMethodOccurrence.canDrop();
  }

  public @Nullable ValueMarkup getValueMarkup() {
    Map<?, ValueMarkup> markers = getValueMarkers();
    if (!markers.isEmpty() && myThisObject != null) {
      return markers.get(myThisObject);
    }
    return null;
  }

  private Map<?, ValueMarkup> getValueMarkers() {
    XValueMarkers<?, ?> markers = DebuggerUtilsImpl.getValueMarkers(getDebugProcess());
    return markers != null ? markers.getAllMarkers() : Collections.emptyMap();
  }

  @Override
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    calcIconLater(descriptorLabelListener);

    if (myLocation == null) {
      return "";
    }
    ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
    @NlsSafe StringBuilder label = new StringBuilder();
    Method method = myMethodOccurrence.getMethod();
    if (method != null) {
      if (myName == null) {
        myName = method.name();
      }
      label.append(settings.SHOW_ARGUMENTS_TYPES ? DebuggerUtilsEx.methodNameWithArguments(method) : myName);
    }
    if (settings.SHOW_LINE_NUMBER) {
      label.append(':').append(DebuggerUtilsEx.getLineNumber(myLocation, false));
      if (Registry.is("debugger.stack.frame.show.code.index")) {
        label.append(':').append(DebuggerUtilsEx.getCodeIndex(myLocation));
      }
    }
    if (settings.SHOW_CLASS_NAME) {
      String name;
      try {
        ReferenceType refType = myLocation.declaringType();
        name = refType != null ? refType.name() : null;
      }
      catch (InternalError e) {
        name = e.toString();
      }
      if (name != null) {
        label.append(", ");
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
          label.append(name);
        }
        else {
          label.append(name.substring(dotIndex + 1));
          if (settings.SHOW_PACKAGE_NAME) {
            label.append(" {");
            label.append(name, 0, dotIndex);
            label.append("}");
          }
        }
      }
    }
    if (settings.SHOW_SOURCE_NAME) {
      label.append(", ").append(DebuggerUtilsEx.getSourceName(myLocation, "Unknown Source"));
    }
    return label.toString();
  }

  @Override
  public boolean isExpandable() {
    return true;
  }

  @Override
  public final void setContext(EvaluationContextImpl context) {
  }

  public boolean isSynthetic() {
    return myIsSynthetic;
  }

  public boolean isInLibraryContent() {
    return myIsInLibraryContent;
  }

  public boolean shouldHide() {
    return isSynthetic() || isInLibraryContent() ||
           (DebugProcessImpl.shouldHideStackFramesUsingSteppingFilters() && myIsFiltered);
  }

  public @Nullable Location getLocation() {
    return myLocation;
  }

  public SourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  private void calcIconLater(DescriptorLabelListener descriptorLabelListener) {
    try {
      myFrame.isObsolete()
        .thenAccept(res -> {
          if (res) {
            myIcon = AllIcons.Debugger.Db_obsolete;
            descriptorLabelListener.labelChanged();
          }
        })
        .exceptionally(throwable -> DebuggerUtilsAsync.logError(throwable));
    }
    catch (EvaluateException ignored) {
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public @Nullable ObjectReference getThisObject() {
    if (myThisObject == null) {
      try {
        myThisObject = myFrame.thisObject();
      }
      catch (EvaluateException e) {
        LOG.info(e);
      }
      if (myThisObject != null) {
        putUserData(BreakpointIntentionAction.THIS_ID_KEY, myThisObject.uniqueID());
        putUserData(BreakpointIntentionAction.THIS_TYPE_KEY, myThisObject.type().name());
      }
    }
    return myThisObject;
  }
}

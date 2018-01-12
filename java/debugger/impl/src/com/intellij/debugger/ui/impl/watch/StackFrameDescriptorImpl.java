/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.tree.StackFrameDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Nodes of this type cannot be updated, because StackFrame objects become invalid as soon as VM has been resumed
 */
public class StackFrameDescriptorImpl extends NodeDescriptorImpl implements StackFrameDescriptor{
  private final StackFrameProxyImpl myFrame;
  private int myUiIndex;
  private String myName = null;
  private Location myLocation;
  private MethodsTracker.MethodOccurrence myMethodOccurrence;
  private boolean myIsSynthetic;
  private boolean myIsInLibraryContent;
  private ObjectReference myThisObject;
  private SourcePosition mySourcePosition;

  private Icon myIcon = AllIcons.Debugger.StackFrame;

  public StackFrameDescriptorImpl(@NotNull StackFrameProxyImpl frame, @NotNull MethodsTracker tracker) {
    myFrame = frame;

    try {
      myUiIndex = frame.getFrameIndex();
      myLocation = frame.location();
      try {
        myThisObject = frame.thisObject();
      } catch (EvaluateException e) {
        // catch internal exceptions here
        if (!(e.getCause() instanceof InternalException)) {
          throw e;
        }
        LOG.info(e);
      }
      myMethodOccurrence = tracker.getMethodOccurrence(myUiIndex, DebuggerUtilsEx.getMethod(myLocation));
      myIsSynthetic = DebuggerUtils.isSynthetic(myMethodOccurrence.getMethod());
      mySourcePosition = ContextUtil.getSourcePosition(this);
      PsiFile psiFile = mySourcePosition != null ? mySourcePosition.getFile() : null;
      myIsInLibraryContent = DebuggerUtilsEx.isInLibraryContent(psiFile != null ? psiFile.getVirtualFile() : null, getDebugProcess().getProject());
    }
    catch (InternalException | EvaluateException e) {
      LOG.info(e);
      myLocation = null;
      myMethodOccurrence = tracker.getMethodOccurrence(0, null);
      myIsSynthetic = false;
      myIsInLibraryContent = false;
    }
  }

  public int getUiIndex() {
    return myUiIndex;
  }

  @Override
  @NotNull
  public StackFrameProxyImpl getFrameProxy() {
    return myFrame;
  }

  @NotNull
  @Override
  public DebugProcess getDebugProcess() {
    return myFrame.getVirtualMachine().getDebugProcess();
  }

  @Nullable
  public Method getMethod() {
    return myMethodOccurrence.getMethod();
  }

  public int getOccurrenceIndex() {
    return myMethodOccurrence.getIndex();
  }

  public boolean isRecursiveCall() {
    return myMethodOccurrence.isRecursive();
  }

  @Nullable
  public ValueMarkup getValueMarkup() {
    if (myThisObject != null) {
      DebugProcess process = myFrame.getVirtualMachine().getDebugProcess();
      if (process instanceof DebugProcessImpl) {
        XDebugSession session = ((DebugProcessImpl)process).getSession().getXDebugSession();
        if (session instanceof XDebugSessionImpl) {
          XValueMarkers<?, ?> markers = ((XDebugSessionImpl)session).getValueMarkers();
          if (markers != null) {
            return markers.getAllMarkers().get(myThisObject);
          }
        }
      }
    }
    return null;
  }
  
  @Override
  public String getName() {
    return myName;
  }

  @Override
  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    if (myLocation == null) {
      return "";
    }
    ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
    StringBuilder label = new StringBuilder();
    Method method = myMethodOccurrence.getMethod();
    if (method != null) {
      myName = method.name();
      label.append(settings.SHOW_ARGUMENTS_TYPES ? DebuggerUtilsEx.methodNameWithArguments(method) : myName);
    }
    if (settings.SHOW_LINE_NUMBER) {
      label.append(':').append(Integer.toString(DebuggerUtilsEx.getLineNumber(myLocation, false)));
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
            label.append(name.substring(0, dotIndex));
            label.append("}");
          }
        }
      }
    }
    if (settings.SHOW_SOURCE_NAME) {
      label.append(", ").append(DebuggerUtilsEx.getSourceName(myLocation, e -> "Unknown Source"));
    }
    return label.toString();
  }

  @Override
  public boolean isExpandable() {
    return true;
  }

  @Override
  public final void setContext(EvaluationContextImpl context) {
    myIcon = calcIcon();
  }

  public boolean isSynthetic() {
    return myIsSynthetic;
  }

  public boolean isInLibraryContent() {
    return myIsInLibraryContent;
  }

  @Nullable
  public Location getLocation() {
    return myLocation;
  }

  public SourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  private Icon calcIcon() {
    try {
      if(myFrame.isObsolete()) {
        return AllIcons.Debugger.Db_obsolete;
      }
    }
    catch (EvaluateException ignored) {
    }
    return JBUI.scale(EmptyIcon.create(6));//AllIcons.Debugger.StackFrame;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public ObjectReference getThisObject() {
    return myThisObject;
  }
}

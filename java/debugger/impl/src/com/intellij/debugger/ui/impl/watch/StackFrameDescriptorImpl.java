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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.tree.StackFrameDescriptor;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.FileColorManager;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.ui.DebuggerIcons;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

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
  private Color myBackgroundColor;

  private static final Icon myObsoleteFrameIcon = IconLoader.getIcon("/debugger/db_obsolete.png");
  private Icon myIcon = DebuggerIcons.STACK_FRAME_ICON;

  public StackFrameDescriptorImpl(StackFrameProxyImpl frame, final MethodsTracker tracker) {
    myFrame = frame;
    try {
      myUiIndex = frame.getFrameIndex();
      myLocation = frame.location();
      myThisObject = frame.thisObject();
      myMethodOccurrence = tracker.getMethodOccurrence(myLocation.method());
      myIsSynthetic = DebuggerUtils.isSynthetic(myMethodOccurrence.getMethod());
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final SourcePosition position = ContextUtil.getSourcePosition(StackFrameDescriptorImpl.this);
          final PsiFile file = position != null? position.getFile() : null;
          if (file == null) {
            myIsInLibraryContent = true;
          }
          else {
            myBackgroundColor = FileColorManager.getInstance(file.getProject()).getFileColor(file);
            
            final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getDebugProcess().getProject()).getFileIndex();
            final VirtualFile vFile = file.getVirtualFile();
            myIsInLibraryContent = vFile != null && (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile));
          }
        }
      });
    }
    catch(InternalException e) {
      LOG.info(e);
      myLocation = null;
      myMethodOccurrence = tracker.getMethodOccurrence(null);
      myIsSynthetic = false;
      myIsInLibraryContent = false;
    }
    catch (EvaluateException e) {
      myLocation = null;
      myMethodOccurrence = tracker.getMethodOccurrence(null);
      myIsSynthetic = false;
      myIsInLibraryContent = false;
    }
  }

  public int getUiIndex() {
    return myUiIndex;
  }

  public StackFrameProxyImpl getFrameProxy() {
    return myFrame;
  }

  public DebugProcess getDebugProcess() {
    return myFrame.getVirtualMachine().getDebugProcess();
  }

  @Override
  public Color getBackgroundColor() {
    return myBackgroundColor;
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
      final Map<ObjectReference, ValueMarkup> markupMap = getMarkupMap(myFrame.getVirtualMachine().getDebugProcess());
      if (markupMap != null) {
        return markupMap.get(myThisObject);
      }
    }
    return null;
  }
  
  public String getName() {
    return myName;
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (myLocation == null) {
      return "";
    }
    ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
    final StringBuilder label = StringBuilderSpinAllocator.alloc();
    try {
      Method method = myMethodOccurrence.getMethod();
      if (method != null) {
        myName = method.name();
        label.append(myName);
        label.append("()");
      }
      if (settings.SHOW_LINE_NUMBER) {
        String lineNumber = null;
        try {
          lineNumber = Integer.toString(myLocation.lineNumber());
        }
        catch (InternalError e) {
          lineNumber = e.toString();
        }
        if (lineNumber != null) {
          label.append(':');
          label.append(lineNumber);
        }
      }
      if (settings.SHOW_CLASS_NAME) {
        String name = null;
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
            label.append(" {");
            label.append(name.substring(0, dotIndex));
            label.append("}");
          }
        }
      }
      if (settings.SHOW_SOURCE_NAME) {
        try {
          String sourceName;
          try {
            sourceName = myLocation.sourceName();
          }
          catch (InternalError e) {
            sourceName = e.toString();
          }
          label.append(", ");
          label.append(sourceName);
        }
        catch (AbsentInformationException exception) {
        }
      }
      return label.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(label);
    }
  }

  public final boolean stackFramesEqual(StackFrameDescriptorImpl d) {
    return getFrameProxy().equals(d.getFrameProxy());
  }

  public boolean isExpandable() {
    return true;
  }

  public final void setContext(EvaluationContextImpl context) {
    myIcon = calcIcon();
  }

  public boolean isSynthetic() {
    return myIsSynthetic;
  }

  public boolean isInLibraryContent() {
    return myIsInLibraryContent;
  }

  public Location getLocation() {
    return myLocation;
  }

  private Icon calcIcon() {
    try {
      if(myFrame.isObsolete()) {
        return myObsoleteFrameIcon;
      }
    }
    catch (EvaluateException e) {
    }
    return DebuggerIcons.STACK_FRAME_ICON;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
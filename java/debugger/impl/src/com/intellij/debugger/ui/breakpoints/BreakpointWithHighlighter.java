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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xml.CommonXmlStrings;
import com.sun.jdi.ReferenceType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import javax.swing.*;

/**
 * User: lex
 * Date: Sep 2, 2003
 * Time: 3:22:55 PM
 */
public abstract class BreakpointWithHighlighter<P extends JavaBreakpointProperties> extends Breakpoint<P> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter");

  @Nullable
  private SourcePosition mySourcePosition;

  private boolean myVisible = true;
  private volatile Icon myIcon = getSetIcon(false);
  @Nullable
  private String myClassName;
  @Nullable
  private String myPackageName;
  @Nullable
  private String myInvalidMessage;

  protected abstract void createRequestForPreparedClass(final DebugProcessImpl debugProcess, final ReferenceType classType);

  protected abstract Icon getDisabledIcon(boolean isMuted);

  protected abstract Icon getInvalidIcon(boolean isMuted);

  protected abstract Icon getSetIcon(boolean isMuted);

  protected abstract Icon getVerifiedIcon(boolean isMuted);

  protected abstract Icon getVerifiedWarningsIcon(boolean isMuted);

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  @Override
  public String getClassName() {
    return myClassName;
  }

  @Override
  @Nullable
  public String getShortClassName() {
    final SourcePosition pos = getSourcePosition();
    if (pos != null) {
      if (pos.getFile() instanceof JspFile) {
        return getClassName();
      }
    }
    return super.getShortClassName();
  }

  @Nullable
  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Nullable
  public BreakpointWithHighlighter init() {
    if (!isValid()) {
      return null;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      updateUI();
      updateGutter();
    }

    return this;
  }

  private void updateCaches(DebugProcessImpl debugProcess) {
    myIcon = calcIcon(debugProcess);
    myClassName = JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, getSourcePosition());
    myPackageName = JVMNameUtil.getSourcePositionPackageDisplayName(debugProcess, getSourcePosition());
  }

  private Icon calcIcon(@Nullable DebugProcessImpl debugProcess) {
    final boolean muted = debugProcess != null && isMuted(debugProcess);
    if (!isEnabled()) {
      return getDisabledIcon(muted);
    }

    myInvalidMessage = "";

    if (!isValid()) {
      return getInvalidIcon(muted);
    }

    if (debugProcess == null) {
      return getSetIcon(muted);
    }

    final RequestManagerImpl requestsManager = debugProcess.getRequestsManager();

    final boolean isVerified = myCachedVerifiedState || requestsManager.isVerified(this);

    final String warning = requestsManager.getWarning(this);
    if (warning != null) {
      myInvalidMessage = warning;
      if (!isVerified) {
        return getInvalidIcon(muted);
      }
      return getVerifiedWarningsIcon(muted);
    }

    if (isVerified) {
      return getVerifiedIcon(muted);
    }

    return getSetIcon(muted);
  }

  protected BreakpointWithHighlighter(@NotNull Project project, XBreakpoint xBreakpoint) {
    //for persistency
    super(project, xBreakpoint);
    ApplicationManager.getApplication().runReadAction((Runnable)this::reload);
  }

  @Override
  public boolean isValid() {
    return isPositionValid(myXBreakpoint.getSourcePosition());
  }

  protected static boolean isPositionValid(@Nullable final XSourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return sourcePosition != null && sourcePosition.getFile().isValid();
      }
    }).booleanValue();
  }

  @Nullable
  public SourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @NotNull
  public String getDescription() {
    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(getDisplayName());

      if (isCountFilterEnabled()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.pass.count")).append(": ");
        buf.append(getCountFilter());
      }
      if (isClassFiltersEnabled()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.class.filters")).append(": ");
        ClassFilter[] classFilters = getClassFilters();
        for (ClassFilter classFilter : classFilters) {
          buf.append(classFilter.getPattern()).append(" ");
        }
      }
      if (isInstanceFiltersEnabled()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.instance.filters"));
        InstanceFilter[] instanceFilters = getInstanceFilters();
        for (InstanceFilter instanceFilter : instanceFilters) {
          buf.append(Long.toString(instanceFilter.getId())).append(" ");
        }
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  @Override
  public void reload() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    XSourcePosition position = myXBreakpoint.getSourcePosition();
    PsiFile psiFile = getPsiFile();
    if (position != null && psiFile != null) {
      mySourcePosition = SourcePosition.createFromLine(psiFile, position.getLine());
      reload(psiFile);
    }
    else {
      mySourcePosition = null;
    }
  }

  @Nullable
  public PsiFile getPsiFile() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    XSourcePosition position = myXBreakpoint.getSourcePosition();
    if (position != null) {
      VirtualFile file = position.getFile();
      if (file.isValid()) {
        return PsiManager.getInstance(myProject).findFile(file);
      }
    }
    return null;
  }

  @Override
  public void createRequest(@NotNull DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    // check is this breakpoint is enabled, vm reference is valid and there're no requests created yet
    if (!shouldCreateRequest(debugProcess)) {
      return;
    }

    if (!isValid()) {
      return;
    }

    SourcePosition position = getSourcePosition();
    if (position != null) {
      createOrWaitPrepare(debugProcess, position);
    }
    else {
      LOG.error("Unable to create request for breakpoint with null position: " + toString() + " at " + myXBreakpoint.getSourcePosition());
    }
    updateUI();
  }

  protected boolean isMuted(@NotNull final DebugProcessImpl debugProcess) {
    return debugProcess.areBreakpointsMuted();
  }

  @Override
  public void processClassPrepare(final DebugProcess debugProcess, final ReferenceType classType) {
    if (!isEnabled() || !isValid()) {
      return;
    }
    createRequestForPreparedClass((DebugProcessImpl)debugProcess, classType);
    updateUI();
  }

  /**
   * updates the state of breakpoint and all the related UI widgets etc
   */
  @Override
  public final void updateUI() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    final Project project = getProject();
    DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
      @Override
      public void run() {
        if (!isValid()) {
          return;
        }

        DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
        final DebugProcessImpl debugProcess = context.getDebugProcess();
        if (debugProcess == null || !debugProcess.isAttached()) {
          updateCaches(null);
          updateGutter();
        }
        else {
          debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  if (!project.isDisposed()) {
                    updateCaches(debugProcess);
                  }
                }
              });
              DebuggerInvocationUtil.swingInvokeLater(project, BreakpointWithHighlighter.this::updateGutter);
            }
          });
        }
      }
    });
  }

  private void updateGutter() {
    if (myVisible) {
      if (isValid()) {
        final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(myProject).getBreakpointManager();
        breakpointManager.updateBreakpointPresentation((XLineBreakpoint)myXBreakpoint, getIcon(), myInvalidMessage);
      }
    }
  }

  public boolean isAt(@NotNull Document document, int offset) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    int line = document.getLineNumber(offset);
    XSourcePosition position = myXBreakpoint.getSourcePosition();
    return position != null && position.getLine() == line && position.getFile().equals(file);
  }

  protected void reload(PsiFile psiFile) {
  }

  @Override
  public PsiClass getPsiClass() {
    final SourcePosition sourcePosition = getSourcePosition();
    return getPsiClassAt(sourcePosition);
  }

  protected static PsiClass getPsiClassAt(@Nullable final SourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      @Override
      public PsiClass compute() {
        return JVMNameUtil.getClassAt(sourcePosition);
      }
    });
  }

  @Override
  public abstract Key<? extends BreakpointWithHighlighter> getCategory();

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  @Nullable
  public Document getDocument() {
    final PsiFile file = getPsiFile();
    if (file != null) {
      return PsiDocumentManager.getInstance(getProject()).getDocument(file);
    }
    return null;
  }

  public int getLineIndex() {
    XSourcePosition sourcePosition = myXBreakpoint.getSourcePosition();
    return sourcePosition != null ? sourcePosition.getLine() : -1;
  }

  protected String getFileName() {
    XSourcePosition sourcePosition = myXBreakpoint.getSourcePosition();
    return sourcePosition != null ? sourcePosition.getFile().getName() : "";
  }

  @Override
  public void readExternal(@NotNull Element breakpointNode) throws InvalidDataException {
    super.readExternal(breakpointNode);
    //noinspection HardCodedStringLiteral
    //final String url = breakpointNode.getAttributeValue("url");

    //noinspection HardCodedStringLiteral
    final String className = breakpointNode.getAttributeValue("class");
    if (className != null) {
      myClassName = className;
    }

    //noinspection HardCodedStringLiteral
    final String packageName = breakpointNode.getAttributeValue("package");
    if (packageName != null) {
      myPackageName = packageName;
    }
  }

  public String toString() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return CommonXmlStrings.HTML_START + CommonXmlStrings.BODY_START
               + getDescription()
               + CommonXmlStrings.BODY_END + CommonXmlStrings.HTML_END;
      }
    });
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xml.CommonXmlStrings;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.BreakpointRequest;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import javax.swing.*;

public abstract class BreakpointWithHighlighter<P extends JavaBreakpointProperties> extends Breakpoint<P> {
  private static final Logger LOG = Logger.getInstance(BreakpointWithHighlighter.class);

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

  protected Icon getInvalidIcon(boolean isMuted) {
    return AllIcons.Debugger.Db_invalid_breakpoint;
  }

  protected Icon getSetIcon(boolean isMuted) {
    return null;
  }

  protected abstract Icon getVerifiedIcon(boolean isMuted);

  protected abstract Icon getVerifiedWarningsIcon(boolean isMuted);

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  @Override
  public @NlsSafe String getClassName() {
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
    }

    return this;
  }

  private void updateCaches(@Nullable DebugProcessImpl debugProcess) {
    myIcon = calcIcon(debugProcess);
    if (isVisible() && isValid() && debugProcess != null && myXBreakpoint instanceof XLineBreakpoint) {
      JavaDebugProcess process = debugProcess.getXdebugProcess();
      if (process != null) {
        process.getSession().updateBreakpointPresentation(((XLineBreakpoint)myXBreakpoint), myIcon, myInvalidMessage);
      }
    }
    if (debugProcess != null && debugProcess.getVirtualMachineProxy().canBeModified()) {
      myClassName = JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, getSourcePosition());
      myPackageName = JVMNameUtil.getSourcePositionPackageDisplayName(debugProcess, getSourcePosition());
    }
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

    return getValidatingIcon(muted);
  }

  protected Icon getValidatingIcon(boolean muted) {
    if (myXBreakpoint != null) {
      Icon icon = myXBreakpoint.getType().getPendingIcon();
      if (icon != null) {
        return icon;
      }
    }
    return getSetIcon(muted);
  }

  protected BreakpointWithHighlighter(@NotNull Project project, XBreakpoint xBreakpoint) {
    //for persistency
    super(project, xBreakpoint);
    ApplicationManager.getApplication().runReadAction(this::reload);
  }

  @Override
  public boolean isValid() {
    return isPositionValid(myXBreakpoint.getSourcePosition());
  }

  protected static boolean isPositionValid(@Nullable final XSourcePosition sourcePosition) {
    return ReadAction.compute(() -> sourcePosition != null && sourcePosition.getFile().isValid()).booleanValue();
  }

  @Nullable
  public SourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @NotNull
  @Nls
  public String getDescription() {
    final StringBuilder buf = new StringBuilder();
    buf.append(getDisplayName());

    if (isCountFilterEnabled()) {
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(JavaDebuggerBundle.message("breakpoint.property.name.pass.count")).append(": ");
      buf.append(getCountFilter());
    }
    if (isClassFiltersEnabled()) {
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(JavaDebuggerBundle.message("breakpoint.property.name.class.filters")).append(": ");
      ClassFilter[] classFilters = getClassFilters();
      for (ClassFilter classFilter : classFilters) {
        buf.append(classFilter.getPattern()).append(" ");
      }
    }
    if (isInstanceFiltersEnabled()) {
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(JavaDebuggerBundle.message("breakpoint.property.name.instance.filters"));
      InstanceFilter[] instanceFilters = getInstanceFilters();
      for (InstanceFilter instanceFilter : instanceFilters) {
        buf.append(instanceFilter.getId()).append(" ");
      }
    }
    //noinspection HardCodedStringLiteral
    return buf.toString();
  }

  @Override
  public void reload() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    mySourcePosition = DebuggerUtilsEx.toSourcePosition(myXBreakpoint.getSourcePosition(), myProject);
  }

  @Nullable
  static BreakpointRequest createLocationBreakpointRequest(@NotNull FilteredRequestor requestor,
                                                           @Nullable Location location,
                                                           @NotNull DebugProcessImpl debugProcess) {
    if (location != null) {
      RequestManagerImpl requestsManager = debugProcess.getRequestsManager();
      BreakpointRequest request = requestsManager.createBreakpointRequest(requestor, location);
      requestsManager.enableRequest(request);
      return request;
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
      XSourcePosition xPosition = myXBreakpoint.getSourcePosition();
      LOG.error("Unable to create request for breakpoint with null position: " + this + " at " + xPosition +
                ", file valid = " + (xPosition != null && xPosition.getFile().isValid()));
    }
    updateUI();
  }

  protected boolean isMuted(@NotNull final DebugProcessImpl debugProcess) {
    return debugProcess.areBreakpointsMuted();
  }

  @Override
  public void processClassPrepare(DebugProcess debugProcess, ReferenceType classType) {
    DebugProcessImpl process = (DebugProcessImpl)debugProcess;
    if (shouldCreateRequest(process, true)) {
      createRequestForPreparedClass(process, classType);
      updateUI();
    }
  }

  /**
   * updates the state of breakpoint and all the related UI widgets etc
   */
  @Override
  public final void updateUI() {
    if (!isVisible() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
      if (!isValid()) {
        return;
      }

      DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
      DebugProcessImpl debugProcess = context.getDebugProcess();
      if (debugProcess == null || !debugProcess.isAttached()) {
        updateCaches(null);
      }
      else {
        debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            if (!myProject.isDisposed()) {
              updateCaches(debugProcess);
            }
          }
        });
      }
    });
  }

  public boolean isAt(@NotNull Document document, int offset) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    int line = document.getLineNumber(offset);
    XSourcePosition position = myXBreakpoint.getSourcePosition();
    return position != null && position.getLine() == line && position.getFile().equals(file);
  }

  @Override
  public PsiClass getPsiClass() {
    final SourcePosition sourcePosition = getSourcePosition();
    return getPsiClassAt(sourcePosition);
  }

  protected static PsiClass getPsiClassAt(@Nullable final SourcePosition sourcePosition) {
    return ReadAction.compute(() -> JVMNameUtil.getClassAt(sourcePosition));
  }

  @Override
  public abstract Key<? extends BreakpointWithHighlighter> getCategory();

  protected boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  @Nullable
  public Document getDocument() {
    PsiFile file = DebuggerUtilsEx.getPsiFile(myXBreakpoint.getSourcePosition(), myProject);
    if (file != null) {
      return PsiDocumentManager.getInstance(myProject).getDocument(file);
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
    //final String url = breakpointNode.getAttributeValue("url");

    final String className = breakpointNode.getAttributeValue("class");
    if (className != null) {
      myClassName = className;
    }

    final String packageName = breakpointNode.getAttributeValue("package");
    if (packageName != null) {
      myPackageName = packageName;
    }
  }

  public String toString() {
    return ReadAction.compute(() -> CommonXmlStrings.HTML_START + CommonXmlStrings.BODY_START
                                    + getDescription()
                                    + CommonXmlStrings.BODY_END + CommonXmlStrings.HTML_END);
  }
}

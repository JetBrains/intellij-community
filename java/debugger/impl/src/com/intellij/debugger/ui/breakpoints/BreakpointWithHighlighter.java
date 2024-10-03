// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.statistics.StatisticsStorage;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.BreakpointRequest;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

import javax.swing.*;
import java.util.List;

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
    if (debugProcess != null && debugProcess.getVirtualMachineProxy().canBeModified() && !isObsolete()) {
      if (myClassName == null) {
        myClassName = JVMNameUtil.getSourcePositionClassDisplayName(debugProcess, getSourcePosition());
      }
      if (myPackageName == null) {
        myPackageName = JVMNameUtil.getSourcePositionPackageDisplayName(debugProcess, getSourcePosition());
      }
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
    scheduleReload();
  }

  @Override
  void scheduleReload() {
    resetSourcePosition(); // sync init source position just in case
    super.scheduleReload();
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

  /**
   * @see #getDisplayName()
   * @deprecated better use {@link Breakpoint#getDisplayName()}
   */
  @NotNull
  @Nls
  @Deprecated
  public String getDescription() {
    return getDisplayName();
  }

  /**
   * Description lines of Java-specific breakpoint properties, XML formatted.
   */
  public List<@Nls String> getPropertyXMLDescriptions() {
    SmartList<String> res = new SmartList<>();

    if (isCountFilterEnabled()) {
      res.add(JavaDebuggerBundle.message("breakpoint.property.name.pass.count") + CommonXmlStrings.NBSP
              + getCountFilter());
    }
    if (isClassFiltersEnabled()) {
      StringBuilder buf = new StringBuilder();
      buf.append(JavaDebuggerBundle.message("breakpoint.property.name.class.filters")).append(CommonXmlStrings.NBSP);
      for (ClassFilter classFilter : getClassFilters()) {
        buf.append(XmlStringUtil.escapeString(classFilter.getPattern())).append(CommonXmlStrings.NBSP);
      }
      res.add(buf.toString());
    }
    if (isInstanceFiltersEnabled()) {
      StringBuilder buf = new StringBuilder();
      buf.append(JavaDebuggerBundle.message("breakpoint.property.name.instance.filters")).append(CommonXmlStrings.NBSP);
      for (InstanceFilter instanceFilter : getInstanceFilters()) {
        buf.append(instanceFilter.getId()).append(CommonXmlStrings.NBSP);
      }
      res.add(buf.toString());
    }
    return res;
  }


  @RequiresBackgroundThread
  @RequiresReadLock
  @Override
  public void reload() {
    if (!myProject.isDisposed()) {
      resetSourcePosition();
    }
  }

  private void resetSourcePosition() {
    mySourcePosition = DebuggerUtilsEx.toSourcePosition(ObjectUtils.doIfNotNull(myXBreakpoint, XBreakpoint::getSourcePosition), myProject);
    myClassName = null;
    myPackageName = null;
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

    ReadAction.run(this::reload); // force reload to ensure the most recent data

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
      long timeMs = TimeoutUtil.measureExecutionTime(() -> createRequestForPreparedClass(process, classType));
      StatisticsStorage.addBreakpointInstall(debugProcess, this, timeMs);
      updateUI();
    }
  }

  /**
   * updates the state of breakpoint and all the related UI widgets etc
   */
  @Override
  public final void updateUI() {
    if (!isVisible() || ApplicationManager.getApplication().isUnitTestMode() || !isValid()) {
      return;
    }
    DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
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
      return file.getViewProvider().getDocument();
    }
    return null;
  }

  public int getLineIndex() {
    XSourcePosition sourcePosition = myXBreakpoint.getSourcePosition();
    return sourcePosition != null ? sourcePosition.getLine() : -1;
  }

  protected String getFileName() {
    VirtualFile file = getVirtualFile();
    return file != null ? file.getName() : "";
  }

  @Nullable
  protected VirtualFile getVirtualFile() {
    return ObjectUtils.doIfNotNull(ObjectUtils.doIfNotNull(myXBreakpoint, XBreakpoint::getSourcePosition), XSourcePosition::getFile);
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
                                    + XmlStringUtil.escapeString(getDisplayName())
                                    + CommonXmlStrings.BODY_END + CommonXmlStrings.HTML_END);
  }
}

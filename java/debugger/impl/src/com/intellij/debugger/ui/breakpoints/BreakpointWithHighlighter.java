/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.debugger.*;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
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
import com.intellij.xdebugger.ui.DebuggerColors;
import com.intellij.xml.util.XmlStringUtil;
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
    reload();
  }

  @Override
  public boolean isValid() {
    return isPositionValid(myXBreakpoint.getSourcePosition());
  }

  protected static boolean isPositionValid(@Nullable final XSourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return sourcePosition != null && sourcePosition.getFile().isValid() ? Boolean.TRUE : Boolean.FALSE;
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
      buf.append("<html><body>");
      buf.append(getDisplayName());
      if (myInvalidMessage != null && !myInvalidMessage.isEmpty()) {
        buf.append("<br><font color='red'>");
        buf.append(DebuggerBundle.message("breakpoint.warning", myInvalidMessage));
        buf.append("</font>");
      }
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(DebuggerBundle.message("breakpoint.property.name.suspend.policy")).append(" : ");
      if (DebuggerSettings.SUSPEND_NONE.equals(getSuspendPolicy()) || !isSuspend()) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.none"));
      }
      else if (DebuggerSettings.SUSPEND_ALL.equals(getSuspendPolicy())) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.all"));
      }
      else if (DebuggerSettings.SUSPEND_THREAD.equals(getSuspendPolicy())) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.thread"));
      }
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(DebuggerBundle.message("breakpoint.property.name.log.message")).append(": ");
      buf.append(isLogEnabled() ? CommonBundle.getYesButtonText() : CommonBundle.getNoButtonText());
      if (isLogExpressionEnabled()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.log.expression")).append(": ");
        buf.append(XmlStringUtil.escapeString(getLogMessage().getText()));
      }
      if (isConditionEnabled() && getCondition() != null && getCondition().getText() != null && !getCondition().getText().isEmpty()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.condition")).append(": ");
        buf.append(XmlStringUtil.escapeString(getCondition().getText()));
      }
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
      buf.append("</body></html>");
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
    if (!isEnabled() ||
        !debugProcess.isAttached() ||
        isMuted(debugProcess) ||
        !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
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
      LOG.error("Unable to create request for breakpoint with null position: " + getDisplayName() + " at " + myXBreakpoint.getSourcePosition());
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
                  updateCaches(debugProcess);
                }
              });
              DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
                @Override
                public void run() {
                  updateGutter();
                }
              });
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
        breakpointManager.updateBreakpointPresentation((XLineBreakpoint)myXBreakpoint, getIcon(), null);
      }
      //RangeHighlighter highlighter = myHighlighter;
      //if (highlighter != null && highlighter.isValid() && isValid()) {
      //  AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      //    @Override
      //    public void run() {
      //      if (isValid()) {
      //        setupGutterRenderer(myHighlighter);
      //      }
      //    }
      //  });
      //}
      //else {
      //  DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(this);
      //}
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

  protected static PsiClass getPsiClassAt(final SourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Nullable
      @Override
      public PsiClass compute() {
        return JVMNameUtil.getClassAt(sourcePosition);
      }
    });
  }

  //private void setupGutterRenderer(@NotNull RangeHighlighter highlighter) {
  //  highlighter.setGutterIconRenderer(new MyGutterIconRenderer(getIcon(), getDescription()));
  //}

  @Override
  public abstract Key<? extends BreakpointWithHighlighter> getCategory();

  //public boolean canMoveTo(@Nullable final SourcePosition position) {
  //  if (position == null || !position.getFile().isValid()) {
  //    return false;
  //  }
  //  final PsiFile psiFile = position.getFile();
  //  final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
  //  if (document == null) {
  //    return false;
  //  }
  //  final int spOffset = position.getOffset();
  //  if (spOffset < 0) {
  //    return false;
  //  }
  //  final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
  //  return breakpointManager.findBreakpoint(document, spOffset, getCategory()) == null;
  //}

  //public boolean moveTo(@NotNull SourcePosition position) {
  //  if (!canMoveTo(position)) {
  //    return false;
  //  }
  //  final PsiFile psiFile = position.getFile();
  //  final PsiFile oldFile = getSourcePosition().getFile();
  //  final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
  //  final Document oldDocument = PsiDocumentManager.getInstance(getProject()).getDocument(oldFile);
  //  if (document == null || oldDocument == null) {
  //    return false;
  //  }
  //  final RangeHighlighter newHighlighter = createHighlighter(myProject, document, position.getLine());
  //  if (newHighlighter == null) {
  //    return false;
  //  }
  //  final RangeHighlighter oldHighlighter = myHighlighter;
  //  myHighlighter = newHighlighter;
  //
  //  reload();
  //
  //  if (!isValid()) {
  //    myHighlighter.dispose();
  //    myHighlighter = oldHighlighter;
  //    reload();
  //    return false;
  //  }
  //
  //  if (oldHighlighter != null) {
  //    oldHighlighter.dispose();
  //  }
  //
  //  DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().fireBreakpointChanged(this);
  //  updateUI();
  //
  //  return true;
  //}

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
    final SourcePosition sourcePosition = getSourcePosition();
    return sourcePosition != null ? sourcePosition.getLine() : -1;
  }

  @Nullable
  protected static RangeHighlighter createHighlighter(@NotNull Project project, @NotNull Document document, int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return null;
    }

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    RangeHighlighter highlighter = ((MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true))
      .addPersistentLineHighlighter(lineIndex, DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes);
    if (highlighter == null || !highlighter.isValid()) {
      return null;
    }
    highlighter.putUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY, Boolean.TRUE);
    highlighter.setErrorStripeTooltip(DebuggerBundle.message("breakpoint.tooltip.text", lineIndex + 1));
    return highlighter;
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

    //VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
    //if (vFile == null) {
    //  throw new InvalidDataException(DebuggerBundle.message("error.breakpoint.file.not.found", url));
    //}
    //final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    //if (doc == null) {
    //  throw new InvalidDataException(DebuggerBundle.message("error.cannot.load.breakpoint.file", url));
    //}
    //
    //// line number
    //final int line;
    //try {
    //  //noinspection HardCodedStringLiteral
    //  line = Integer.parseInt(breakpointNode.getAttributeValue("line"));
    //}
    //catch (Exception e) {
    //  throw new InvalidDataException("Line number is invalid for breakpoint");
    //}
    //if (line < 0) {
    //  throw new InvalidDataException("Line number is invalid for breakpoint");
    //}
    //
    //RangeHighlighter highlighter = createHighlighter(myProject, doc, line);
    //
    //if (highlighter == null) {
    //  throw new InvalidDataException("");
    //}
    //
    //myHighlighter = highlighter;
    //reload();
  }
  //
  //@Override
  //@SuppressWarnings({"HardCodedStringLiteral"})
  //public void writeExternal(@NotNull Element parentNode) throws WriteExternalException {
  //  super.writeExternal(parentNode);
  //  PsiFile psiFile = getSourcePosition().getFile();
  //  final VirtualFile virtualFile = psiFile.getVirtualFile();
  //  final String url = virtualFile != null ? virtualFile.getUrl() : "";
  //  parentNode.setAttribute("url", url);
  //  parentNode.setAttribute("line", Integer.toString(getSourcePosition().getLine()));
  //  if (myClassName != null) {
  //    parentNode.setAttribute("class", myClassName);
  //  }
  //  if (myPackageName != null) {
  //    parentNode.setAttribute("package", myPackageName);
  //  }
  //}

  //private class MyGutterIconRenderer extends GutterIconRenderer {
  //  private final Icon myIcon;
  //  private final String myDescription;
  //
  //  public MyGutterIconRenderer(@NotNull Icon icon, @NotNull String description) {
  //    myIcon = icon;
  //    myDescription = description;
  //  }
  //
  //  @Override
  //  @NotNull
  //  public Icon getIcon() {
  //    return myIcon;
  //  }
  //
  //  @Override
  //  public String getTooltipText() {
  //    return myDescription;
  //  }
  //
  //  @Override
  //  public Alignment getAlignment() {
  //    return Alignment.RIGHT;
  //  }
  //
  //  @Override
  //  public AnAction getClickAction() {
  //    return new AnAction() {
  //      @Override
  //      public void actionPerformed(AnActionEvent e) {
  //        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(BreakpointWithHighlighter.this);
  //      }
  //    };
  //  }
  //
  //  @Override
  //  public AnAction getMiddleButtonClickAction() {
  //    return new AnAction() {
  //      @Override
  //      public void actionPerformed(AnActionEvent e) {
  //        setEnabled(!isEnabled());
  //        DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().fireBreakpointChanged(BreakpointWithHighlighter.this);
  //        updateUI();
  //      }
  //    };
  //  }
  //
  //  @Override
  //  public ActionGroup getPopupMenuActions() {
  //    return null;
  //  }
  //
  //  @Nullable
  //  @Override
  //  public AnAction getRightButtonClickAction() {
  //    return new EditBreakpointAction.ContextAction(this, BreakpointWithHighlighter.this, DebuggerSupport.getDebuggerSupport(JavaDebuggerSupport.class));
  //  }
  //
  //  @Override
  //  public GutterDraggableObject getDraggableObject() {
  //    return new GutterDraggableObject() {
  //      @Override
  //      public boolean copy(int line, @NotNull VirtualFile file) {
  //        final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
  //        return psiFile != null && moveTo(SourcePosition.createFromLine(psiFile, line));
  //      }
  //
  //      @Override
  //      public Cursor getCursor(int line) {
  //        final SourcePosition newPosition = SourcePosition.createFromLine(getSourcePosition().getFile(), line);
  //        return canMoveTo(newPosition) ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop;
  //      }
  //    };
  //  }
  //
  //  @Override
  //  public boolean equals(@NotNull Object obj) {
  //    return obj instanceof MyGutterIconRenderer &&
  //           Comparing.equal(getTooltipText(), ((MyGutterIconRenderer)obj).getTooltipText()) &&
  //           Comparing.equal(getIcon(), ((MyGutterIconRenderer)obj).getIcon());
  //  }
  //
  //  @Override
  //  public int hashCode() {
  //    return getIcon().hashCode();
  //  }
  //
  //  @Override
  //  public String toString() {
  //    return "LB " + getDisplayName();
  //  }
  //}

}

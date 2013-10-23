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
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.jsp.JspFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.EditBreakpointAction;
import com.intellij.xdebugger.ui.DebuggerColors;
import com.intellij.xml.util.XmlStringUtil;
import com.sun.jdi.ReferenceType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DragSource;

/**
 * User: lex
 * Date: Sep 2, 2003
 * Time: 3:22:55 PM
 */
public abstract class BreakpointWithHighlighter extends Breakpoint {
  @Nullable
  private RangeHighlighter myHighlighter;

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
      final RangeHighlighter highlighter = myHighlighter;
      if (highlighter != null) {
        highlighter.dispose();
      }
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
    if (!ENABLED) {
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

  protected BreakpointWithHighlighter(@NotNull Project project) {
    //for persistency
    super(project);
  }

  public BreakpointWithHighlighter(@NotNull final Project project, @NotNull final RangeHighlighter highlighter) {
    super(project);
    myHighlighter = highlighter;
    setEditorFilter(highlighter);
    reload();
  }

  protected void setEditorFilter(RangeHighlighter highlighter) {
    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return myHighlighter;
  }

  @Override
  public boolean isValid() {
    return isPositionValid(getSourcePosition());
  }

  private static boolean isPositionValid(@Nullable final SourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return sourcePosition != null && sourcePosition.getFile().isValid() ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue();
  }

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
      if (DebuggerSettings.SUSPEND_NONE.equals(SUSPEND_POLICY) || !SUSPEND) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.none"));
      }
      else if (DebuggerSettings.SUSPEND_ALL.equals(SUSPEND_POLICY)) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.all"));
      }
      else if (DebuggerSettings.SUSPEND_THREAD.equals(SUSPEND_POLICY)) {
        buf.append(DebuggerBundle.message("breakpoint.properties.panel.option.suspend.thread"));
      }
      buf.append("&nbsp;<br>&nbsp;");
      buf.append(DebuggerBundle.message("breakpoint.property.name.log.message")).append(": ");
      buf.append(LOG_ENABLED ? CommonBundle.getYesButtonText() : CommonBundle.getNoButtonText());
      if (LOG_EXPRESSION_ENABLED) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.log.expression")).append(": ");
        buf.append(XmlStringUtil.escapeString(getLogMessage().getText()));
      }
      if (CONDITION_ENABLED && getCondition() != null && getCondition().getText() != null && !getCondition().getText().isEmpty()) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.condition")).append(": ");
        buf.append(XmlStringUtil.escapeString(getCondition().getText()));
      }
      if (COUNT_FILTER_ENABLED) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.pass.count")).append(": ");
        buf.append(COUNT_FILTER);
      }
      if (CLASS_FILTERS_ENABLED) {
        buf.append("&nbsp;<br>&nbsp;");
        buf.append(DebuggerBundle.message("breakpoint.property.name.class.filters")).append(": ");
        ClassFilter[] classFilters = getClassFilters();
        for (ClassFilter classFilter : classFilters) {
          buf.append(classFilter.getPattern()).append(" ");
        }
      }
      if (INSTANCE_FILTERS_ENABLED) {
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
  public final void reload() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RangeHighlighter highlighter = myHighlighter;
    if (highlighter != null && highlighter.isValid()) {
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(highlighter.getDocument());
      if (psiFile != null) {
        mySourcePosition = SourcePosition.createFromOffset(psiFile, highlighter.getStartOffset());
        reload(psiFile);
        return;
      }
    }
    mySourcePosition = null;
  }

  @Override
  public void createRequest(@NotNull DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    // check is this breakpoint is enabled, vm reference is valid and there're no requests created yet
    if (!ENABLED ||
        !debugProcess.isAttached() ||
        isMuted(debugProcess) ||
        !debugProcess.getRequestsManager().findRequests(this).isEmpty()) {
      return;
    }

    if (!isValid()) {
      return;
    }

    createOrWaitPrepare(debugProcess, getSourcePosition());
    updateUI();
  }

  protected boolean isMuted(@NotNull final DebugProcessImpl debugProcess) {
    return debugProcess.areBreakpointsMuted();
  }

  @Override
  public void processClassPrepare(final DebugProcess debugProcess, final ReferenceType classType) {
    if (!ENABLED || !isValid()) {
      return;
    }
    createRequestForPreparedClass((DebugProcessImpl)debugProcess, classType);
    updateUI();
  }

  /**
   * updates the state of breakpoint and all the related UI widgets etc
   */
  @Override
  public final void updateUI(@NotNull final Runnable afterUpdate) {
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

          afterUpdate.run();
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
                  afterUpdate.run();
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
      RangeHighlighter highlighter = myHighlighter;
      if (highlighter != null && highlighter.isValid() && isValid()) {
        AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
          @Override
          public void run() {
            if (isValid()) {
              setupGutterRenderer(myHighlighter);
            }
          }
        });
      }
      else {
        DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(this);
      }
    }
  }

  /**
   * called by BreakpointManager when destroying the breakpoint
   */
  @Override
  public void delete() {
    if (isVisible()) {
      final RangeHighlighter highlighter = getHighlighter();
      if (highlighter != null) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          @Override
          public void run() {
            highlighter.dispose();
            //we should delete it here, so gutter will not fire events to deleted breakpoint
            BreakpointWithHighlighter.super.delete();
          }
        });
      }
    }

  }

  public boolean isAt(@NotNull Document document, int offset) {
    RangeHighlighter highlighter = getHighlighter();
    return highlighter != null &&
           highlighter.isValid() &&
           document.equals(highlighter.getDocument()) &&
           getSourcePosition().getLine() == document.getLineNumber(offset);
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

  private void setupGutterRenderer(@NotNull RangeHighlighter highlighter) {
    highlighter.setGutterIconRenderer(new MyGutterIconRenderer(getIcon(), getDescription()));
  }

  @Override
  public abstract Key<? extends BreakpointWithHighlighter> getCategory();

  public boolean canMoveTo(@Nullable final SourcePosition position) {
    if (position == null || !position.getFile().isValid()) {
      return false;
    }
    final PsiFile psiFile = position.getFile();
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    if (document == null) {
      return false;
    }
    final int spOffset = position.getOffset();
    if (spOffset < 0) {
      return false;
    }
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager();
    return breakpointManager.findBreakpoint(document, spOffset, getCategory()) == null;
  }

  public boolean moveTo(@NotNull SourcePosition position) {
    if (!canMoveTo(position)) {
      return false;
    }
    final PsiFile psiFile = position.getFile();
    final PsiFile oldFile = getSourcePosition().getFile();
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    final Document oldDocument = PsiDocumentManager.getInstance(getProject()).getDocument(oldFile);
    if (document == null || oldDocument == null) {
      return false;
    }
    final RangeHighlighter newHighlighter = createHighlighter(myProject, document, position.getLine());
    if (newHighlighter == null) {
      return false;
    }
    final RangeHighlighter oldHighlighter = myHighlighter;
    myHighlighter = newHighlighter;

    reload();

    if (!isValid()) {
      myHighlighter.dispose();
      myHighlighter = oldHighlighter;
      reload();
      return false;
    }

    if (oldHighlighter != null) {
      oldHighlighter.dispose();
    }

    DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().fireBreakpointChanged(this);
    updateUI();

    return true;
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  @Nullable
  public Document getDocument() {
    final RangeHighlighter highlighter = getHighlighter();
    if (highlighter != null) {
      return highlighter.getDocument();
    }
    final SourcePosition position = getSourcePosition();
    if (position != null) {
      final PsiFile file = position.getFile();
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
    final String url = breakpointNode.getAttributeValue("url");

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

    VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (vFile == null) {
      throw new InvalidDataException(DebuggerBundle.message("error.breakpoint.file.not.found", url));
    }
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null) {
      throw new InvalidDataException(DebuggerBundle.message("error.cannot.load.breakpoint.file", url));
    }

    // line number
    final int line;
    try {
      //noinspection HardCodedStringLiteral
      line = Integer.parseInt(breakpointNode.getAttributeValue("line"));
    }
    catch (Exception e) {
      throw new InvalidDataException("Line number is invalid for breakpoint");
    }
    if (line < 0) {
      throw new InvalidDataException("Line number is invalid for breakpoint");
    }

    RangeHighlighter highlighter = createHighlighter(myProject, doc, line);

    if (highlighter == null) {
      throw new InvalidDataException("");
    }

    myHighlighter = highlighter;
    reload();
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(@NotNull Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    PsiFile psiFile = getSourcePosition().getFile();
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    final String url = virtualFile != null ? virtualFile.getUrl() : "";
    parentNode.setAttribute("url", url);
    parentNode.setAttribute("line", Integer.toString(getSourcePosition().getLine()));
    if (myClassName != null) {
      parentNode.setAttribute("class", myClassName);
    }
    if (myPackageName != null) {
      parentNode.setAttribute("package", myPackageName);
    }
  }

  private class MyGutterIconRenderer extends GutterIconRenderer {
    private final Icon myIcon;
    private final String myDescription;

    public MyGutterIconRenderer(@NotNull Icon icon, @NotNull String description) {
      myIcon = icon;
      myDescription = description;
    }

    @Override
    @NotNull
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public String getTooltipText() {
      return myDescription;
    }

    @Override
    public Alignment getAlignment() {
      return Alignment.RIGHT;
    }

    @Override
    public AnAction getClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().removeBreakpoint(BreakpointWithHighlighter.this);
        }
      };
    }

    @Override
    public AnAction getMiddleButtonClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ENABLED = !ENABLED;
          DebuggerManagerEx.getInstanceEx(getProject()).getBreakpointManager().fireBreakpointChanged(BreakpointWithHighlighter.this);
          updateUI();
        }
      };
    }

    @Override
    public ActionGroup getPopupMenuActions() {
      return null;
    }

    @Nullable
    @Override
    public AnAction getRightButtonClickAction() {
      return new EditBreakpointAction.ContextAction(this, BreakpointWithHighlighter.this, DebuggerSupport.getDebuggerSupport(JavaDebuggerSupport.class));
    }

    @Override
    public GutterDraggableObject getDraggableObject() {
      return new GutterDraggableObject() {
        @Override
        public boolean copy(int line, @NotNull VirtualFile file) {
          final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
          return psiFile != null && moveTo(SourcePosition.createFromLine(psiFile, line));
        }

        @Override
        public Cursor getCursor(int line) {
          final SourcePosition newPosition = SourcePosition.createFromLine(getSourcePosition().getFile(), line);
          return canMoveTo(newPosition) ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop;
        }
      };
    }

    @Override
    public boolean equals(@NotNull Object obj) {
      return obj instanceof MyGutterIconRenderer &&
             Comparing.equal(getTooltipText(), ((MyGutterIconRenderer)obj).getTooltipText()) &&
             Comparing.equal(getIcon(), ((MyGutterIconRenderer)obj).getIcon());
    }

    @Override
    public int hashCode() {
      return getIcon().hashCode();
    }

    @Override
    public String toString() {
      return "LB " + getDisplayName();
    }
  }

}

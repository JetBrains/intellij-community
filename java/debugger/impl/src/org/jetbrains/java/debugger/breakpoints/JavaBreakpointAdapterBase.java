package org.jetbrains.java.debugger.breakpoints;

import com.intellij.debugger.ui.breakpoints.LineBreakpoint;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaBreakpointAdapterBase extends XBreakpointAdapter<XLineBreakpoint<XBreakpointProperties>> {
  protected final Project myProject;

  public JavaBreakpointAdapterBase(Project project) {
    myProject = project;
  }

  @Nullable
  public LineBreakpoint createBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint) {
    String url = breakpoint.getFileUrl();
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    Document document = file != null ? FileDocumentManager.getInstance().getDocument(file) : null;
    if (document == null) {
      return null;
    }
    LineBreakpoint jBreakpoint = createInvisibleBreakpoint(myProject, document, breakpoint);
    configureCreatedBreakpoint(jBreakpoint, breakpoint);
    return jBreakpoint;
  }

  protected void configureCreatedBreakpoint(LineBreakpoint oldBreakpoint, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    final RangeHighlighter highlighter = oldBreakpoint.getHighlighter();
    if (highlighter != null) {
      highlighter.dispose();
    }
  }

  @Override
  public final void breakpointChanged(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint) {
    LineBreakpoint jBreakpoint = findBreakpoint(breakpoint);
    if (jBreakpoint != null) {
      updateBreakpoint(jBreakpoint, breakpoint);
    }
  }

  protected abstract void updateBreakpoint(LineBreakpoint oldBreakpoint, XLineBreakpoint<XBreakpointProperties> breakpoint);

  protected abstract LineBreakpoint findBreakpoint(XLineBreakpoint<XBreakpointProperties> breakpoint);

  protected LineBreakpoint createInvisibleBreakpoint(Project project, Document document, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    LineBreakpoint oldBreakpoint = doCreateInstance(project, document, breakpoint);
    oldBreakpoint.setVisible(false);
    oldBreakpoint.updateUI();
    oldBreakpoint.ENABLED = breakpoint.isEnabled();
    return oldBreakpoint;
  }

  protected LineBreakpoint doCreateInstance(Project project, Document document, XLineBreakpoint<XBreakpointProperties> breakpoint) {
    return LineBreakpoint.create(project, document, breakpoint.getLine());
  }
}

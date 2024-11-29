// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SourcePositionProvider;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.FieldBreakpoint;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleFieldBreakpointAction extends AnAction implements ActionRemoteBehaviorSpecification.Disabled {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    final SourcePosition place = getPlace(e);

    if (place != null) {
      Document document = place.getFile().getViewProvider().getDocument();
      if (document != null) {
        DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
        BreakpointManager manager = debuggerManager.getBreakpointManager();
        final int offset = place.getOffset();
        final Breakpoint breakpoint = offset >= 0 ? manager.findBreakpoint(document, offset, FieldBreakpoint.CATEGORY) : null;

        if (breakpoint == null) {
          FieldBreakpoint fieldBreakpoint = manager.addFieldBreakpoint(document, offset);
          if (fieldBreakpoint != null) {
            final Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
              manager.editBreakpoint(fieldBreakpoint, editor);
            }
          }
        }
        else {
          manager.removeBreakpoint(breakpoint);
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    SourcePosition place = getPlace(event);
    boolean toEnable = place != null;

    Presentation presentation = event.getPresentation();
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.BOOKMARKS_VIEW_POPUP.equals(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    presentation.setVisible(toEnable);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  private static SourcePosition getPlace(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return null;
    }
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.BOOKMARKS_VIEW_POPUP.equals(event.getPlace())) {
      final PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
      if (psiElement instanceof PsiField) {
        return SourcePosition.createFromElement(psiElement);
      }
      return null;
    }

    XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
    if (value instanceof NodeDescriptorProvider) {
      NodeDescriptorImpl descriptor = ((NodeDescriptorProvider)value).getDescriptor();
      if (descriptor instanceof FieldDescriptorImpl) {
        final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(dataContext);
        DebuggerManagerThreadImpl managerThread = debuggerContext.getManagerThread();
        if (managerThread != null) { // if there is an active debug session
          final Ref<SourcePosition> positionRef = new Ref<>(null);
          managerThread.invokeAndWait(new DebuggerContextCommandImpl(debuggerContext) {
            @Override
            public Priority getPriority() {
              return Priority.HIGH;
            }

            @Override
            public void threadAction(@NotNull SuspendContextImpl suspendContext) {
              ApplicationManager.getApplication().runReadAction(
                () -> positionRef.set(SourcePositionProvider.getSourcePosition(descriptor, project, debuggerContext)));
            }
          });
          final SourcePosition sourcePosition = positionRef.get();
          if (sourcePosition != null) {
            return sourcePosition;
          }
        }
      }
    }

    Editor editor = ToggleMethodBreakpointAction.getEditor(event);
    if (editor != null) {
      final Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file != null) {
        final VirtualFile virtualFile = file.getVirtualFile();
        FileType fileType = virtualFile != null ? virtualFile.getFileType() : null;
        if (JavaFileType.INSTANCE == fileType || JavaClassFileType.INSTANCE == fileType) {
          final PsiField field = FieldBreakpoint.findField(project, document, editor.getCaretModel().getOffset());
          if (field != null) {
            return SourcePosition.createFromElement(field);
          }
        }
      }
    }
    return null;
  }
}

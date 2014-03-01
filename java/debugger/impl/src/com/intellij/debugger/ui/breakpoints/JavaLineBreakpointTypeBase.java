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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointFiltersPanel;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;

/**
 * Base class for java line-connected exceptions (line, method, field)
 * @author egor
 */
public abstract class JavaLineBreakpointTypeBase<P extends JavaBreakpointProperties> extends XLineBreakpointType<P> {
  public JavaLineBreakpointTypeBase(@NonNls @NotNull String id, @Nls @NotNull String title) {
    super(id, title);
  }

  @Override
  public boolean isAddBreakpointButtonVisible() {
    return true;
  }

  @Override
  public final boolean isSuspendThreadSupported() {
    return true;
  }

  @Nullable
  @Override
  public final XBreakpointCustomPropertiesPanel<XLineBreakpoint<P>> createCustomRightPropertiesPanel(@NotNull Project project) {
    return new JavaBreakpointFiltersPanel<P, XLineBreakpoint<P>>(project);
  }

  @Nullable
  @Override
  public final XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<P> breakpoint, @NotNull Project project) {
    return new JavaDebuggerEditorsProvider();
  }

  @Override
  public String getDisplayText(XLineBreakpoint<P> breakpoint) {
    BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(JavaDebuggerSupport.getCurrentProject()).getBreakpointManager();
      BreakpointWithHighlighter javaBreakpoint = (BreakpointWithHighlighter)breakpointManager.findBreakpoint(breakpoint);
      if (javaBreakpoint != null) {
        return javaBreakpoint.getDescription();
      }
      else {
        return super.getDisplayText(breakpoint);
      }
  }

  @Override
  public final boolean canPutAt(@NotNull VirtualFile file, final int line, @NotNull Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    // JSPX supports jvm debugging, but not in XHTML files
    if (psiFile == null || psiFile.getVirtualFile().getFileType() == StdFileTypes.XHTML) {
      return false;
    }

    FileType fileType = psiFile.getFileType();
    if (!StdFileTypes.CLASS.equals(fileType) &&
        !DebuggerUtils.supportsJVMDebugging(fileType) &&
        !DebuggerUtils.supportsJVMDebugging(psiFile)) {
      return false;
    }

    final Document document = FileDocumentManager.getInstance().getDocument(file);
    final Ref<Class<? extends JavaLineBreakpointTypeBase>> result = Ref.create();
    XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        // avoid comments
        if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
          return true;
        }
        // first check fields
        PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
        if(field != null) {
          result.set(JavaFieldBreakpointType.class);
          return false;
        }
        // then methods
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        if(method != null && document.getLineNumber(method.getTextOffset()) == line) {
          result.set(JavaMethodBreakpointType.class);
          return false;
        }
        // then regular statements
        PsiElement child = element;
        while(element != null) {

          final int offset = element.getTextOffset();
          if (offset >= 0) {
            if (document.getLineNumber(offset) != line) {
              break;
            }
          }
          child = element;
          element = element.getParent();
        }

        if(child instanceof PsiMethod && child.getTextRange().getEndOffset() >= document.getLineEndOffset(line)) {
          PsiCodeBlock body = ((PsiMethod)child).getBody();
          if(body != null) {
            PsiStatement[] statements = body.getStatements();
            if (statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == line) {
              result.set(JavaLineBreakpointType.class);
            }
          }
        }
        else {
          result.set(JavaLineBreakpointType.class);
        }
        return true;
      }
    });
    return result.get() == getClass();
  }
}

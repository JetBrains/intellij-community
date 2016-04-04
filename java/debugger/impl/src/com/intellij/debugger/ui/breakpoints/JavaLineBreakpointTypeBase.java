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

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
    return false;
  }

  @Override
  public final boolean isSuspendThreadSupported() {
    return true;
  }

  @Nullable
  @Override
  public final XBreakpointCustomPropertiesPanel<XLineBreakpoint<P>> createCustomRightPropertiesPanel(@NotNull Project project) {
    return new JavaBreakpointFiltersPanel<>(project);
  }

  @Nullable
  @Override
  public final XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<P> breakpoint, @NotNull Project project) {
    return new JavaDebuggerEditorsProvider();
  }

  @Override
  public String getDisplayText(XLineBreakpoint<P> breakpoint) {
    BreakpointWithHighlighter javaBreakpoint = (BreakpointWithHighlighter)BreakpointManager.getJavaBreakpoint(breakpoint);
    if (javaBreakpoint != null) {
      return javaBreakpoint.getDescription();
    }
    else {
      return super.getDisplayText(breakpoint);
    }
  }

  @Override
  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    // JSPX supports jvm debugging, but not in XHTML files
    if (psiFile == null || psiFile.getViewProvider().getFileType() == StdFileTypes.XHTML) {
      return false;
    }

    if (!StdFileTypes.CLASS.equals(psiFile.getFileType()) && !DebuggerUtils.isBreakpointAware(psiFile)) {
      return false;
    }

    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    final Ref<Class<? extends JavaLineBreakpointTypeBase>> result = Ref.create();
    XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        // avoid comments
        if ((element instanceof PsiWhiteSpace)
            || (PsiTreeUtil.getParentOfType(element, PsiComment.class, PsiImportStatementBase.class, PsiPackageStatement.class) != null)) {
          return true;
        }
        PsiElement parent = element;
        while(element != null) {
          // skip modifiers
          if (element instanceof PsiModifierList) {
            element = element.getParent();
            continue;
          }

          final int offset = element.getTextOffset();
          if (offset >= 0) {
            if (document.getLineNumber(offset) != line) {
              break;
            }
          }
          parent = element;
          element = element.getParent();
        }

        if(parent instanceof PsiMethod) {
          if (parent.getTextRange().getEndOffset() >= document.getLineEndOffset(line)) {
            PsiCodeBlock body = ((PsiMethod)parent).getBody();
            if (body != null) {
              PsiStatement[] statements = body.getStatements();
              if (statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == line) {
                result.set(JavaLineBreakpointType.class);
              }
            }
          }
          if (result.isNull()) {
            result.set(JavaMethodBreakpointType.class);
          }
        }
        else if (parent instanceof PsiField) {
          if (result.isNull()) {
            result.set(JavaFieldBreakpointType.class);
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

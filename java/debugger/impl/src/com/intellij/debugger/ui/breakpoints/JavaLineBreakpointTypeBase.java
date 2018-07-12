// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.PairFunction;
import com.intellij.xdebugger.XDebugSession;
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

import java.util.List;

/**
 * Base class for java line-connected exceptions (line, method, field)
 * @author egor
 */
public abstract class JavaLineBreakpointTypeBase<P extends JavaBreakpointProperties> extends XLineBreakpointType<P>
  implements JavaBreakpointType<P> {
  public JavaLineBreakpointTypeBase(@NonNls @NotNull String id, @Nls @NotNull String title) {
    super(id, title);
  }

  @Override
  public final boolean isSuspendThreadSupported() {
    return true;
  }

  @NotNull
  @Override
  public final XBreakpointCustomPropertiesPanel<XLineBreakpoint<P>> createCustomRightPropertiesPanel(@NotNull Project project) {
    return new JavaBreakpointFiltersPanel<>(project);
  }

  @NotNull
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

  protected static boolean canPutAtElement(@NotNull final VirtualFile file,
                                           final int line,
                                           @NotNull Project project,
                                           @NotNull PairFunction<PsiElement, Document, Boolean> processor) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    // JSPX supports jvm debugging, but not in XHTML files
    if (psiFile == null || psiFile.getViewProvider().getFileType() == StdFileTypes.XHTML) {
      return false;
    }

    if (!StdFileTypes.CLASS.equals(psiFile.getFileType()) && !DebuggerUtils.isBreakpointAware(psiFile)) {
      return false;
    }

    // workaround for KT-23886, remove after it is fixed
    if ("kt".equals(psiFile.getFileType().getDefaultExtension())) {
      return false;
    }

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      Ref<Boolean> res = Ref.create(false);
      XDebuggerUtil.getInstance().iterateLine(project, document, line, element -> {
        // avoid comments
        if ((element instanceof PsiWhiteSpace)
            || (PsiTreeUtil.getParentOfType(element, PsiComment.class, PsiImportStatementBase.class, PsiPackageStatement.class) != null)) {
          return true;
        }
        PsiElement parent = element;
        while (element != null) {
          // skip modifiers
          if (element instanceof PsiModifierList) {
            element = element.getParent();
            continue;
          }

          final int offset = element.getTextOffset();
          if (!DocumentUtil.isValidOffset(offset, document) || document.getLineNumber(offset) != line) {
            break;
          }
          parent = element;
          element = element.getParent();
        }

        if (processor.fun(parent, document)) {
          res.set(true);
          return false;
        }
        return true;
      });
      return res.get();
    }
    return false;
  }

  @Override
  public List<? extends AnAction> getAdditionalPopupMenuActions(@NotNull XLineBreakpoint<P> breakpoint,
                                                                @Nullable XDebugSession currentSession) {
    return BreakpointIntentionAction.getIntentions(breakpoint, currentSession);
  }
}

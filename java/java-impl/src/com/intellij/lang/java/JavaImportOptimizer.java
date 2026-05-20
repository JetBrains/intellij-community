// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportList;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance(JavaImportOptimizer.class);

  @Override
  public @NotNull Runnable processFile(@NotNull PsiFile file) {
    if (!(file instanceof PsiJavaFile javaFile) || !CodeStyle.isFormattingEnabled(file)) {
      return EmptyRunnable.getInstance();
    }
    Project project = file.getProject();
    //it is from a dummy file, so can survive reparsing the main file
    final PsiImportList newImportList = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult(javaFile);
    if (newImportList == null) return EmptyRunnable.getInstance();

    record ImportContext(int importsAdded, int importsRemoved) {
    }

    return new CollectingInfoRunnable() {
      private @Nullable ImportContext myImportContext;

      @Override
      public void run() {
        //ModCommandExecutor can be used only in dispatch thread without write access
        if (ApplicationManager.getApplication().isWriteAccessAllowed() ||
            !ApplicationManager.getApplication().isDispatchThread()) {
          ImportContext importContext = modifyImportList(javaFile, newImportList);
          if (importContext == null) return;
          myImportContext = importContext;
          return;
        }
        ActionContext ctx = ActionContext.from(null, file);
        Ref<ImportContext> ref = new Ref<>();
        ModCommandExecutor.executeInteractively(
          ctx, QuickFixBundle.message("add.import"), null,
          () -> ModCommand.psiUpdate(javaFile, (e, _) -> {
            ref.set(modifyImportList(e, newImportList));
          }));
        ImportContext importContext = ref.get();
        if (importContext == null) return;
        myImportContext = importContext;
      }

      private static @Nullable ImportContext modifyImportList(@NotNull PsiJavaFile javaFile,
                                                              @NotNull PsiImportList newImportList) {
        try {
          PsiDocumentManager.getInstance(javaFile.getProject()).commitDocument(javaFile.getFileDocument());
          final PsiImportList oldImportList = javaFile.getImportList();
          assert oldImportList != null;
          if (oldImportList instanceof JspxImportList) {
            oldImportList.replace(newImportList);
          }
          else {
            oldImportList.getParent()
              .addRangeAfter(newImportList.getParent().getFirstChild(), newImportList.getParent().getLastChild(), oldImportList);
            oldImportList.delete();
          }
          return new ImportContext(ImportHelper.getImportsAdded(newImportList), ImportHelper.getImportsRemoved(newImportList));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return null;
      }

      @Override
      public String getUserNotificationInfo() {
        if (myImportContext == null) {
          return null;
        }
        if (myImportContext.importsRemoved == 0) {
          return JavaBundle.message("hint.text.rearranged.imports");
        }
        String notification =
          JavaBundle.message("hint.text.removed.imports", myImportContext.importsRemoved, myImportContext.importsRemoved == 1 ? 0 : 1);
        if (myImportContext.importsAdded > 0) {
          notification +=
            JavaBundle.message("hint.text.added.imports", myImportContext.importsAdded, myImportContext.importsAdded == 1 ? 0 : 1);
        }
        return notification;
      }
    };
  }

  @Override
  public boolean supports(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile && !(file instanceof JspFile) && !TemplateLanguageUtil.isTemplateDataFile(file)) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
      return virtualFile != null && (ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInSource(virtualFile) ||
                                     virtualFile instanceof LightVirtualFile ||
                                     ScratchUtil.isScratch(virtualFile));
    }
    return false;
  }

  /**
   * Optimize Imports for Java may schedule auto-imports for unresolved references on EDT under the same write action
   * (see {@code OptimizeImportsProcessor#fixAllImportsSilently}). The auto-import quickfix can do a long
   * {@code advancedResolve}, which would otherwise freeze the EDT. Ask the platform to run the whole write action
   * under a cancellable progress dialog instead.
   */
  @Override
  public @NotNull ImportOptimizer.ActionMode getActionMode() {
    return Registry.is("java.import.with.write.action", true)
           ? ImportOptimizer.ActionMode.WRITE_COMMAND_ACTION
           : ImportOptimizer.ActionMode.EDT;
  }
}

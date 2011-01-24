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

package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.RenameFileFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceQuickFixProvider {
  private FileReferenceQuickFixProvider() {}

  public static List<? extends LocalQuickFix> registerQuickFix(final HighlightInfo info, final FileReference reference) {
    final FileReferenceSet fileReferenceSet = reference.getFileReferenceSet();
    int index = reference.getIndex();

    if (index < 0) return Collections.emptyList();
    final String newFileName = reference.getCanonicalText();

    // check if we could create file
    if (newFileName.length() == 0 ||
        newFileName.indexOf('\\') != -1 ||
        newFileName.indexOf('*') != -1 ||
        newFileName.indexOf('?') != -1 ||
        SystemInfo.isWindows && newFileName.indexOf(':') != -1) {
      return Collections.emptyList();
    }

    PsiFileSystemItem context = null;
    if(index > 0) {
      context = fileReferenceSet.getReference(index - 1).resolve();
    } else { // index == 0
      final Collection<PsiFileSystemItem> defaultContexts = fileReferenceSet.getDefaultContexts();
      if (defaultContexts.isEmpty()) {
        return Collections.emptyList();
      }

      PsiElement element = reference.getElement();
      Module module = element != null ? ModuleUtil.findModuleForPsiElement(element) : null;

      for (PsiFileSystemItem defaultContext : defaultContexts) {
        if (defaultContext != null) {
          final VirtualFile virtualFile = defaultContext.getVirtualFile();
          if (virtualFile != null && defaultContext.isDirectory() && virtualFile.isInLocalFileSystem()) {
            if (context == null) {
              context = defaultContext;
            }
            else if (module != null && module == getModuleForContext(defaultContext)) {
              context = defaultContext;
              break;
            }
          }
        }
      }
      if (context == null && ApplicationManager.getApplication().isUnitTestMode()) {
        context = defaultContexts.iterator().next();
      }
    }
    if (context == null) return Collections.emptyList();

    final VirtualFile virtualFile = context.getVirtualFile();
    if (virtualFile == null) return Collections.emptyList();
    
    final PsiDirectory directory = context.getManager().findDirectory(virtualFile);
    if (directory == null) return Collections.emptyList();

    if (fileReferenceSet.isCaseSensitive()) {
      final PsiElement psiElement = reference.innerSingleResolve(false);

      if (psiElement instanceof PsiNamedElement) {
        final String existingElementName = ((PsiNamedElement)psiElement).getName();

        final RenameFileReferenceIntentionAction renameRefAction = new RenameFileReferenceIntentionAction(existingElementName, reference);
        QuickFixAction.registerQuickFixAction(info, renameRefAction);

        final RenameFileFix renameFileFix = new RenameFileFix(newFileName);
        QuickFixAction.registerQuickFixAction(info, renameFileFix);
        return Arrays.asList(renameRefAction, renameFileFix);
      }
    }

    final boolean isdirectory;

    if (!reference.isLast()) {
      // directory
      try {
        directory.checkCreateSubdirectory(newFileName);
      } catch(IncorrectOperationException ex) {
        return Collections.emptyList();
      }
      isdirectory = true;
    } else {
      FileType ft = FileTypeManager.getInstance().getFileTypeByFileName(newFileName);
      if (ft instanceof UnknownFileType) return Collections.emptyList();

      try {
        directory.checkCreateFile(newFileName);
      } catch(IncorrectOperationException ex) {
        return Collections.emptyList();
      }

      isdirectory = false;
    }

    final CreateFileFix action = new CreateFileFix(isdirectory, newFileName, directory);
    QuickFixAction.registerQuickFixAction(info, action);
    return Arrays.asList(action);
  }


  @Nullable
  private static Module getModuleForContext(@NotNull PsiFileSystemItem context) {
    VirtualFile file = context.getVirtualFile();
    return file != null ? ModuleUtil.findModuleForFile(file, context.getProject()) : null;
  }
}

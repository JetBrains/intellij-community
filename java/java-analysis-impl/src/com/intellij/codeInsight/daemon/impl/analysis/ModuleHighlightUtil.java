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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.GoToSymbolFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveFileFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

public class ModuleHighlightUtil {
  @Nullable
  static HighlightInfo checkFileName(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    if (!MODULE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorMessages.message("module.file.wrong.name");
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).description(message).create();
      QuickFixAction.registerQuickFixAction(info, factory().createRenameFileFix(MODULE_INFO_FILE));
      return info;
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkModuleDuplicates(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    String name = element.getModuleName();
    Project project = file.getProject();
    Collection<PsiJavaModule> others = JavaModuleNameIndex.getInstance().get(name, project, ProjectScope.getAllScope(project));
    if (others.size() > 1) {
      String message = JavaErrorMessages.message("module.name.duplicate", name);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element.getNameElement()).description(message).create();
      others.stream().filter(m -> m != element).findFirst().ifPresent(
        duplicate -> QuickFixAction.registerQuickFixAction(info, new GoToSymbolFix(duplicate, JavaErrorMessages.message("module.open.duplicate.text")))
      );
      return info;
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkFileDuplicates(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      Project project = file.getProject();
      Collection<VirtualFile> others =
        FilenameIndex.getVirtualFilesByName(project, MODULE_INFO_FILE, new ModulesScope(Collections.singleton(module), project));
      if (others.size() > 1) {
        String message = JavaErrorMessages.message("module.file.duplicate");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(element)).description(message).create();
        others.stream().map(f -> PsiManager.getInstance(project).findFile(f)).filter(f -> f != file).findFirst().ifPresent(
          duplicate -> QuickFixAction.registerQuickFixAction(info, new GoToSymbolFix(duplicate, JavaErrorMessages.message("module.open.duplicate.text")))
        );
        return info;
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkFileLocation(@NotNull PsiJavaModule element, @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null) {
      VirtualFile root = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getSourceRootForFile(vFile);
      if (root != null && !root.equals(vFile.getParent())) {
        String message = JavaErrorMessages.message("module.file.wrong.location");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(range(element)).description(message).create();
        QuickFixAction.registerQuickFixAction(info, new MoveFileFix(vFile, root, QuickFixBundle.message("move.file.to.source.root.text")));
        return info;
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkModuleReference(@Nullable PsiJavaModuleReferenceElement refElement) {
    if (refElement != null) {
      PsiPolyVariantReference ref = refElement.getReference();
      assert ref != null : refElement.getParent();
      if (ref.multiResolve(false).length == 0) {
        String message = JavaErrorMessages.message("module.ref.unknown", refElement.getReferenceText());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refElement).description(message).create();
      }
    }

    return null;
  }

  private static QuickFixFactory factory() {
    return QuickFixFactory.getInstance();
  }

  private static TextRange range(PsiJavaModule module) {
    return new TextRange(module.getTextOffset(), module.getNameElement().getTextRange().getEndOffset());
  }
}
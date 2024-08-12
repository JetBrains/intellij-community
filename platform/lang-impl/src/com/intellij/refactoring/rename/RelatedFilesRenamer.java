// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * According to {@link NestingTreeStructureProvider} some files in the Project View are shown as
 * children of another peer file. When going to rename such 'parent' file {@link RelatedFilesRenamer}
 * suggests to rename child files as well. Example: when renaming foo.ts file user is suggested to rename generated foo.js and foo.js.map
 * files as well.
 */
public class RelatedFilesRenamer extends AutomaticRenamer {
  private static final Logger LOG = Logger.getInstance(RelatedFilesRenamer.class.getName());

  public RelatedFilesRenamer(final @NotNull PsiFile psiFile, final @NotNull String newName) {
    final Collection<NestingTreeStructureProvider.ChildFileInfo> relatedFileInfos =
      NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(psiFile.getProject(), psiFile.getVirtualFile());

    for (NestingTreeStructureProvider.ChildFileInfo info : relatedFileInfos) {
      final PsiFile relatedPsiFile = psiFile.getManager().findFile(info.file());
      if (relatedPsiFile == null) continue;

      LOG.assertTrue(psiFile.getName().startsWith(info.namePartCommonWithParentFile()) &&
                     relatedPsiFile.getName().startsWith(info.namePartCommonWithParentFile()),
                     psiFile.getName() + "," + relatedPsiFile.getName() + "," + info.namePartCommonWithParentFile());

      final String suffix = psiFile.getName().substring(info.namePartCommonWithParentFile().length());
      LOG.assertTrue(suffix.length() > 0, psiFile.getName() + "," + info.namePartCommonWithParentFile());

      if (!newName.endsWith(suffix)) {
        // if suffix touched we can't suggest any reasonable new name for the related file
        continue;
      }

      final String newRelatedFileName = newName.substring(0, newName.length() - suffix.length()) +
                                        relatedPsiFile.getName().substring(info.namePartCommonWithParentFile().length());
      myElements.add(relatedPsiFile);
      suggestAllNames(relatedPsiFile.getName(), newRelatedFileName);
    }
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  @Override
  public String getDialogTitle() {
    return RefactoringBundle.message("rename.title");
  }

  @Override
  public String getDialogDescription() {
    return RefactoringBundle.message("rename.related.file.dialog.description");
  }

  @Override
  public String entityName() {
    return RefactoringBundle.message("related.file");
  }
}



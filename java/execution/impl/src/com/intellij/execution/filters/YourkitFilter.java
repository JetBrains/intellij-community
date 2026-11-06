// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementRenderingInfo;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YourkitFilter implements Filter{
  private static final Logger LOG = Logger.getInstance(YourkitFilter.class);

  private final Project myProject;


  private static final Pattern PATTERN = Pattern.compile("\\s*(\\w*)\\(\\):(-?\\d*), (\\w*\\.java)\\n");

  public YourkitFilter(final @NotNull Project project) {
    myProject = project;
  }

  @Override
  public Result applyFilter(final @NotNull String line, final int entireLength) {
    if (!line.endsWith(".java\n")) {
      return null;
    }

    try {
      final Matcher matcher = PATTERN.matcher(line);
      if (matcher.matches()) {
        final String method = matcher.group(1);
        final int lineNumber = Integer.parseInt(matcher.group(2));
        final String fileName = matcher.group(3);

        final int textStartOffset = entireLength - line.length();

        final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myProject);
        final PsiFile[] psiFiles = cache.getFilesByName(fileName);

        if (psiFiles.length == 0) return null;


        final HyperlinkInfo info = psiFiles.length == 1 ?
                                   new OpenFileHyperlinkInfo(myProject, psiFiles[0].getVirtualFile(), lineNumber - 1) :
                                   new MyHyperlinkInfo(psiFiles);

        return new Result(textStartOffset + matcher.start(2), textStartOffset + matcher.end(3), info);
      }
    }
    catch (NumberFormatException e) {
      LOG.debug(e);
    }

    return null;
  }

  private static class MyHyperlinkInfo implements HyperlinkInfo {
    private final PsiFile[] myPsiFiles;

    MyHyperlinkInfo(final PsiFile[] psiFiles) {
      myPsiFiles = psiFiles;
    }

    @Override
    public void navigate(@NotNull Project project) {
      final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
      if (editor != null) {
        new PsiTargetNavigator<>(myPsiFiles)
          .presentationProvider(file -> PsiElementRenderingInfo.targetPresentation(file, new PsiElementRenderingInfo<>() {
            @Override
            public @NotNull String getPresentableText(@NotNull PsiFile element) {
              return element.getName();
            }

            @Override
            public @Nullable String getContainerText(@NotNull PsiFile element) {
              PsiDirectory parent = element.getParent();
              PsiPackage psiPackage = parent == null ? null : JavaDirectoryService.getInstance().getPackage(parent);
              return psiPackage == null ? null : psiPackage.getQualifiedName();
            }
          }))
          .navigate(editor, ExecutionBundle.message("choose.file"), element -> {
            Navigatable descriptor = EditSourceUtil.getDescriptor(element);
            if (descriptor != null && descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
            return true;
          });
      }
    }
  }
}

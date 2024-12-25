// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
      DefaultPsiElementListCellRenderer renderer = new DefaultPsiElementListCellRenderer();
      final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
      if (editor != null) {
        final IPopupChooserBuilder<PsiFile> builder = JBPopupFactory.getInstance()
          .createPopupChooserBuilder(List.of(myPsiFiles))
          .setRenderer(renderer)
          .setTitle(ExecutionBundle.message("choose.file"))
          .setItemsChosenCallback((selectedElements) -> {
            for (PsiFile element : selectedElements) {
              Navigatable descriptor = EditSourceUtil.getDescriptor(element);
              if (descriptor != null && descriptor.canNavigate()) {
                descriptor.navigate(true);
              }
            }
          });
        renderer.installSpeedSearch(builder);
        builder.createPopup().showInBestPositionFor(editor);
      }
    }
  }


  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer<PsiElement> {
    @Override
    public String getElementText(final PsiElement element) {
      return element.getContainingFile().getName();
    }

    @Override
    protected @Nullable String getContainerText(final PsiElement element, final String name) {
      final PsiDirectory parent = ((PsiFile)element).getParent();
      if (parent == null) return null;
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(parent);
      if (psiPackage == null) return null;
      return "(" + psiPackage.getQualifiedName() + ")";
    }
  }
}

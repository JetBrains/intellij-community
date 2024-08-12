// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Handles tooltip links in format {@code #inspection/inspection_short_name}.
 * On a click or expend acton returns more detailed description for given inspection.
 */
public final class InspectionDescriptionLinkHandler extends TooltipLinkHandler {
  private static final Logger LOG = Logger.getInstance(InspectionDescriptionLinkHandler.class);

  @Override
  public String getDescription(final @NotNull String refSuffix, final @NotNull Editor editor) {
    final Project project = editor.getProject();
    if (project == null) {
      LOG.error("Project is null for " + editor);
      return null;
    }
    if (project.isDisposed()) return null;
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return null;
    }

    final InspectionProfile profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper toolWrapper = profile.getInspectionTool(refSuffix, file);
    if (toolWrapper == null) return null;

    String description = toolWrapper.loadDescription();
    if (description == null) {
      LOG.warn("No description for inspection '" + refSuffix + "'");
      description = InspectionsBundle.message("inspection.tool.description.under.construction.text");
    }
    return description;
  }
}

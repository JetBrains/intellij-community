// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

public class CodeCleanupCodeProcessor extends AbstractLayoutCodeProcessor {
  public static final String COMMAND_NAME = "Cleanup code";
  public static final String PROGRESS_TEXT = CodeInsightBundle.message("process.cleanup.code");

  private static final Logger LOG = Logger.getInstance(CodeCleanupCodeProcessor.class);
  private SelectionModel mySelectionModel = null;

  public CodeCleanupCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor) {
    super(previousProcessor, COMMAND_NAME, PROGRESS_TEXT);
  }

  public CodeCleanupCodeProcessor(@NotNull AbstractLayoutCodeProcessor previousProcessor, @NotNull SelectionModel selectionModel) {
    super(previousProcessor, COMMAND_NAME, PROGRESS_TEXT);
    mySelectionModel = selectionModel;
  }


  @NotNull
  @Override
  protected FutureTask<Boolean> prepareTask(@NotNull final PsiFile file, final boolean processChangedTextOnly) {
    return new FutureTask<>(() -> {
      LOG.debug("Running " + COMMAND_NAME);
      InspectionProfileImpl profileToUse = ProjectInspectionProfileManager.getInstance(myProject).getCurrentProfile();
      InspectionProfileWrapper profileWrapper = new InspectionProfileWrapper(profileToUse);
      InspectionToolWrapper[] tools = profileWrapper.getInspectionProfile().getInspectionTools(file);

      List<LocalInspectionToolWrapper> cleanupTools = StreamEx.of(tools)
        .filter(InspectionToolWrapper::isCleanupTool)
        .select(LocalInspectionToolWrapper.class)
        .collect(Collectors.toList());


      Map<String, List<ProblemDescriptor>> inspectionResults =
        InspectionEngine.inspectEx(cleanupTools, file, InspectionManager.getInstance(myProject), false, new EmptyProgressIndicator());

      Collection<TextRange> ranges = getRanges(file, processChangedTextOnly);
      // TODO Should it be interval tree?

      for (List<ProblemDescriptor> descriptors : inspectionResults.values()) {
        for (ProblemDescriptor descriptor : descriptors) {
          if (!isInRanges(ranges, descriptor)) continue;

          QuickFix[] fixes = descriptor.getFixes();
          if (fixes != null) {
            for (QuickFix fix : fixes) {
              fix.applyFix(myProject, descriptor);
            }
          }
        }
      }
      return true;
    });
  }

  public Collection<TextRange> getRanges(@NotNull PsiFile file, boolean processChangedTextOnly) {
    if (mySelectionModel != null) {
      return getSelectedRanges(mySelectionModel);
    }

    if (processChangedTextOnly) {
      return FormatChangedTextUtil.getInstance().getChangedTextRanges(myProject, file);
    }

    return ContainerUtil.newSmartList(file.getTextRange());
  }

  private static boolean isInRanges(Collection<TextRange> ranges, @NotNull ProblemDescriptor descriptor) {
    for (TextRange range : ranges) {
      if (range.containsOffset(descriptor.getStartElement().getTextOffset())
          || range.containsOffset(descriptor.getEndElement().getTextOffset())) {
        return true;
      }
    }
    return false;
  }
}

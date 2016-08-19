/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class DefaultHighlightVisitorBasedInspection extends GlobalSimpleInspectionTool {
  private final boolean highlightErrorElements;
  private final boolean runAnnotators;

  public DefaultHighlightVisitorBasedInspection(boolean highlightErrorElements, boolean runAnnotators) {
    this.highlightErrorElements = highlightErrorElements;
    this.runAnnotators = runAnnotators;
  }

  public static class AnnotatorBasedInspection extends DefaultHighlightVisitorBasedInspection {
    private static final String ANNOTATOR_SHORT_NAME = "Annotator";

    public AnnotatorBasedInspection() {
      super(false, true);
    }
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return getShortName();
    }

    @NotNull
    @Override
    public String getShortName() {
      return ANNOTATOR_SHORT_NAME;
    }

  }
  public static class SyntaxErrorInspection extends DefaultHighlightVisitorBasedInspection {
    public SyntaxErrorInspection() {
      super(true, false);
    }
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
      return "Syntax error";
    }

    @NotNull
    @Override
    public String getShortName() {
      return "SyntaxError";
    }
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void checkFile(@NotNull PsiFile originalFile,
                        @NotNull final InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull final GlobalInspectionContext globalContext,
                        @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    for (Pair<PsiFile, HighlightInfo> pair : runGeneralHighlighting(originalFile, highlightErrorElements, runAnnotators,
                                                                    problemsHolder.isOnTheFly())) {
      PsiFile file = pair.first;
      HighlightInfo info = pair.second;
      TextRange range = new TextRange(info.startOffset, info.endOffset);
      PsiElement element = file.findElementAt(info.startOffset);

      while (element != null && !element.getTextRange().contains(range)) {
        element = element.getParent();
      }

      if (element == null) {
        element = file;
      }

      GlobalInspectionUtil.createProblem(
        element,
        info,
        range.shiftRight(-element.getNode().getStartOffset()),
        info.getProblemGroup(),
        manager,
        problemDescriptionsProcessor,
        globalContext
      );

    }
  }

  public static List<Pair<PsiFile,HighlightInfo>> runGeneralHighlighting(PsiFile file,
                                            final boolean highlightErrorElements,
                                            final boolean runAnnotators, boolean isOnTheFly) {
    MyPsiElementVisitor visitor = new MyPsiElementVisitor(highlightErrorElements, runAnnotators, isOnTheFly);
    file.accept(visitor);
    return new ArrayList<>(visitor.result);
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  private static class MyPsiElementVisitor extends PsiElementVisitor {
    private final boolean highlightErrorElements;
    private final boolean runAnnotators;
    private final List<Pair<PsiFile, HighlightInfo>> result = new ArrayList<>();

    public MyPsiElementVisitor(boolean highlightErrorElements, boolean runAnnotators, boolean isOnTheFly) {
      this.highlightErrorElements = highlightErrorElements;
      this.runAnnotators = runAnnotators;
    }

    @Override
    public void visitFile(final PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) {
        return;
      }

      final Project project = file.getProject();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return;
      DaemonProgressIndicator progress = new DaemonProgressIndicator();
      progress.start();
      try {
        TextEditorHighlightingPassRegistrarEx passRegistrarEx = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project);
        List<TextEditorHighlightingPass> passes = passRegistrarEx.instantiateMainPasses(file, document, HighlightInfoProcessor.getEmpty());
        List<GeneralHighlightingPass> gpasses = ContainerUtil.collect(passes.iterator(), FilteringIterator.instanceOf(GeneralHighlightingPass.class));
        for (final GeneralHighlightingPass gpass : gpasses) {
          gpass.setHighlightVisitorProducer(() -> {
            gpass.incVisitorUsageCount(1);

            HighlightVisitor visitor = new DefaultHighlightVisitor(project, highlightErrorElements, runAnnotators, true,
                                                                          ServiceManager.getService(project, CachedAnnotators.class));
            return new HighlightVisitor[]{visitor};
          });
        }


        for (TextEditorHighlightingPass pass : gpasses) {
          pass.doCollectInformation(progress);
          List<HighlightInfo> infos = pass.getInfos();
          for (HighlightInfo info : infos) {
            if (info == null) continue;
            //if (info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT) continue;
            if (info.getSeverity().compareTo(HighlightSeverity.INFORMATION) <= 0) continue;
            result.add(Pair.create(file, info));
          }
        }
      }
      finally {
        progress.stop();
      }
    }
  }
}

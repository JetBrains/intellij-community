// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.codeInsight.actions.FormatChangedTextUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uast.UastMetaLanguage;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UFile;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ShowDiscoveredTestsFromChangesAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ShowDiscoveredTestsAction.isEnabledForProject(e) && e.getData(VcsDataKeys.CHANGES) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    Project project = e.getProject();
    assert project != null;
    UastMetaLanguage jvmLanguage = Language.findInstance(UastMetaLanguage.class);


    List<PsiElement> methods = FormatChangedTextUtil.getInstance().getChangedElements(project, changes, file -> {
      PsiFile psiFile = PsiUtilCore.getPsiFile(project, file);
      if (!jvmLanguage.matchesLanguage(psiFile.getLanguage())) {
        return null;
      }
      Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) return null;
      UFile uFile = UastContextKt.toUElement(psiFile, UFile.class);
      if (uFile == null) return null;


      PsiDocumentManager.getInstance(project).commitDocument(document);
      List<PsiElement> physicalMethods = new ArrayList<>();
      uFile.accept(new AbstractUastVisitor() {
        @Override
        public boolean visitMethod(@NotNull UMethod node) {
          physicalMethods.add(node.getSourcePsi());
          return true;
        }
      });

      return physicalMethods;
    });

    PsiMethod[] asJavaMethods = methods
      .stream()
      .map(m -> ObjectUtils.tryCast(Objects.requireNonNull(UastContextKt.toUElement(m)).getJavaPsi(), PsiMethod.class))
      .filter(Objects::nonNull)
      .toArray(PsiMethod.ARRAY_FACTORY::create);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("test.discovery.selected.changes");
    ShowDiscoveredTestsAction.showDiscoveredTests(project, e.getDataContext(), "Selected Changes", asJavaMethods);
  }
}

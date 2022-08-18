// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.find.FindBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UastContextKt;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

class GenerateTestDataPathCommon extends BaseGenerateAction {

  @NonNls
  private static final String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  @NonNls
  private static final String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";
  @NonNls
  protected static final String ANNOTATION_FQN = "com.intellij.testFramework.TestDataPath";
  protected static final Logger LOG = Logger.getInstance(GenerateTestDataPathCommon.class);

  GenerateTestDataPathCommon(CodeInsightActionHandler handler) {
    super(handler);
  }

  @Nullable
  protected static String annotationValue(@NotNull PsiModifierListOwner owner, String annotationFqName) {
    var annotationNames = Collections.singleton(annotationFqName);
    var nestedClass = owner instanceof PsiClass && ((PsiClass)owner).getContainingClass() != null;
    var element = nestedClass
                  ? AnnotationUtil.findAnnotation(owner, annotationNames)
                  : AnnotationUtil.findAnnotationInHierarchy(owner, annotationNames);
    final var annotation = UastContextKt.toUElement(element, UAnnotation.class);
    if (annotation != null) {
      var value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value != null) {
        final var project = owner.getProject();
        final var constantValue = value.evaluate();
        if (constantValue instanceof String) {
          var path = (String)constantValue;
          if (path.contains(CONTENT_ROOT_VARIABLE)) {
            final var fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            final var file = owner.getContainingFile().getVirtualFile();
            if (file == null) return null;
            final var contentRoot = fileIndex.getContentRootForFile(file);
            if (contentRoot == null) return null;
            path = path.replace(CONTENT_ROOT_VARIABLE, contentRoot.getPath());
          }
          if (path.contains(PROJECT_ROOT_VARIABLE)) {
            var baseDir = project.getBasePath();
            if (baseDir == null) {
              return null;
            }
            path = path.replace(PROJECT_ROOT_VARIABLE, baseDir);
          }
          return path;
        }
      }
    }
    return null;
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
      return AnnotationUtil.findAnnotation(targetClass, "com.intellij.testFramework.TestDataPath") != null;
  }

  protected static abstract class TestDataPathDialog extends DialogWrapper {
    protected TestDataPathDialog(@Nullable Project project, @NlsSafe @NotNull String titleText, boolean canBeParent) {
      super(project, canBeParent);
      setTitle(titleText);
      setOKButtonText(JavaBundle.message("generate.button.title"));
      init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      var mainPanel = createMainPanel();
      getPanelContent().forEach(mainPanel::add);
      return mainPanel;
    }

    abstract protected List<Component> getPanelContent();

    protected JBPanel createMainPanel() {
      var mainPanel = new JBPanel<>().withPreferredWidth(250);
      var layout = new BoxLayout(mainPanel, BoxLayout.Y_AXIS);
      mainPanel.setLayout(layout);
      return mainPanel;
    }
  }
}

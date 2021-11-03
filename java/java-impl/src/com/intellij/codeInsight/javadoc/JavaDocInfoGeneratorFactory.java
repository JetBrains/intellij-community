// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaDocInfoGeneratorFactory {

  public static JavaDocInfoGeneratorFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaDocInfoGeneratorFactory.class);
  }

  protected JavaDocInfoGenerator createImpl(@NotNull Project project, @Nullable PsiElement element) {
    return new JavaDocInfoGenerator(
      project,
      element,
      JavaDocHighlightingManagerImpl.getInstance(),
      false,
      DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(),
      DocumentationSettings.isHighlightingOfCodeBlocksEnabled(),
      DocumentationSettings.getInlineCodeHighlightingMode(),
      DocumentationSettings.isSemanticHighlightingOfLinksEnabled(),
      DocumentationSettings.getHighlightingSaturation(false));
  }

  @NotNull
  public static JavaDocInfoGenerator create(@NotNull Project project, @Nullable PsiElement element) {
    return getInstance().createImpl(project, element);
  }

  public static @NotNull JavaDocInfoGeneratorBuilder getBuilder(@NotNull Project project) {
    return new JavaDocInfoGeneratorBuilder(project);
  }


  public static final class JavaDocInfoGeneratorBuilder {
    private final @NotNull Project myProject;
    private @Nullable PsiElement myElement;
    private @NotNull JavaDocHighlightingManager myManager = new JavaDocHighlightingManagerImpl();
    private boolean myIsRendered = false;
    private boolean myDoHighlightSignatures = DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled();
    private boolean myDoHighlightCodeBlocks = DocumentationSettings.isHighlightingOfCodeBlocksEnabled();
    private @NotNull InlineCodeHighlightingMode myInlineCodeBlocksHighlightingMode = DocumentationSettings.getInlineCodeHighlightingMode();
    private boolean myDoSemanticHighlightingOfLinks = DocumentationSettings.isSemanticHighlightingOfLinksEnabled();
    private float myHighlightingSaturation = DocumentationSettings.getHighlightingSaturation(false);

    private JavaDocInfoGeneratorBuilder(@NotNull Project project) {
      myProject = project;
    }

    public JavaDocInfoGeneratorBuilder setPsiElement(@Nullable PsiElement element) {
      myElement = element;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setHighlightingManager(@NotNull JavaDocHighlightingManager manager) {
      myManager = manager;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setIsGenerationForRenderedDoc(boolean isRendered) {
      myIsRendered = isRendered;
      myHighlightingSaturation = DocumentationSettings.getHighlightingSaturation(isRendered);
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoHighlightSignatures(boolean doHighlighting) {
      myDoHighlightSignatures = doHighlighting;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoHighlightCodeBlocks(boolean doHighlighting) {
      myDoHighlightCodeBlocks = doHighlighting;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoInlineCodeHighlightingMode(@NotNull InlineCodeHighlightingMode mode) {
      myInlineCodeBlocksHighlightingMode = mode;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoSemanticHighlightingOfLinks(boolean doHighlightLinks) {
      myDoSemanticHighlightingOfLinks = doHighlightLinks;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setHighlightingSaturationFactor(float saturationFactor) {
      myHighlightingSaturation = saturationFactor;
      return this;
    }

    public JavaDocInfoGenerator create() {
      return new JavaDocInfoGenerator(
        myProject,
        myElement,
        myManager,
        myIsRendered,
        myDoHighlightSignatures,
        myDoHighlightCodeBlocks,
        myInlineCodeBlocksHighlightingMode,
        myDoSemanticHighlightingOfLinks,
        myHighlightingSaturation);
    }
  }
}

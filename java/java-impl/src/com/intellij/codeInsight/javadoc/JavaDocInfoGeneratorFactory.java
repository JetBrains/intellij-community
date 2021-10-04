// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaDocInfoGeneratorFactory {

  public static JavaDocInfoGeneratorFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaDocInfoGeneratorFactory.class);
  }

  protected JavaDocInfoGenerator createImpl(@NotNull Project project, @Nullable PsiElement element) {
    EditorSettingsExternalizable.getInstance();
    EditorSettingsExternalizable.getInstance();
    EditorSettingsExternalizable.getInstance();
    return new JavaDocInfoGenerator(
      project,
      element,
      JavaDocHighlightingManagerImpl.getInstance(),
      false,
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting"),
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting.of.inline.code.blocks"),
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting.of.links"));
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
    private boolean myDoHighlighting =
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting");
    private boolean myDoHighlightBlocks =
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting.of.inline.code.blocks");
    private boolean myDoHighlightLinks =
      AdvancedSettings.getBoolean("documentation.components.enable.doc.syntax.highlighting.of.inline.code.blocks");

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
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoSyntaxHighlighting(boolean doHighlighting) {
      myDoHighlighting = doHighlighting;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoHighlightInlineCodeBlocks(boolean doHighlightBlocks) {
      myDoHighlightBlocks = doHighlightBlocks;
      return this;
    }

    public JavaDocInfoGeneratorBuilder setDoHighlightLinks(boolean doHighlightLinks) {
      myDoHighlightLinks = doHighlightLinks;
      return this;
    }

    public JavaDocInfoGenerator create() {
      if (!myDoHighlighting) {
        myDoHighlightBlocks = false;
        myDoHighlightLinks = false;
      }
      return new JavaDocInfoGenerator(
        myProject,
        myElement,
        myManager,
        myIsRendered,
        myDoHighlighting,
        myDoHighlightBlocks,
        myDoHighlightLinks);
    }
  }
}

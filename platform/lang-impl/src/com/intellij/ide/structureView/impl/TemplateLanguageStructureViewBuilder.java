// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.StructureViewCompositeModel;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class TemplateLanguageStructureViewBuilder extends TreeBasedStructureViewBuilder {

  public static @NotNull TemplateLanguageStructureViewBuilder create(@NotNull PsiFile psiFile,
                                                                     @Nullable PairFunction<? super PsiFile, ? super Editor, ? extends StructureViewModel> modelFactory) {
    return new TemplateLanguageStructureViewBuilder(psiFile) {
      @Override
      protected TreeBasedStructureViewBuilder createMainBuilder(@NotNull PsiFile psi) {
        return modelFactory == null ? null : new TreeBasedStructureViewBuilder() {
          @Override
          public boolean isRootNodeShown() {
            return false;
          }

          @Override
          public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
            return modelFactory.fun(psi, editor);
          }
        };
      }
    };
  }

  private final VirtualFile myVirtualFile;
  private final Project myProject;

  private final Map<Language, StructureViewBuilder> myLanguageToBuilderMap = new LinkedHashMap<>();

  protected TemplateLanguageStructureViewBuilder(PsiElement psiElement) {
    myProject = psiElement.getProject();
    myVirtualFile = psiElement.getContainingFile().getVirtualFile();

    PsiFile file = psiElement.getContainingFile();
    if (file != null) {
      for (Language language : getLanguages(psiElement.getContainingFile())) {
        StructureViewBuilder builder = getBuilder(file, language);
        if (builder != null) {
          myLanguageToBuilderMap.put(language, builder);
        }
      }
    }
  }

  @Override
  public boolean isRootNodeShown() {
    return false;
  }

  @Override
  public @NotNull StructureView createStructureView(FileEditor fileEditor, @NotNull Project project) {
    List<StructureViewComposite.StructureViewDescriptor> viewDescriptors = new ArrayList<>();
    for (Language language : myLanguageToBuilderMap.keySet()) {
      StructureViewBuilder builder = myLanguageToBuilderMap.get(language);
      StructureView structureView = builder.createStructureView(fileEditor, project);
      String title = language.getDisplayName();
      Icon icon = ObjectUtils.notNull(LanguageUtil.getLanguageFileType(language), FileTypes.UNKNOWN).getIcon();
      viewDescriptors.add(new StructureViewComposite.StructureViewDescriptor(title, structureView, icon));
    }
    StructureViewComposite.StructureViewDescriptor[] array = viewDescriptors.toArray(new StructureViewComposite.StructureViewDescriptor[0]);
    return new StructureViewComposite(array) {
      @Override
      public boolean isOutdated() {
        VirtualFile file = fileEditor == null ? null : fileEditor.getFile();
        PsiFile psiFile = file == null || !file.isValid() ? null : PsiManager.getInstance(project).findFile(file);
        Set<Language> newLanguages = getLanguages(psiFile).toSet();
        // think views count depends only on acceptable languages
        return !Comparing.equal(myLanguageToBuilderMap.keySet(), newLanguages);
      }
    };
  }

  @Override
  public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
    List<StructureViewComposite.StructureViewDescriptor> viewDescriptors = new ArrayList<>();
    for (Language language : myLanguageToBuilderMap.keySet()) {
      StructureViewBuilder builder = myLanguageToBuilderMap.get(language);
      if (!(builder instanceof TreeBasedStructureViewBuilder)) continue;
      StructureViewModel model = ((TreeBasedStructureViewBuilder)builder).createStructureViewModel(editor);
      String title = language.getDisplayName();
      Icon icon = ObjectUtils.notNull(LanguageUtil.getLanguageFileType(language), FileTypes.UNKNOWN).getIcon();
      viewDescriptors.add(new StructureViewComposite.StructureViewDescriptor(title, model, icon));
    }
    PsiFile psiFile = Objects.requireNonNull(PsiManager.getInstance(myProject).findFile(myVirtualFile));
    return new StructureViewCompositeModel(psiFile, editor, viewDescriptors);
  }

  private @NotNull JBIterable<Language> getLanguages(@Nullable PsiFile psiFile) {
    if (psiFile == null) return JBIterable.empty();
    FileViewProvider viewProvider = psiFile.getViewProvider();

    Language baseLanguage = viewProvider.getBaseLanguage();
    Language dataLanguage = viewProvider instanceof TemplateLanguageFileViewProvider
                            ? ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage() : null;
    return JBIterable.of(baseLanguage)
      .append(dataLanguage)
      .append(viewProvider.getLanguages())
      .unique()
      .filter(language -> {
        PsiFile psi = viewProvider.getPsi(language);
        return psi != null && (language == baseLanguage || isAcceptableBaseLanguageFile(psi));
      });
  }

  private @Nullable StructureViewBuilder getBuilder(@NotNull PsiFile psiFile, @NotNull Language language) {
    FileViewProvider viewProvider = psiFile.getViewProvider();
    Language baseLanguage = viewProvider.getBaseLanguage();
    PsiFile psi = viewProvider.getPsi(language);
    if (psi == null) return null;
    if (language == baseLanguage) return createMainBuilder(psi);
    PsiStructureViewFactory factory = LanguageStructureViewBuilder.getInstance().forLanguage(language);
    return factory == null ? null : factory.getStructureViewBuilder(psi);
  }

  protected boolean isAcceptableBaseLanguageFile(PsiFile dataFile) {
    return true;
  }

  protected abstract @Nullable TreeBasedStructureViewBuilder createMainBuilder(@NotNull PsiFile psi);
}

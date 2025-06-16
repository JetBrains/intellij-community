// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.settings.RepositoryLibraryPropertiesDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibrarySupport;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

public class RepositoryAddLibraryAction extends IntentionAndQuickFixAction {
  private final Module module;
  private final @NotNull RepositoryLibraryDescription libraryDescription;

  public RepositoryAddLibraryAction(Module module, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.module = module;
    this.libraryDescription = libraryDescription;
  }

  @Override
  public @NotNull String getName() {
    return JavaUiBundle.message("intention.text.add.0.library.to.module.dependencies", libraryDescription.getDisplayName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaUiBundle.message("intention.family.maven.libraries");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor) {
    addLibraryToModule(libraryDescription, module);
  }

  public static Promise<Void> addLibraryToModule(@NotNull RepositoryLibraryDescription libraryDescription,
                                                 @NotNull Module module,
                                                 @NotNull String defaultVersion,
                                                 @Nullable DependencyScope  scope,
                                                 boolean downloadSources,
                                                 boolean downloadJavaDocs) {
    RepositoryLibraryPropertiesModel model = new RepositoryLibraryPropertiesModel(defaultVersion, downloadSources, downloadJavaDocs);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      RepositoryLibraryPropertiesDialog dialog = new RepositoryLibraryPropertiesDialog(
        module.getProject(),
        model,
        libraryDescription,
        false, true, false);
      if (!dialog.showAndGet()) {
        return Promises.rejectedPromise();
      }
    }
    IdeaModifiableModelsProvider modifiableModelsProvider = new IdeaModifiableModelsProvider();
    final ModifiableRootModel modifiableModel = modifiableModelsProvider.getModuleModifiableModel(module);
    RepositoryLibrarySupport librarySupport = new RepositoryLibrarySupport(module.getProject(), libraryDescription, model);
    assert modifiableModel != null;
    librarySupport.addSupport(
      module,
      modifiableModel,
      modifiableModelsProvider,
      scope);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
    return Promises.resolvedPromise(null);
  }

  public static Promise<Void> addLibraryToModule(@NotNull RepositoryLibraryDescription libraryDescription, @NotNull Module module) {
    return addLibraryToModule(libraryDescription, module, RepositoryLibraryDescription.DefaultVersionId, null, false, false);
  }
}

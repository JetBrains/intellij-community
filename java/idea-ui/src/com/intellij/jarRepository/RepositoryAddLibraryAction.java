// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.jarRepository.settings.RepositoryLibraryPropertiesDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
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
  @NotNull private final RepositoryLibraryDescription libraryDescription;

  public RepositoryAddLibraryAction(Module module, @NotNull RepositoryLibraryDescription libraryDescription) {
    this.module = module;
    this.libraryDescription = libraryDescription;
  }

  @NotNull
  @Override
  public String getName() {
    return "Add " + libraryDescription.getDisplayName() +" library to module dependencies";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Maven libraries";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor) {
    addLibraryToModule(libraryDescription, module);
  }

  public static Promise<Void> addLibraryToModule(RepositoryLibraryDescription libraryDescription, Module module) {
    RepositoryLibraryPropertiesModel model = new RepositoryLibraryPropertiesModel(
      RepositoryLibraryDescription.DefaultVersionId,
      false,
      false);
    RepositoryLibraryPropertiesDialog dialog = new RepositoryLibraryPropertiesDialog(
      module.getProject(),
      model,
      libraryDescription,
      false, true);
    if (!dialog.showAndGet()) {
      return Promises.rejectedPromise();
    }
    IdeaModifiableModelsProvider modifiableModelsProvider = new IdeaModifiableModelsProvider();
    final ModifiableRootModel modifiableModel = modifiableModelsProvider.getModuleModifiableModel(module);
    RepositoryLibrarySupport librarySupport = new RepositoryLibrarySupport(module.getProject(), libraryDescription, model);
    assert modifiableModel != null;
    librarySupport.addSupport(
      module,
      modifiableModel,
      modifiableModelsProvider);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
    return Promises.resolvedPromise(null);
  }
}

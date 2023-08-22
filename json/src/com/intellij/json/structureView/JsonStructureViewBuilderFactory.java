// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.structureView;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewBuilderFactory implements PsiStructureViewFactory {

  public JsonStructureViewBuilderFactory() {
    JsonCustomStructureViewFactory.EP_NAME.addChangeListener(
      () -> ApplicationManager.getApplication().getMessageBus().syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run(),
      ExtensionPointUtil.createKeyedExtensionDisposable(this, PsiStructureViewFactory.EP_NAME.getPoint()));
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) {
      return null;
    }

    List<JsonCustomStructureViewFactory> extensionList = JsonCustomStructureViewFactory.EP_NAME.getExtensionList();
    if (extensionList.size() > 1) {
      Logger.getInstance(JsonStructureViewBuilderFactory.class)
        .warn("Several extensions are registered for JsonCustomStructureViewFactory extension point. " +
              "Conflicts can arise if there are several builders corresponding to the same file.");
    }

    for (JsonCustomStructureViewFactory extension : extensionList) {
      final StructureViewBuilder builder = extension.getStructureViewBuilder((JsonFile)psiFile);
      if (builder != null) {
        return builder;
      }
    }

    return new TreeBasedStructureViewBuilder() {
      @NotNull
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new JsonStructureViewModel(psiFile, editor);
      }
    };
  }
}

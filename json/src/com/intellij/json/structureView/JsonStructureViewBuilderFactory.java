package com.intellij.json.structureView;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewBuilderFactory implements PsiStructureViewFactory {

  public JsonStructureViewBuilderFactory() {
    JsonCustomStructureViewFactory.EP_NAME.addExtensionPointListener(
      () -> ApplicationManager.getApplication().getMessageBus().syncPublisher(StructureViewWrapperImpl.STRUCTURE_CHANGED).run(),
      ExtensionPointUtil.createKeyedExtensionDisposable(this, PsiStructureViewFactory.EP_NAME.getPoint(null)));
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) {
      return null;
    }

    for (JsonCustomStructureViewFactory extension : JsonCustomStructureViewFactory.EP_NAME.getExtensionList()) {
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

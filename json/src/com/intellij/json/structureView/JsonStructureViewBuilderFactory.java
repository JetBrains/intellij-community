package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewBuilderFactory implements PsiStructureViewFactory {
  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) {
      return null;
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

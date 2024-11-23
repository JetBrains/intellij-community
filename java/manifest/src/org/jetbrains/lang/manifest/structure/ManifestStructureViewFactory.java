package org.jetbrains.lang.manifest.structure;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManifestStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      @Override
      public ManifestStructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new ManifestStructureViewModel(editor, psiFile);
      }
    };
  }
}

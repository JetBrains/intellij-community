package com.intellij.json.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.json.psi.JsonFile;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewFactory implements PsiStructureViewFactory {
  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(PsiFile psiFile) {
    return new JsonStructureViewBuilder(((JsonFile)psiFile));
  }
}

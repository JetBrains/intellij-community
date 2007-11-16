/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public interface PsiStructureViewFactory {
  @Nullable
  StructureViewBuilder getStructureViewBuilder(PsiFile psiFile);
}
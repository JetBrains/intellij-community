// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface DiffMultiDiffPart extends PsiElement {

  @NotNull
  DiffConsoleCommand getConsoleCommand();

  @NotNull
  List<DiffContextHunk> getContextHunkList();

  @NotNull
  List<DiffNormalHunk> getNormalHunkList();

  @NotNull
  List<DiffUnifiedHunk> getUnifiedHunkList();

}

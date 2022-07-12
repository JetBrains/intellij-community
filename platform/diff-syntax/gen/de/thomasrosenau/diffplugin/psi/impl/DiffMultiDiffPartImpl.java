// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.thomasrosenau.diffplugin.psi.DiffTypes.*;
import de.thomasrosenau.diffplugin.psi.DiffNavigationItem;
import de.thomasrosenau.diffplugin.psi.*;

public class DiffMultiDiffPartImpl extends DiffNavigationItem implements DiffMultiDiffPart {

  public DiffMultiDiffPartImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DiffVisitor visitor) {
    visitor.visitMultiDiffPart(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DiffVisitor) accept((DiffVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DiffConsoleCommand getConsoleCommand() {
    return findNotNullChildByClass(DiffConsoleCommand.class);
  }

  @Override
  @NotNull
  public List<DiffContextHunk> getContextHunkList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DiffContextHunk.class);
  }

  @Override
  @NotNull
  public List<DiffNormalHunk> getNormalHunkList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DiffNormalHunk.class);
  }

  @Override
  @NotNull
  public List<DiffUnifiedHunk> getUnifiedHunkList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, DiffUnifiedHunk.class);
  }

}

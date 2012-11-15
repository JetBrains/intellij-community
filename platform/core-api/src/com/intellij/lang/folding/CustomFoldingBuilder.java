package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds custom folding regions. If custom folding is supported for a language, its FoldingBuilder must be inherited from this class.
 * 
 * @author Rustam Vishnyakov
 */
public abstract class CustomFoldingBuilder extends FoldingBuilderEx implements DumbAware {

  private CustomFoldingProvider myDefaultProvider;
  private static final int MAX_LOOKUP_DEPTH = 10;

  @NotNull
  @Override
  public final FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    if (CustomFoldingProvider.getAllProviders().length > 0) {
      myDefaultProvider = null;
      addCustomFoldingRegionsRecursively(null, root.getNode(), descriptors, 0);
    }
    buildLanguageFoldRegions(descriptors, root, document, quick);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  @NotNull
  @Override
  public final FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    return buildFoldRegions(node.getPsi(), document, false);
  }

  /**
   * Implement this method to build language folding regions besides custom folding regions.
   *
   * @param descriptors The list of folding descriptors to store results to.
   * @param root        The root node for which the folding is requested.
   * @param document    The document for which folding is built.
   * @param quick       whether the result should be provided as soon as possible without reference resolving
   *                    and complex checks.
   */
  protected abstract void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                                   @NotNull PsiElement root,
                                                   @NotNull Document document,
                                                   boolean quick);

  private void addCustomFoldingRegionsRecursively(@Nullable FoldingStack foldingStack,
                                                  @NotNull ASTNode node,
                                                  @NotNull List<FoldingDescriptor> descriptors,
                                                  int currDepth) {
    FoldingStack localFoldingStack = isCustomFoldingRoot(node) || foldingStack == null ? new FoldingStack(node) : foldingStack;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (isCustomRegionStart(child)) {
        localFoldingStack.push(child);
      }
      else if (isCustomRegionEnd(child)) {
        if (!localFoldingStack.isEmpty()) {
          ASTNode startNode = localFoldingStack.pop();
          int startOffset = startNode.getTextRange().getStartOffset();
          TextRange range = new TextRange(startOffset, child.getTextRange().getEndOffset());
          descriptors.add(new FoldingDescriptor(startNode, range));
        }
      }
      else {
        if (currDepth < MAX_LOOKUP_DEPTH) {
          addCustomFoldingRegionsRecursively(localFoldingStack, child, descriptors, currDepth + 1);
        }
      }
    }
  }

  @Override
  public final String getPlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
    if (isCustomFoldingCandidate(node)) {
      String elementText = node.getText();
      CustomFoldingProvider defaultProvider = getDefaultProvider(elementText);
      if (defaultProvider != null && defaultProvider.isCustomRegionStart(elementText)) {
        return defaultProvider.getPlaceholderText(elementText);
      }
    }
    return getLanguagePlaceholderText(node, range);
  }
  
  protected abstract String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range);


  @Override
  public final String getPlaceholderText(@NotNull ASTNode node) {
    return "...";
  }


  @Override
  public final boolean isCollapsedByDefault(@NotNull ASTNode node) {
    // TODO<rv>: Modify Folding API and pass here folding range.
    if (isCustomFoldingRoot(node)) {
      for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
        if (isCustomRegionStart(child)) {
          String childText = child.getText();
          CustomFoldingProvider defaultProvider = getDefaultProvider(childText);
          return defaultProvider != null && defaultProvider.isCollapsedByDefault(childText);
        }
      }
    }
    return isRegionCollapsedByDefault(node);
  }

  /**
   * Returns the default collapsed state for the folding region related to the specified node.
   *
   * @param node the node for which the collapsed state is requested.
   * @return true if the region is collapsed by default, false otherwise.
   */
  protected abstract boolean isRegionCollapsedByDefault(@NotNull ASTNode node);

  /**
   * Returns true if the node corresponds to custom region start. The node must be a custom folding candidate and match custom folding 
   * start pattern.
   *
   * @param node The node which may contain custom region start.
   * @return True if the node marks a custom region start.
   */
  public final boolean isCustomRegionStart(ASTNode node) {
    if (isCustomFoldingCandidate(node)) {
      String nodeText = node.getText();
      CustomFoldingProvider defaultProvider = getDefaultProvider(nodeText);
      return defaultProvider != null && defaultProvider.isCustomRegionStart(nodeText);
    }
    return false;
  }

  /**
   * Returns true if the node corresponds to custom region end. The node must be a custom folding candidate and match custom folding
   * end pattern.
   *
   * @param node The node which may contain custom region end
   * @return True if the node marks a custom region end.
   */
  protected final boolean isCustomRegionEnd(ASTNode node) {
    if (isCustomFoldingCandidate(node)) {
      String nodeText = node.getText();
      CustomFoldingProvider defaultProvider = getDefaultProvider(nodeText);
      return defaultProvider != null && defaultProvider.isCustomRegionEnd(nodeText);
    }
    return false;
  }

  @Nullable
  private CustomFoldingProvider getDefaultProvider(String elementText) {
    if (myDefaultProvider == null) {
      for (CustomFoldingProvider provider : CustomFoldingProvider.getAllProviders()) {
        if (provider.isCustomRegionStart(elementText) || provider.isCustomRegionEnd(elementText)) {
          myDefaultProvider = provider;
        }
      }
    }
    return myDefaultProvider;
  }

  /**
   * Checks if a node may contain custom folding tags. By default returns true for PsiComment but a language folding builder may override
   * this method to allow only specific subtypes of comments (for example, line comments only).
   * @param node The node to check.
   * @return True if the node may contain custom folding tags.
   */
  protected boolean isCustomFoldingCandidate(ASTNode node) {
    return node.getPsi() instanceof PsiComment;
  }

  /**
   * Checks if the node is used as custom folding root. Any custom folding elements inside the root are considered to be at the same level
   * even if they are located at different levels of PSI tree. Collected folding descriptors always contain only root elements with
   * appropriate ranges. The method returns true if the node has any child elements.
   *
   * @param node  The node to check.
   * @return      True if the node is a root for custom foldings.
   */
  protected boolean isCustomFoldingRoot(ASTNode node) {
    return node.getFirstChildNode() != null;
  }

  private static class FoldingStack extends Stack<ASTNode> {
    private final ASTNode owner;

    public FoldingStack(@NotNull ASTNode owner) {
      super(1);
      this.owner = owner;
    }

    @NotNull
    public ASTNode getOwner() {
      return owner;
    }
  }
}

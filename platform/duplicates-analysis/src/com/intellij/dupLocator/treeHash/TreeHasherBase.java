package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.PsiElementRole;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.dupLocator.equivalence.MultiChildDescriptor;
import com.intellij.dupLocator.equivalence.SingleChildDescriptor;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class TreeHasherBase extends AbstractTreeHasher {
  private final FragmentsCollector myCallback;
  private final int myDiscardCost;
  private final DuplicatesProfile myProfile;

  TreeHasherBase(@Nullable FragmentsCollector callback,
                 @NotNull DuplicatesProfile profile,
                 int discardCost, boolean forIndexing) {
    super(callback, forIndexing);
    myCallback = callback;
    myDiscardCost = discardCost;
    myProfile = profile;
  }

  @Override
  protected int getDiscardCost(PsiElement root) {
    if (myDiscardCost >= 0) {
      return myDiscardCost;
    }
    return myProfile.getDuplocatorState(myProfile.getLanguage(root)).getDiscardCost();
  }

  @Override
  protected TreeHashResult hash(@NotNull PsiElement root, PsiFragment upper, @NotNull NodeSpecificHasher hasher) {
    final TreeHashResult result = computeHash(root, upper, hasher);

    // todo: try to optimize (ex. compute cost and hash separately)
    final int discardCost = getDiscardCost(root);
    if (result.getCost() < discardCost) {
      return new TreeHashResult(0, result.getCost(), result.getFragment());
    }

    return result;
  }

  private TreeHashResult computeHash(PsiElement root, PsiFragment upper, NodeSpecificHasher hasher) {
    final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(root);

    if (descriptorProvider != null) {
      final EquivalenceDescriptor descriptor = descriptorProvider.buildDescriptor(root);

      if (descriptor != null) {
        return computeHash(root, upper, descriptor, hasher);
      }
    }

    if (root instanceof PsiFile) {
      final List<PsiElement> children = hasher.getNodeChildren(root);
      if (children.size() <= 20) {
        return hashCodeBlock(children, upper, hasher, true);
      }
    }

    final NodeSpecificHasherBase ssrNodeSpecificHasher = (NodeSpecificHasherBase)hasher;

    if (shouldBeAnonymized(root, ssrNodeSpecificHasher)) {
      return computeElementHash(root, upper, hasher);
    }

    if (myForIndexing) {
      return computeElementHash(root, upper, hasher);
    }

    final PsiElement element = DuplocatorUtil.getOnlyChild(root, ssrNodeSpecificHasher.getNodeFilter());
    if (element != root) {
      final TreeHashResult result = hash(element, upper, hasher);
      final int cost = hasher.getNodeCost(root);
      return new TreeHashResult(result.getHash(), result.getCost() + cost, result.getFragment());
    }

    return computeElementHash(element, upper, hasher);
  }

  @Override
  public boolean shouldAnonymize(PsiElement root, NodeSpecificHasher hasher) {
    return shouldBeAnonymized(root, (NodeSpecificHasherBase)hasher);
  }

  @Override
  protected TreeHashResult computeElementHash(@NotNull PsiElement root, PsiFragment upper, NodeSpecificHasher hasher) {
    if (myForIndexing) {
      return TreeHashingUtils.computeElementHashForIndexing(this, myCallBack, root, upper, hasher);
    }

    final List<PsiElement> children = hasher.getNodeChildren(root);
    final int size = children.size();
    final int[] childHashes = new int[size];
    final int[] childCosts = new int[size];

    final PsiFragment fragment = buildFragment(hasher, root, getCost(root));

    if (upper != null) {
      fragment.setParent(upper);
    }

    if (size == 0 && !(root instanceof LeafElement)) {
      // contains only whitespaces and other unmeaning children
      return new TreeHashResult(0, hasher.getNodeCost(root), fragment);
    }

    for (int i = 0; i < size; i++) {
      final TreeHashResult res = this.hash(children.get(i), fragment, hasher);
      childHashes[i] = res.getHash();
      childCosts[i] = res.getCost();
    }

    final int c = hasher.getNodeCost(root) + AbstractTreeHasher.vector(childCosts);
    final int h1 = hasher.getNodeHash(root);

    final int discardCost = getDiscardCost(root);

    for (int i = 0; i < size; i++) {
      if (childCosts[i] <= discardCost && ignoreChildHash(children.get(i))) {
        childHashes[i] = 0;
      }
    }

    int h = h1 + AbstractTreeHasher.vector(childHashes);

    if (shouldBeAnonymized(root, (NodeSpecificHasherBase)hasher)) {
      h = 0;
    }

    if (myCallBack != null) {
      myCallBack.add(h, c, fragment);
    }

    return new TreeHashResult(h, c, fragment);
  }

  @Override
  protected TreeHashResult hashCodeBlock(List<? extends PsiElement> statements,
                                         PsiFragment upper,
                                         NodeSpecificHasher hasher,
                                         boolean forceHash) {
    if (!myForIndexing) return super.hashCodeBlock(statements, upper, hasher, forceHash);

    return TreeHashingUtils.hashCodeBlockForIndexing(this, myCallBack, statements, upper, hasher);
  }

  private TreeHashResult computeHash(PsiElement element,
                                     PsiFragment parent,
                                     EquivalenceDescriptor descriptor,
                                     NodeSpecificHasher hasher) {
    final NodeSpecificHasherBase ssrHasher = (NodeSpecificHasherBase)hasher;
    final PsiElement element2 = DuplocatorUtil.skipNodeIfNeccessary(element, descriptor, ssrHasher.getNodeFilter());
    final boolean canSkip = element2 != element;

    final PsiFragment fragment = buildFragment(hasher, element, 0);

    if (parent != null) {
      fragment.setParent(parent);
    }

    int hash = canSkip ? 0 : hasher.getNodeHash(element);
    int cost = hasher.getNodeCost(element);

    for (SingleChildDescriptor childDescriptor : descriptor.getSingleChildDescriptors()) {
      final Couple<Integer> childHashResult = computeHash(childDescriptor, fragment, hasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (MultiChildDescriptor childDescriptor : descriptor.getMultiChildDescriptors()) {
      final Couple<Integer> childHashResult = computeHash(childDescriptor, fragment, hasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (Object constant : descriptor.getConstants()) {
      final int constantHash = constant != null ? constant.hashCode() : 0;
      hash = hash * 31 + constantHash;
    }

    for (PsiElement[] codeBlock : descriptor.getCodeBlocks()) {
      final List<PsiElement> filteredBlock = filter(codeBlock, ssrHasher);
      final TreeHashResult childHashResult = hashCodeBlock(filteredBlock, fragment, hasher);
      hash = hash * 31 + childHashResult.getHash();
      cost += childHashResult.getCost();
    }

    if (myCallback != null) {
      myCallback.add(hash, cost, fragment);
    }
    return new TreeHashResult(hash, cost, fragment);
  }

  public static List<PsiElement> filter(PsiElement[] elements, NodeSpecificHasherBase hasher) {
    List<PsiElement> filteredElements = new ArrayList<>();
    for (PsiElement element : elements) {
      if (!hasher.getNodeFilter().accepts(element)) {
        filteredElements.add(element);
      }
    }
    return filteredElements;
  }

  @NotNull
  private Couple<Integer> computeHash(SingleChildDescriptor childDescriptor,
                                      PsiFragment parentFragment,
                                      NodeSpecificHasher nodeSpecificHasher) {

    final PsiElement element = childDescriptor.getElement();
    if (element == null) {
      return Couple.of(0, 0);
    }
    final Couple<Integer> result = doComputeHash(childDescriptor, parentFragment, nodeSpecificHasher);

    final DuplicatesProfileBase duplicatesProfile = ((NodeSpecificHasherBase)nodeSpecificHasher).getDuplicatesProfile();
    final PsiElementRole role = duplicatesProfile.getRole(element);
    if (role != null && !duplicatesProfile.getDuplocatorState(duplicatesProfile.getLanguage(element)).distinguishRole(role)) {
      return Couple.of(0, result.second);
    }
    return result;
  }

  private static boolean shouldBeAnonymized(PsiElement element, NodeSpecificHasherBase nodeSpecificHasher) {
    final DuplicatesProfileBase duplicatesProfile = nodeSpecificHasher.getDuplicatesProfile();
    final PsiElementRole role = duplicatesProfile.getRole(element);
    return role != null && !duplicatesProfile.getDuplocatorState(duplicatesProfile.getLanguage(element)).distinguishRole(role);
  }

  @NotNull
  private Couple<Integer> doComputeHash(SingleChildDescriptor childDescriptor,
                                        PsiFragment parentFragment,
                                        NodeSpecificHasher nodeSpecificHasher) {
    final PsiElement element = childDescriptor.getElement();

    switch (childDescriptor.getType()) {
      case OPTIONALLY_IN_PATTERN:
      case DEFAULT:
        final TreeHashResult result = hash(element, parentFragment, nodeSpecificHasher);
        return Couple.of(result.getHash(), result.getCost());

      case CHILDREN_OPTIONALLY_IN_PATTERN:
      case CHILDREN:
        TreeHashResult[] childResults = computeHashesForChildren(element, parentFragment, nodeSpecificHasher);
        int[] hashes = getHashes(childResults);
        int[] costs = getCosts(childResults);

        int hash = AbstractTreeHasher.vector(hashes, 31);
        int cost = AbstractTreeHasher.vector(costs);

        return Couple.of(hash, cost);

      case CHILDREN_IN_ANY_ORDER:
        childResults = computeHashesForChildren(element, parentFragment, nodeSpecificHasher);
        hashes = getHashes(childResults);
        costs = getCosts(childResults);

        hash = AbstractTreeHasher.vector(hashes);
        cost = AbstractTreeHasher.vector(costs);

        return Couple.of(hash, cost);

      default:
        return Couple.of(0, 0);
    }
  }

  @NotNull
  private Couple<Integer> computeHash(MultiChildDescriptor childDescriptor,
                                      PsiFragment parentFragment,
                                      NodeSpecificHasher nodeSpecificHasher) {
    final PsiElement[] elements = childDescriptor.getElements();

    if (elements == null) {
      return Couple.of(0, 0);
    }

    switch (childDescriptor.getType()) {

      case OPTIONALLY_IN_PATTERN:
      case DEFAULT:
        TreeHashResult[] childResults = computeHashes(elements, parentFragment, nodeSpecificHasher);
        int[] hashes = getHashes(childResults);
        int[] costs = getCosts(childResults);

        int hash = AbstractTreeHasher.vector(hashes, 31);
        int cost = AbstractTreeHasher.vector(costs);

        return Couple.of(hash, cost);

      case IN_ANY_ORDER:
        childResults = computeHashes(elements, parentFragment, nodeSpecificHasher);
        hashes = getHashes(childResults);
        costs = getCosts(childResults);

        hash = AbstractTreeHasher.vector(hashes);
        cost = AbstractTreeHasher.vector(costs);

        return Couple.of(hash, cost);

      default:
        return Couple.of(0, 0);
    }
  }

  @NotNull
  private TreeHashResult[] computeHashesForChildren(PsiElement element,
                                                    PsiFragment parentFragment,
                                                    NodeSpecificHasher nodeSpecificHasher) {
    final List<TreeHashResult> result = new ArrayList<>();

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final TreeHashResult childResult = hash(element, parentFragment, nodeSpecificHasher);
      result.add(childResult);
    }
    return result.toArray(new TreeHashResult[result.size()]);
  }

  @NotNull
  private TreeHashResult[] computeHashes(PsiElement[] elements,
                                         PsiFragment parentFragment,
                                         NodeSpecificHasher nodeSpecificHasher) {
    TreeHashResult[] result = new TreeHashResult[elements.length];

    for (int i = 0; i < elements.length; i++) {
      result[i] = hash(elements[i], parentFragment, nodeSpecificHasher);
    }

    return result;
  }

  private static int[] getHashes(TreeHashResult[] results) {
    int[] hashes = new int[results.length];

    for (int i = 0; i < results.length; i++) {
      hashes[i] = results[i].getHash();
    }

    return hashes;
  }

  private static int[] getCosts(TreeHashResult[] results) {
    int[] costs = new int[results.length];

    for (int i = 0; i < results.length; i++) {
      costs[i] = results[i].getCost();
    }

    return costs;
  }
}

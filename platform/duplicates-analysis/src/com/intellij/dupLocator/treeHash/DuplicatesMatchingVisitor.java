package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.AbstractMatchingVisitor;
import com.intellij.dupLocator.ExternalizableDuplocatorState;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.PsiElementRole;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class DuplicatesMatchingVisitor extends AbstractMatchingVisitor {
  private final NodeSpecificHasherBase myNodeSpecificHasher;
  private final NodeFilter myNodeFilter;
  private final int myDiscardCost;
  private final TreeHasherBase myTreeHasher;
  private final Map<PsiElement, TreeHashResult> myPsiElement2HashAndCost = new HashMap<>();

  public DuplicatesMatchingVisitor(NodeSpecificHasherBase nodeSpecificHasher,
                                   @NotNull NodeFilter nodeFilter,
                                   int discardCost) {
    myNodeSpecificHasher = nodeSpecificHasher;
    myNodeFilter = nodeFilter;
    myDiscardCost = discardCost;
    myTreeHasher = new TreeHasherBase(null, myNodeSpecificHasher.getDuplicatesProfile(), discardCost, false) {
      @Override
      protected TreeHashResult hash(@NotNull PsiElement root, PsiFragment upper, @NotNull NodeSpecificHasher hasher) {
        TreeHashResult result = myPsiElement2HashAndCost.get(root);
        if (result == null) {
          result = super.hash(root, upper, hasher);
          myPsiElement2HashAndCost.put(root, result);
        }
        return result;
      }
    };
  }

  @Override
  public boolean matchSequentially(@NotNull NodeIterator nodes, @NotNull NodeIterator nodes2) {
    while (true) {
      if (!nodes.hasNext() || !nodes2.hasNext()) {
        return !nodes.hasNext() && !nodes2.hasNext();
      }

      skipIfNecessary(nodes, nodes2);
      skipIfNecessary(nodes2, nodes);

      if (!nodes.hasNext() || !nodes2.hasNext()) {
        return !nodes.hasNext() && !nodes2.hasNext();
      }

      if (!match(nodes.current(), nodes2.current())) {
        return false;
      }

      nodes.advance();
      nodes2.advance();
    }
  }

  private static void skipIfNecessary(NodeIterator nodes, NodeIterator nodes2) {
    while (DuplocatorUtil.shouldSkip(nodes2.current(), nodes.current())) {
      nodes2.advance();
    }
  }

  @Override
  public boolean match(PsiElement element1, PsiElement element2) {
    if (element1 == null || element2 == null) {
      return element1 == element2;
    }

    if (myDiscardCost > 0) {
      final int cost1 = myTreeHasher.hash(element1, null, myNodeSpecificHasher).getCost();
      final int cost2 = myTreeHasher.hash(element2, null, myNodeSpecificHasher).getCost();

      if (cost1 < myDiscardCost || cost2 < myDiscardCost) {
        return true;
      }
    }

    final DuplicatesProfileBase duplicatesProfile = myNodeSpecificHasher.getDuplicatesProfile();

    final PsiElementRole role1 = duplicatesProfile.getRole(element1);
    final PsiElementRole role2 = duplicatesProfile.getRole(element2);

    final Set<PsiElementRole> skippedRoles = EnumSet.noneOf(PsiElementRole.class);
    final ExternalizableDuplocatorState duplocatorState =
      duplicatesProfile.getDuplocatorState(duplicatesProfile.getLanguage(element1));

    for (PsiElementRole role : PsiElementRole.values()) {
      if (!duplocatorState.distinguishRole(role)) {
        skippedRoles.add(role);
      }
    }

    if (role1 == role2 && skippedRoles.contains(role1)) {
      return true;
    }

    final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(element1);
    EquivalenceDescriptor descriptor1 = descriptorProvider != null ? descriptorProvider.buildDescriptor(element1) : null;
    EquivalenceDescriptor descriptor2 = descriptorProvider != null ? descriptorProvider.buildDescriptor(element2) : null;

    PsiElement newElement1 = DuplocatorUtil.skipNodeIfNecessary(element1, descriptor1, myNodeFilter);
    PsiElement newElement2 = DuplocatorUtil.skipNodeIfNecessary(element2, descriptor2, myNodeFilter);

    if (newElement1 != element1 || newElement2 != element2) {
      return match(newElement1, newElement2);
    }

    if (!element1.getClass().equals(element2.getClass())) {
      return false;
    }

    if (descriptor1 != null && descriptor2 != null) {
      return DuplocatorUtil.match(descriptor1, descriptor2, this, skippedRoles, duplicatesProfile);
    }

    if (element1 instanceof LeafElement) {
      IElementType elementType1 = ((LeafElement)element1).getElementType();
      IElementType elementType2 = ((LeafElement)element2).getElementType();

      if (!duplocatorState.distinguishLiterals() &&
          duplicatesProfile.getLiterals().contains(elementType1) &&
          duplicatesProfile.getLiterals().contains(elementType2)) {
        return true;
      }
      return element1.getText().equals(element2.getText());
    }

    if (element1.getFirstChild() == null && element1.getTextLength() == 0) {
      return element2.getFirstChild() == null && element2.getTextLength() == 0;
    }

    return matchSequentially(new FilteringNodeIterator(SiblingNodeIterator.create(element1.getFirstChild()), getNodeFilter()),
                             new FilteringNodeIterator(SiblingNodeIterator.create(element2.getFirstChild()), getNodeFilter()));
  }

  @Override
  protected boolean doMatchInAnyOrder(@NotNull NodeIterator it1, @NotNull NodeIterator it2) {
    final List<PsiElement> elements1 = new ArrayList<>();
    final List<PsiElement> elements2 = new ArrayList<>();

    while (it1.hasNext()) {
      final PsiElement element = it1.current();
      if (element != null) {
        elements1.add(element);
      }
      it1.advance();
    }

    while (it2.hasNext()) {
      final PsiElement element = it2.current();
      if (element != null) {
        elements2.add(element);
      }
      it2.advance();
    }

    if (elements1.size() != elements2.size()) {
      return false;
    }

    Int2ObjectMap<List<PsiElement>> hashToElement = new Int2ObjectOpenHashMap<>(elements1.size());
    for (PsiElement element : elements1) {
      TreeHashResult result = myTreeHasher.hash(element, null, myNodeSpecificHasher);
      if (result != null) {
        hashToElement.computeIfAbsent(result.getHash(), __ -> new ArrayList<>()).add(element);
      }
    }

    for (PsiElement element : elements2) {
      TreeHashResult result = myTreeHasher.hash(element, null, myNodeSpecificHasher);
      if (result != null) {
        int hash = result.getHash();
        List<PsiElement> list = hashToElement.get(hash);
        if (list == null) {
          return false;
        }

        boolean found = false;
        for (Iterator<PsiElement> it = list.iterator(); it.hasNext();) {
          if (match(element, it.next())) {
            it.remove();
            found = true;
          }
        }

        if (!found) {
          return false;
        }

        if (list.size() == 0) {
          hashToElement.remove(hash);
        }
      }
    }

    return hashToElement.size() == 0;
  }

  @NotNull
  @Override
  protected NodeFilter getNodeFilter() {
    return myNodeFilter;
  }
}

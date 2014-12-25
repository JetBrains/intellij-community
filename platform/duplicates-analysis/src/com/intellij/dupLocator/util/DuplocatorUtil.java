package com.intellij.dupLocator.util;

import com.intellij.dupLocator.*;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.equivalence.EquivalenceDescriptorProvider;
import com.intellij.dupLocator.equivalence.MultiChildDescriptor;
import com.intellij.dupLocator.equivalence.SingleChildDescriptor;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class DuplocatorUtil {
  private DuplocatorUtil() {
  }

  public static boolean isIgnoredNode(PsiElement element) {
    // ex. "var i = 0" in AS: empty JSAttributeList should be skipped
    /*if (element.getText().length() == 0) {
      return true;
    }*/

    if (element instanceof PsiWhiteSpace || element instanceof PsiErrorElement || element instanceof PsiComment) {
      return true;
    }

    if (!(element instanceof LeafElement)) {
      return false;
    }

    if (CharArrayUtil.containsOnlyWhiteSpaces(element.getText())) {
      return true;
    }

    EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(element);
    if (descriptorProvider == null) {
      return false;
    }

    final IElementType elementType = ((LeafElement)element).getElementType();
    return descriptorProvider.getIgnoredTokens().contains(elementType);
  }

  public static PsiElement getOnlyChild(PsiElement element, @NotNull NodeFilter filter) {
    FilteringNodeIterator it = new FilteringNodeIterator(new SiblingNodeIterator(element.getFirstChild()), filter);
    PsiElement child = it.current();
    if (child != null) {
      it.advance();
      if (!it.hasNext()) {
        return child;
      }
    }
    return element;
  }

  public static boolean shouldSkip(PsiElement element, PsiElement elementToMatchWith) {
    if (element == null || elementToMatchWith == null) {
      return false;
    }

    if (element.getClass() == elementToMatchWith.getClass()) {
      return false;
    }

    if (element.getFirstChild() == null && element.getTextLength() == 0 && !(element instanceof LeafElement)) {
      return true;
    }

    return false;
  }

  @Nullable
  public static PsiElement skipNodeIfNeccessary(PsiElement element, EquivalenceDescriptor descriptor, NodeFilter filter) {
    if (element == null) {
      return null;
    }

    /*if (!canSkip(element) && getOnlyNonWhitespaceChild(element) == null) {
      return element;
    }*/

    // todo optimize! (this method is often invokated for the same node)

    if (descriptor == null) {
      final EquivalenceDescriptorProvider provider = EquivalenceDescriptorProvider.getInstance(element);
      if (provider != null) {
        descriptor = provider.buildDescriptor(element);
      }
    }

    if (descriptor != null) {
      final PsiElement onlyChild = getOnlyChildFromDescriptor(descriptor, filter);
      return onlyChild != null ? onlyChild : element;
    }
    return getOnlyChild(element, filter);
  }

  @Nullable
  private static PsiElement getOnlyChildFromDescriptor(EquivalenceDescriptor equivalenceDescriptor, NodeFilter filter) {
    if (!equivalenceDescriptor.getConstants().isEmpty()) {
      return null;
    }

    final List<SingleChildDescriptor> singleChildren = equivalenceDescriptor.getSingleChildDescriptors();
    final List<MultiChildDescriptor> multiChildren = equivalenceDescriptor.getMultiChildDescriptors();
    final List<PsiElement[]> codeBlocks = equivalenceDescriptor.getCodeBlocks();

    if (singleChildren.size() + multiChildren.size() + codeBlocks.size() != 1) {
      return null;
    }

    if (!singleChildren.isEmpty()) {
      final SingleChildDescriptor descriptor = singleChildren.get(0);
      final PsiElement child = descriptor.getElement();

      if (child != null) {
        final SingleChildDescriptor.MyType type = descriptor.getType();

        if (type == SingleChildDescriptor.MyType.DEFAULT) {
          return child;
        }
        else if (type == SingleChildDescriptor.MyType.CHILDREN ||
                 type == SingleChildDescriptor.MyType.CHILDREN_IN_ANY_ORDER) {
          return getOnlyChild(child, filter);
        }
      }
    }
    else if (!multiChildren.isEmpty()) {
      final MultiChildDescriptor descriptor = multiChildren.get(0);
      final PsiElement[] children = descriptor.getElements();

      if (children != null && children.length == 1 && descriptor.getType() != MultiChildDescriptor.MyType.OPTIONALLY) {
        return children[0];
      }
    }
    else if (!codeBlocks.isEmpty()) {
      final PsiElement[] codeBlock = codeBlocks.get(0);
      if (codeBlock != null && codeBlock.length == 1) {
        return codeBlock[0];
      }
    }
    return null;
  }

  public static boolean match(@NotNull EquivalenceDescriptor descriptor1,
                              @NotNull EquivalenceDescriptor descriptor2,
                              @NotNull AbstractMatchingVisitor g,
                              @NotNull Set<PsiElementRole> skippedRoles,
                              @Nullable DuplicatesProfile profile) {

    if (descriptor1.getSingleChildDescriptors().size() != descriptor2.getSingleChildDescriptors().size()) {
      return false;
    }

    if (descriptor1.getMultiChildDescriptors().size() != descriptor2.getMultiChildDescriptors().size()) {
      return false;
    }

    if (descriptor1.getCodeBlocks().size() != descriptor2.getCodeBlocks().size()) {
      return false;
    }

    if (descriptor1.getConstants().size() != descriptor2.getConstants().size()) {
      return false;
    }

    for (int i = 0, n = descriptor1.getConstants().size(); i < n; i++) {
      Object childDescriptor1 = descriptor1.getConstants().get(i);
      Object childDescriptor2 = descriptor2.getConstants().get(i);

      if (!Comparing.equal(childDescriptor1, childDescriptor2)) {
        return false;
      }
    }

    for (int i = 0, n = descriptor1.getSingleChildDescriptors().size(); i < n; i++) {
      SingleChildDescriptor childDescriptor1 = descriptor1.getSingleChildDescriptors().get(i);
      SingleChildDescriptor childDescriptor2 = descriptor2.getSingleChildDescriptors().get(i);

      if (!match(childDescriptor1, childDescriptor2, g, skippedRoles, profile)) {
        return false;
      }
    }

    for (int i = 0, n = descriptor1.getMultiChildDescriptors().size(); i < n; i++) {
      MultiChildDescriptor childDescriptor1 = descriptor1.getMultiChildDescriptors().get(i);
      MultiChildDescriptor childDescriptor2 = descriptor2.getMultiChildDescriptors().get(i);

      if (!match(childDescriptor1, childDescriptor2, g)) {
        return false;
      }
    }

    for (int i = 0, n = descriptor1.getCodeBlocks().size(); i < n; i++) {
      final PsiElement[] codeBlock1 = descriptor1.getCodeBlocks().get(i);
      final PsiElement[] codeBlock2 = descriptor2.getCodeBlocks().get(i);

      if (!g.matchSequentially(codeBlock1, codeBlock2)) {
        return false;
      }
    }

    return true;
  }

  private static boolean match(@NotNull SingleChildDescriptor childDescriptor1,
                               @NotNull SingleChildDescriptor childDescriptor2,
                               @NotNull AbstractMatchingVisitor g,
                               @NotNull Set<PsiElementRole> skippedRoles,
                               @Nullable DuplicatesProfile duplicatesProfile) {
    if (childDescriptor1.getType() != childDescriptor2.getType()) {
      return false;
    }

    final PsiElement element1 = childDescriptor1.getElement();
    final PsiElement element2 = childDescriptor2.getElement();

    if (duplicatesProfile != null) {
      final PsiElementRole role1 = element1 != null ? duplicatesProfile.getRole(element1) : null;
      final PsiElementRole role2 = element2 != null ? duplicatesProfile.getRole(element2) : null;

      if (role1 == role2 && skippedRoles.contains(role1)) {
        return true;
      }
    }

    switch (childDescriptor1.getType()) {

      case DEFAULT:
        return g.match(element1, element2);

      case OPTIONALLY_IN_PATTERN:
      case OPTIONALLY:
        return g.matchOptionally(element1, element2);

      case CHILDREN:
        return g.matchSons(element1, element2);

      case CHILDREN_OPTIONALLY_IN_PATTERN:
      case CHILDREN_OPTIONALLY:
        return g.matchSonsOptionally(element1, element2);

      case CHILDREN_IN_ANY_ORDER:
        return g.matchSonsInAnyOrder(element1, element2);

      default:
        return false;
    }
  }

  private static boolean match(@NotNull MultiChildDescriptor childDescriptor1,
                               @NotNull MultiChildDescriptor childDescriptor2,
                               @NotNull AbstractMatchingVisitor g) {

    if (childDescriptor1.getType() != childDescriptor2.getType()) {
      return false;
    }

    final PsiElement[] elements1 = childDescriptor1.getElements();
    final PsiElement[] elements2 = childDescriptor2.getElements();

    switch (childDescriptor1.getType()) {

      case DEFAULT:
        return g.matchSequentially(elements1, elements2);

      case OPTIONALLY_IN_PATTERN:
      case OPTIONALLY:
        return g.matchOptionally(elements1, elements2);

      case IN_ANY_ORDER:
        return g.matchInAnyOrder(elements1, elements2);

      default:
        return false;
    }
  }

  @Nullable
  public static DuplocatorState getDuplocatorState(PsiFragment frag) {
    final Language language = frag.getLanguage();
    if (language == null) {
      return null;
    }

    final DuplicatesProfile profile = DuplicatesProfile.findProfileForLanguage(language);
    return profile != null
           ? profile.getDuplocatorState(language)
           : null;
  }

  @NotNull
  public static ExternalizableDuplocatorState registerAndGetState(@NotNull Language language) {
    final MultilanguageDuplocatorSettings settings = MultilanguageDuplocatorSettings.getInstance();
    ExternalizableDuplocatorState state = settings.getState(language);
    if (state == null) {
      state = new DefaultDuplocatorState();
      settings.registerState(language, state);
    }
    return state;
  }
}

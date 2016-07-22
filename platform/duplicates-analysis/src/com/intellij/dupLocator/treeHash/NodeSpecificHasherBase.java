package com.intellij.dupLocator.treeHash;

import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class NodeSpecificHasherBase extends NodeSpecificHasher {
  private final TreeHasherBase myTreeHasher;
  private final DuplocatorSettings mySettings;
  private final DuplicatesProfileBase myDuplicatesProfile;

  private final NodeFilter myNodeFilter = new NodeFilter() {
    @Override
    public boolean accepts(PsiElement element) {
      return DuplocatorUtil.isIgnoredNode(element) || isToSkipAsLiteral(element);
    }
  };
  protected final boolean myForIndexing;

  private boolean isToSkipAsLiteral(PsiElement element) {
    return isLiteral(element) &&
           !myDuplicatesProfile.getDuplocatorState(myDuplicatesProfile.getLanguage(element)).distinguishLiterals();
  }

  public NodeSpecificHasherBase(@NotNull final DuplocatorSettings settings,
                                @NotNull FragmentsCollector callback,
                                @NotNull DuplicatesProfileBase duplicatesProfile) {
    this(settings, callback, duplicatesProfile, false);
  }

  public NodeSpecificHasherBase(@NotNull final DuplocatorSettings settings,
                                @NotNull FragmentsCollector callback,
                                @NotNull DuplicatesProfileBase duplicatesProfile,
                                boolean forIndexing) {
    myTreeHasher = new TreeHasherBase(callback, duplicatesProfile, forIndexing ? 0:-1, forIndexing);
    mySettings = settings;
    myDuplicatesProfile = duplicatesProfile;
    myForIndexing = forIndexing;
  }

  @NotNull
  public NodeFilter getNodeFilter() {
    return myNodeFilter;
  }

  @Override
  public int getNodeHash(PsiElement node) {
    if (node == null) {
      return 0;
    }
    if (node instanceof PsiWhiteSpace || node instanceof PsiErrorElement) {
      return 0;
    }
    else if (node instanceof LeafElement) {
      if (isToSkipAsLiteral(node)) {
        return 0;
      }
      return node.getText().hashCode();

    }
    return node.getClass().getName().hashCode();
  }

  private boolean isLiteral(PsiElement node) {
    if (node instanceof LeafElement) {
      final IElementType elementType = ((LeafElement)node).getElementType();
      if (myDuplicatesProfile.getLiterals().contains(elementType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getNodeCost(PsiElement node) {
    return node != null ? myDuplicatesProfile.getNodeCost(node) : 0;
  }

  @Override
  public List<PsiElement> getNodeChildren(PsiElement node) {
    final List<PsiElement> result = new ArrayList<>();

    final FilteringNodeIterator it = new FilteringNodeIterator(new SiblingNodeIterator(node.getFirstChild()), myNodeFilter);
    while (it.hasNext()) {
      result.add(it.current());
      it.advance();
    }

    return result;
  }

  @Override
  public boolean areNodesEqual(@NotNull PsiElement node1, @NotNull PsiElement node2) {
    return false;
  }

  @Override
  public boolean areTreesEqual(@NotNull PsiElement root1, @NotNull PsiElement root2, int discardCost) {
    if (root1 == root2) {
      return true;
    }
    return new DuplicatesMatchingVisitor(this, myNodeFilter, discardCost).match(root1, root2);
  }

  @NotNull
  public DuplicatesProfileBase getDuplicatesProfile() {
    return myDuplicatesProfile;
  }

  @Override
  public boolean checkDeep(PsiElement node1, PsiElement node2) {
    // todo: try to optimize this
    return true;
  }

  @Override
  public void visitNode(@NotNull PsiElement node) {
    Language language = null;
    if (node instanceof PsiFile) {
      FileType fileType = ((PsiFile)node).getFileType();
      if (fileType instanceof LanguageFileType) {
        language = ((LanguageFileType)fileType).getLanguage();
      }
    }
    if (language == null) language = node.getLanguage();
    if ((myForIndexing || mySettings.SELECTED_PROFILES.contains(language.getDisplayName())) &&
        myDuplicatesProfile.isMyLanguage(language)) {

      myTreeHasher.hash(node, this);
    }
  }

  @Override
  public void hashingFinished() {
  }
}

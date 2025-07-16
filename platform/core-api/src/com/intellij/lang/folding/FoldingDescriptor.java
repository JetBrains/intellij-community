// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Defines a single folding region in the code.
 *
 * <p><a name="Dependencies"><b>Dependencies</b></a></p>
 * Dependencies are objects (in particular, instances of {@link com.intellij.openapi.util.ModificationTracker},
 * more info - {@link com.intellij.psi.util.CachedValueProvider.Result#getDependencyItems here}),
 * which can be tracked for changes, that should trigger folding regions recalculation for an editor (initiating code folding pass).
 *
 * @see FoldingBuilder
 */
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY_ARRAY = new FoldingDescriptor[0];
  @Deprecated
  public static final FoldingDescriptor[] EMPTY = EMPTY_ARRAY;

  private static final byte FLAG_NEVER_EXPANDS = 1;
  private static final byte FLAG_COLLAPSED_BY_DEFAULT_DEFINED = 1 << 1;
  private static final byte FLAG_COLLAPSED_BY_DEFAULT = 1 << 2;
  private static final byte FLAG_CAN_BE_REMOVED_WHEN_COLLAPSED = 1 << 3;
  private static final byte FLAG_GUTTER_MARK_ENABLED_FOR_SINGLE_LINE = 1 << 4;

  private final ASTNode myElement;
  private final TextRange myRange;
  private final @Nullable FoldingGroup myGroup;
  private final Set<Object> myDependencies;
  private String myPlaceholderText;
  private byte myFlags;

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range) {
    this(node, range, null);
  }

  public FoldingDescriptor(@NotNull PsiElement element, @NotNull TextRange range) {
    this(Objects.requireNonNull(element.getNode()), range, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group) {
    this(node, range, group, Collections.emptySet());
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change
   * folding description, see <a href="#Dependencies">Dependencies</a>
   */
  public FoldingDescriptor(@NotNull ASTNode node, @NotNull TextRange range, @Nullable FoldingGroup group, Set<Object> dependencies) {
    this(node, range, group, dependencies, false);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   * @param neverExpands shall be true for fold regions that must not be ever expanded.
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           Set<Object> dependencies,
                           boolean neverExpands) {
    this(node, range, group, dependencies, neverExpands, null, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param e  PSI element to which the folding region is related.
   * @param start Folded text range's start offset in file
   * @param end Folded text range's end offset in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   */
  public FoldingDescriptor(@NotNull PsiElement e,
                           int start,
                           int end,
                           @Nullable FoldingGroup group,
                           @NotNull String placeholderText) {
    this(e.getNode(), new TextRange(start, end), group, placeholderText);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @NotNull String placeholderText) {
    this(node, range, group, Collections.emptySet(), false, placeholderText, null);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   * @param collapsedByDefault Whether the region should be collapsed for newly opened files
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @Nullable("null means FoldingBuilder.getPlaceholderText will be used") String placeholderText,
                           @Nullable("null means FoldingBuilder.isCollapsedByDefault will be used") Boolean collapsedByDefault,
                           @NotNull Set<Object> dependencies) {
    this(node, range, group, dependencies, false, placeholderText, collapsedByDefault);
  }

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(ASTNode)}.
   * @param range The folded text range in file
   * @param group Regions with the same group instance expand and collapse together.
   * @param dependencies folding dependencies: other files or elements that could change, see <a href="#Dependencies">Dependencies</a>
   * @param neverExpands shall be true for fold regions that must not be ever expanded.
   * @param placeholderText Text displayed instead of folded text, when the region is collapsed
   * @param collapsedByDefault Whether the region should be collapsed for newly opened files
   */
  public FoldingDescriptor(@NotNull ASTNode node,
                           @NotNull TextRange range,
                           @Nullable FoldingGroup group,
                           @NotNull @Unmodifiable Set<Object> dependencies,
                           boolean neverExpands,
                           @Nullable("null means FoldingBuilder.getPlaceholderText will be used") String placeholderText,
                           @Nullable("null means FoldingBuilder.isCollapsedByDefault will be used") Boolean collapsedByDefault) {
    assert range.getLength() > 0 : range + ", text: " + node.getText() + ", language = " + node.getPsi().getLanguage();
    if (neverExpands && group != null) {
      throw new IllegalArgumentException("'Never-expanding' region cannot be part of a group");
    }
    myElement = node;
    myRange = range;
    myGroup = group;
    myDependencies = dependencies;
    try {
      assert dependencies.isEmpty() || !dependencies.contains(null);
    }
    catch (NullPointerException ignored) {
      // ImmutableCollections doesn't support null elements
    }
    myPlaceholderText = placeholderText;
    setFlag(FLAG_NEVER_EXPANDS, neverExpands);
    if (collapsedByDefault != null) {
      setFlag(FLAG_COLLAPSED_BY_DEFAULT_DEFINED, true);
      setFlag(FLAG_COLLAPSED_BY_DEFAULT, collapsedByDefault);
    }
  }

  /**
   * @return the node to which the folding region is related.
   */
  public @NotNull ASTNode getElement() {
    return myElement;
  }

  /**
   * Returns the folded text range.
   * @return the folded text range.
   */
  public @NotNull TextRange getRange() {
    return myRange;
  }

  public @Nullable FoldingGroup getGroup() {
    return myGroup;
  }

  public @Nullable String getPlaceholderText() {
    return myPlaceholderText == null ? calcPlaceholderText() : myPlaceholderText;
  }

  @ApiStatus.Internal
  final String getCachedPlaceholderText() {
    return myPlaceholderText;
  }

  public void setPlaceholderText(@Nullable("null means FoldingBuilder.getPlaceholderText will be used") String placeholderText) {
    myPlaceholderText = placeholderText;
  }

  String calcPlaceholderText() {
    PsiElement psiElement = myElement.getPsi();
    if (psiElement == null) return null;
    FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(psiElement.getLanguage());
    if (foldingBuilder == null) return null;
    return foldingBuilder instanceof FoldingBuilderEx
           ? ((FoldingBuilderEx)foldingBuilder).getPlaceholderText(myElement, myRange)
           : foldingBuilder.getPlaceholderText(myElement);
  }

  public @NotNull @Unmodifiable Set<Object> getDependencies() {
    return myDependencies;
  }

  public boolean isNonExpandable() {
    return getFlag(FLAG_NEVER_EXPANDS);
  }

  public boolean canBeRemovedWhenCollapsed() {
    return getFlag(FLAG_CAN_BE_REMOVED_WHEN_COLLAPSED);
  }

  public @Nullable Boolean isCollapsedByDefault() {
    return getFlag(FLAG_COLLAPSED_BY_DEFAULT_DEFINED) ? getFlag(FLAG_COLLAPSED_BY_DEFAULT) : null;
  }

  /**
   * By default, collapsed regions are not removed automatically, even if related PSI elements become invalid.
   * This method allows to override default behaviour for specific regions.
   */
  public void setCanBeRemovedWhenCollapsed(boolean canBeRemovedWhenCollapsed) {
    setFlag(FLAG_CAN_BE_REMOVED_WHEN_COLLAPSED, canBeRemovedWhenCollapsed);
  }

  /**
   * @see #setGutterMarkEnabledForSingleLine(boolean)
   */
  public boolean isGutterMarkEnabledForSingleLine() {
    return getFlag(FLAG_GUTTER_MARK_ENABLED_FOR_SINGLE_LINE);
  }

  /**
   * See javadoc for {@link com.intellij.openapi.editor.FoldRegion#setGutterMarkEnabledForSingleLine(boolean)}.
   *
   * @see #isGutterMarkEnabledForSingleLine()
   */
  public void setGutterMarkEnabledForSingleLine(boolean value) {
    setFlag(FLAG_GUTTER_MARK_ENABLED_FOR_SINGLE_LINE, value);
  }

  private boolean getFlag(byte mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  private void setFlag(byte mask, boolean value) {
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public String toString() {
    return myRange + " for AST: " + myElement;
  }
}

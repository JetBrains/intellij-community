// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

public abstract class PresentableNodeDescriptor<E> extends NodeDescriptor<E>  {
  private volatile @Nullable PresentationData myTemplatePresentation = null;
  private volatile @Nullable PresentationData myUpdatedPresentation = null;

  protected PresentableNodeDescriptor(Project project, @Nullable NodeDescriptor parentDescriptor) {
    super(project, parentDescriptor);
  }

  @Override
  public final boolean update() {
    if (shouldUpdateData()) {
      PresentationData before = getPresentation();
      PresentationData updated = getUpdatedPresentation();
      return shouldApply() && apply(updated, before);
    }
    return false;
  }

  protected final boolean apply(@NotNull PresentationData presentation) {
    return apply(presentation, null);
  }

  @Override
  public void applyFrom(@NotNull NodeDescriptor desc) {
    if (desc instanceof PresentableNodeDescriptor) {
      apply(((PresentableNodeDescriptor<?>)desc).getPresentation());
    }
    else {
      super.applyFrom(desc);
    }
  }

  protected final boolean apply(@NotNull PresentationData presentation, @Nullable PresentationData before) {
    setIcon(presentation.getIcon(false));
    // If the node has both plain and colored text, the plain one takes priority for myName because it's also supposed to be plain,
    // and it can be used, e.g. for sorting, while the colored version may contain information not needed for sorting such as inplace comments.
    myName = presentation.getPresentableText();
    if (myName == null) {
      myName = getColoredTextAsPlainText(presentation);
    }
    myColor = presentation.getForcedTextForeground();
    boolean updated = !presentation.equals(before);

    var updatedPresentation = myUpdatedPresentation;
    if (updatedPresentation == null) {
      updatedPresentation = createPresentation();
    } else {
      updatedPresentation = updatedPresentation.clone();
    }

    updatedPresentation.copyFrom(presentation);

    final var templatePresentation = myTemplatePresentation;
    if (templatePresentation != null) {
      updatedPresentation.applyFrom(templatePresentation);
    }

    updated |= updatedPresentation.isChanged();
    updatedPresentation.setChanged(false);

    myUpdatedPresentation = updatedPresentation;
    return updated;
  }

  private @NotNull PresentationData getUpdatedPresentation() {
    final var presentation = getPresentation().clone();
    presentation.clear();
    presentation.setBackground(computeBackgroundColor());
    update(presentation);

    if (shouldPostprocess()) {
      postprocess(presentation);
    }

    myUpdatedPresentation = presentation;
    return presentation;
  }

  protected @NotNull PresentationData createPresentation() {
    return new PresentationData();
  }

  protected void postprocess(@NotNull PresentationData date) {

  }

  protected boolean shouldPostprocess() {
    return true;
  }

  protected boolean shouldApply() {
    return true;
  }

  protected boolean shouldUpdateData() {
    return true;
  }

  @RequiresReadLock(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  protected @Nullable Color computeBackgroundColor() {
    return null;
  }

  protected abstract void update(@NotNull PresentationData presentation);

  public final @NotNull PresentationData getPresentation() {
    final var updatedPresentation = myUpdatedPresentation;
    return updatedPresentation == null ? getTemplatePresentation() : updatedPresentation;
  }

  protected final @NotNull PresentationData getTemplatePresentation() {
    var templatePresentation = myTemplatePresentation;
    if (templatePresentation == null) {
      templatePresentation = createPresentation();
      myTemplatePresentation = templatePresentation;
    }
    return templatePresentation;
  }

  public boolean isContentHighlighted() {
    return false;
  }

  public boolean isHighlightableContentNode(@NotNull PresentableNodeDescriptor<?> kid) {
    return true;
  }

  public PresentableNodeDescriptor<?> getChildToHighlightAt(int index) {
    return null;
  }

  public boolean isParentOf(@NotNull NodeDescriptor<?> eachNode) {
    NodeDescriptor<?> eachParent = eachNode.getParentDescriptor();
    while (eachParent != null) {
      if (eachParent == this) return true;
      eachParent = eachParent.getParentDescriptor();
    }
    return false;
  }

  public boolean isAncestorOrSelf(NodeDescriptor<?> selectedNode) {
    NodeDescriptor<?> node = selectedNode;
    while (node != null) {
      if (equals(node)) return true;
      node = node.getParentDescriptor();
    }
    return false;
  }

  public @NotNull Color getHighlightColor() {
    return StartupUiUtil.isUnderDarcula() ? ColorUtil.shift(UIUtil.getTreeBackground(), 1.1) : UIUtil.getTreeBackground().brighter();
  }

  public static class ColoredFragment {
    private final @NlsSafe String myText;
    private final @NlsSafe String myToolTip;
    private final SimpleTextAttributes myAttributes;

    public ColoredFragment(@NlsSafe String aText, SimpleTextAttributes aAttributes) {
      this(aText, null, aAttributes);
    }

    public ColoredFragment(@NlsSafe String aText, @NlsSafe String toolTip, SimpleTextAttributes aAttributes) {
      myText = aText == null? "" : aText;
      myAttributes = aAttributes;
      myToolTip = toolTip;
    }

    public @NlsSafe String getToolTip() {
      return myToolTip;
    }

    public @NlsSafe String getText() {
      return myText;
    }

    public SimpleTextAttributes getAttributes() {
      return myAttributes;
    }


    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ColoredFragment that = (ColoredFragment)o;

      return
        Objects.equals(myAttributes, that.myAttributes) &&
        Objects.equals(myText, that.myText) &&
        Objects.equals(myToolTip, that.myToolTip);
    }

    @Override
    public int hashCode() {
      int result = myText != null ? myText.hashCode() : 0;
      result = 31 * result + (myToolTip != null ? myToolTip.hashCode() : 0);
      result = 31 * result + (myAttributes != null ? myAttributes.hashCode() : 0);
      return result;
    }
  }

  public @NlsSafe String getName() {
    String result = getColoredTextAsPlainText(getPresentation());
    if (result != null) {
      return result;
    }
    return myName;
  }

  @ApiStatus.Internal
  protected static @Nullable String getColoredTextAsPlainText(PresentationData presentation) {
    if (!presentation.getColoredText().isEmpty()) {
      StringBuilder result = new StringBuilder();
      for (ColoredFragment each : presentation.getColoredText()) {
        result.append(each.getText());
      }
      return result.toString();
    }
    return null;
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

/**
 * A set of properties which define inlay's behaviour. It should be provided at inlay's creation time (see
 * {@link InlayModel#addInlineElement(int, InlayProperties, EditorCustomElementRenderer)},
 * {@link InlayModel#addBlockElement(int, InlayProperties, EditorCustomElementRenderer)},
 * {@link InlayModel#addAfterLineEndElement(int, InlayProperties, EditorCustomElementRenderer)}).
 * <p>
 * This class is mutable (and not thread-safe), setter methods return the instance on which they are invoked, to enable chained invocation.
 * Values of properties in the set are captured at the time of inlay creation, and stay constant throughout inlay's lifetime. Further
 * changes to {@code InlayProperties} instance don't have any effect on the created inlay.
 * <p>
 * Some properties may have an impact only for certain types of inlays, corresponding information is provided in setter methods'
 * descriptions.
 */
public final class InlayProperties {
  private boolean myRelatesToPrecedingText;
  private boolean myShowAbove;
  private int myPriority;
  private boolean myShowWhenFolded;
  private boolean myDisableSoftWrapping;

  /**
   * Creates a default set of inlay properties.
   */
  public InlayProperties() {}

  /**
   * This property tells whether this inlay is associated with preceding or following text. This relation defines certain aspects of inlay's
   * behaviour with respect to changes in editor, e.g. when text is inserted at inlay's position, inlay will end up before the inserted text
   * if the property is {@code false} and after the text, if it is {@code true}.
   * <p>
   * Also, when {@link Caret#moveToOffset(int)} or similar offset-based method is invoked, and an inline inlay exists at the given offset,
   * caret will be positioned to the left of inlay if the property is {@code true}, and vice versa.
   * <p>
   * For block elements this value impacts their visibility on the boundary offsets of collapsed fold region. If the value is {@code true},
   * the inlay will be visible at the trailing boundary, and if the value is {@code false} - on the leading boundary.
   */
  public InlayProperties relatesToPrecedingText(boolean value) {
    myRelatesToPrecedingText = value;
    return this;
  }

  /**
   * This property applies only to 'block' inlays, and defines whether it will be shown above or below corresponding visual line.
   */
  public InlayProperties showAbove(boolean value) {
    myShowAbove = value;
    return this;
  }

  /**
   * This property impacts the visual order in which adjacent inlays are displayed. For 'inline' and 'after line end' ones, higher priority
   * means the inlay will be shown closer to the left, for 'block' ones - closer to the line of text (for inlays related to the same visual
   * line).
   */
  public InlayProperties priority(int value) {
    myPriority = value;
    return this;
  }

  /**
   * If this property is {@code true}, the inlay will be shown even if corresponding offset is in collapsed (folded) area. Applies only to
   * 'block' (inter-line) inlays.
   */
  public InlayProperties showWhenFolded(boolean value) {
    myShowWhenFolded = value;
    return this;
  }

  /**
   * 'After line end' inlays with this property won't be moved to the next visual line, even if they don't fit editor visible area. They
   * also will be displayed to the left of inlays with enabled soft wrapping, regardless of their {@link #priority(int) priorities}.
   */
  public InlayProperties disableSoftWrapping(boolean value) {
    myDisableSoftWrapping = value;
    return this;
  }

  /**
   * @see #relatesToPrecedingText(boolean)
   */
  public boolean isRelatedToPrecedingText() {
    return myRelatesToPrecedingText;
  }

  /**
   * @see #showAbove(boolean)
   */
  public boolean isShownAbove() {
    return myShowAbove;
  }

  /**
   * @see #priority(int)
   */
  public int getPriority() {
    return myPriority;
  }

  /**
   * @see #showWhenFolded(boolean)
   */
  public boolean isShownWhenFolded() {
    return myShowWhenFolded;
  }

  /**
   * @see #disableSoftWrapping(boolean)
   */
  public boolean isSoftWrappingDisabled() {
    return myDisableSoftWrapping;
  }
}

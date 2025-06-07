// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

/**
 * A set of properties that define the inlay's behavior.
 * <p>
 * The properties should be provided when the inlay is created, see:
 * <ul>
 * <li>{@link InlayModel#addInlineElement(int, InlayProperties, EditorCustomElementRenderer) InlayModel.addInlineElement},
 * <li>{@link InlayModel#addAfterLineEndElement(int, InlayProperties, EditorCustomElementRenderer) InlayModel.addAfterLineEndElement},
 * <li>{@link InlayModel#addBlockElement(int, InlayProperties, EditorCustomElementRenderer) InlayModel.addBlockElement}.
 * </ul>
 * <p>
 * This class is mutable (and not thread-safe); the modifying methods return {@code this}, to enable chained invocation.
 * <p>
 * At the time an inlay is created, the inlay gets an immutable snapshot of the properties,
 * so that further changes to the {@code InlayProperties} instance don't affect the created inlay.
 * <p>
 * Some properties may have an impact only for certain {@linkplain Inlay types of inlays},
 * see the setter methods' descriptions for details.
 */
public final class InlayProperties {
  private boolean myRelatesToPrecedingText;
  private boolean myShowAbove;
  private int myPriority;
  private boolean myShowWhenFolded;
  private boolean myDisableSoftWrapping;

  /**
   * Creates a default set of inlay properties:
   * <ul>
   * <li>The inlay is related to the text that follows (rather than to the preceding text), see {@link #relatesToPrecedingText(boolean)}.
   * <li>If it is a block inlay, it is shown below the corresponding line, see {@link #showAbove(boolean)}.
   * <li>If the corresponding text is collapsed, the inlay is hidden, see {@link #showWhenFolded(boolean)}.
   * </ul>
   */
  public InlayProperties() {}

  /**
   * Tells whether this inlay is associated with the preceding or the following text.
   * This relation affects the inlay's behavior with respect to changes in the editor.
   * For example, when a text is inserted at the inlay's position,
   * the inlay will end up before the inserted text if the property is {@code false}
   * and after the text if it is {@code true}.
   * <p>
   * Also, when {@link Caret#moveToOffset(int)} or a similar offset-based method is invoked,
   * and an inline inlay exists at the given offset,
   * the caret will be positioned to the left of the inlay if the property is {@code true}, and vice versa.
   * <p>
   * For block inlays, this value impacts the inlay's visibility on the boundary offsets of collapsed fold regions.
   * If the value is {@code true}, the inlay will be visible at the trailing boundary,
   * and if the value is {@code false}, on the leading boundary.
   */
  public InlayProperties relatesToPrecedingText(boolean value) {
    myRelatesToPrecedingText = value;
    return this;
  }

  /**
   * This property applies only to 'block' inlays
   * and defines whether it will be shown above or below the corresponding visual line.
   */
  public InlayProperties showAbove(boolean value) {
    myShowAbove = value;
    return this;
  }

  /**
   * This property impacts the visual order in which adjacent inlays are displayed.
   * <p>
   * For inline and after-line-end inlays, higher priority means
   * the inlay will be shown closer to the left.
   * <p>
   * For block inlays, higher priority means closer to the line of text
   * (for inlays related to the same visual line).
   */
  public InlayProperties priority(int value) {
    myPriority = value;
    return this;
  }

  /**
   * For block inlays that have this property set to {@code true},
   * the inlay is shown even if the corresponding offset is in a collapsed (folded) area.
   */
  public InlayProperties showWhenFolded(boolean value) {
    myShowWhenFolded = value;
    return this;
  }

  /**
   * For after-line-end inlays that have this property set to {@code true},
   * the inlay is not moved to the next visual line,
   * even if it doesn't fit the editor's visible area.
   * <p>
   * They also will be displayed to the left of inlays with enabled soft wrapping,
   * regardless of their {@link #priority(int) priorities}.
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

  @Override
  public String toString() {
    return "InlayProperties{" +
           "myRelatesToPrecedingText=" + myRelatesToPrecedingText +
           ", myShowAbove=" + myShowAbove +
           ", myPriority=" + myPriority +
           ", myShowWhenFolded=" + myShowWhenFolded +
           ", myDisableSoftWrapping=" + myDisableSoftWrapping +
           '}';
  }
}

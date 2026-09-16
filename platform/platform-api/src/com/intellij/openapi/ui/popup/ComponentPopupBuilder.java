// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ActiveComponent;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * A builder for a {@link JBPopup} that shows a custom Swing component.
 * <p>
 * Get an instance from {@link JBPopupFactory#createComponentPopupBuilder}:
 * <pre>{@code
 * JBPopup popup = JBPopupFactory.getInstance()
 *   .createComponentPopupBuilder(content, preferredFocusedComponent)
 *   .setRequestFocus(true)
 *   .createPopup();
 * popup.showInBestPositionFor(dataContext);
 * }</pre>
 * Every setter returns the same builder. {@link #createPopup} then makes the popup,
 * and a {@code show} method of the popup makes it visible.
 */
@ApiStatus.NonExtendable
public interface ComponentPopupBuilder {
  /**
   * Shows the text in the popup header.
   * The default is an empty text, which keeps the header, but shows no title.
   */
  @NotNull
  ComponentPopupBuilder setTitle(@NlsContexts.PopupTitle String title);

  /**
   * Lets the user resize the popup.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setResizable(boolean forceResizable);

  /**
   * Lets the user drag the popup by its header.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setMovable(boolean forceMovable);

  /**
   * Moves the focus into the popup when it opens.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setRequestFocus(boolean requestFocus);

  /**
   * Makes the popup window focusable.
   * A popup that is not focusable never takes the focus, even with {@link #setRequestFocus}.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setFocusable(boolean focusable);

  /**
   * Computes the condition at once, then applies the result as {@link #setRequestFocus}.
   */
  @NotNull
  ComponentPopupBuilder setRequestFocusCondition(@NotNull Project project, @NotNull Condition<? super Project> condition);

  /**
   * Saves the popup size under the key, and the location too if {@code useForXYLocation} is {@code true}.
   * The default is no key, so the popup keeps neither size nor location.
   * The key also sets the project, like {@link #setProject}.
   *
   * @see com.intellij.openapi.util.DimensionService
   */
  @NotNull
  ComponentPopupBuilder setDimensionServiceKey(@Nullable Project project, @NonNls String key, boolean useForXYLocation);

  /**
   * Asks the callback before the popup closes.
   * The callback returns {@code true} to let the popup close.
   * The default is no callback.
   */
  @NotNull
  ComponentPopupBuilder setCancelCallback(@NotNull Computable<Boolean> shouldProceed);

  /**
   * Closes the popup on a click outside of it.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setCancelOnClickOutside(boolean cancel);

  /**
   * Adds a listener for the popup show and close events.
   */
  @NotNull
  ComponentPopupBuilder addListener(@NotNull JBPopupListener listener);

  /**
   * Closes the popup when the checker accepts a mouse move or a mouse press outside of it.
   * The checker gets the events only after the mouse enters the popup one time.
   * The default is no checker.
   */
  @NotNull
  ComponentPopupBuilder setCancelOnMouseOutCallback(@NotNull MouseChecker shouldCancel);

  /**
   * Creates the popup.
   * Call one of the {@code show} methods of {@link JBPopup} to make it visible.
   */
  @NotNull
  JBPopup createPopup();

  /**
   * Shows a close button at the right end of the header.
   * It replaces {@link #setCommandButton}, and {@link #setCouldPin} replaces it.
   * The default is no button.
   */
  @NotNull
  ComponentPopupBuilder setCancelButton(@NotNull IconButton cancelButton);

  /**
   * Closes the popup when another window opens or gets the focus.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setCancelOnOtherWindowOpen(boolean cancelOnWindow);

  /**
   * Shows the icon before the title text.
   * The default is an empty icon.
   */
  @NotNull
  ComponentPopupBuilder setTitleIcon(@NotNull ActiveIcon icon);

  /**
   * Closes the popup on the Escape key.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setCancelKeyEnabled(boolean enabled);

  /**
   * Places the content, and not the window, at the target point.
   * The popup then moves up by the height of the header.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setLocateByContent(boolean byContent);

  /**
   * Keeps the popup inside the screen bounds.
   * The default is {@code true}. The Wayland toolkit always ignores this flag.
   */
  @NotNull
  ComponentPopupBuilder setLocateWithinScreenBounds(boolean within);

  /**
   * Sets the minimum popup size.
   * The default is no minimum, so the preferred size of the content applies.
   */
  @NotNull
  ComponentPopupBuilder setMinSize(Dimension minSize);

  /**
   * Use this method if you need the popup to have the same width as the owner,
   * @see JBPopup#show(Component) for the meaning of the owner.
   * <p>
   * Note that setting owner.getWidth() to popup beforehand won't work in remote development scenario.
   * <p>
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setStretchToOwnerWidth(boolean stretchToOwnerWidth);

  /**
   * Use this method if you need the popup to have the same height as the owner,
   * @see JBPopup#show(Component) for the meaning of the owner.
   * <p>
   * Note that setting owner.getHeight() to popup beforehand won't work in remote development scenario.
   * <p>
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setStretchToOwnerHeight(boolean stretchToOwnerHeight);

  /**
   * Use this method to customize shape of popup window (e.g. to use bounded corners).
   * <p>
   * The default is no provider, so the popup keeps the rectangular shape.
   */
  @SuppressWarnings("UnusedDeclaration")//used in 'Presentation Assistant' plugin
  @NotNull
  ComponentPopupBuilder setMaskProvider(MaskProvider maskProvider);

  /**
   * Sets the window transparency, from {@code 0.0f} for an opaque popup to {@code 1.0f} for an invisible one.
   * The default is {@code 0.0f}. A window manager that has no alpha mode ignores the value.
   */
  @NotNull
  ComponentPopupBuilder setAlpha(float alpha);

  /**
   * Pushes the popup on the global popup stack, so the Escape key closes the popups in order.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setBelongsToGlobalPopupStack(boolean isInStack);

  /**
   * Binds the popup to the project.
   * The default is no project.
   */
  @NotNull
  ComponentPopupBuilder setProject(Project project);

  /**
   * Adds an object that {@link JBPopup#getUserData} then finds by its class.
   * The default is no user data.
   */
  @NotNull
  ComponentPopupBuilder addUserData(Object object);

  /**
   * Marks the popup content as a modal context, which an action reads with {@link JBPopup#isModalContext}.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setModalContext(boolean modal);

  /**
   * Adds the components that count as the popup focus for {@link JBPopup#isFocused}.
   * The default is an empty array.
   */
  @NotNull
  ComponentPopupBuilder setFocusOwners(Component @NotNull [] focusOwners);

  /**
   * Adds "advertising" text to the bottom (e.g.: hints in code completion popup).
   * <p>
   * The default is no text, so the popup shows no bottom row.
   */
  @NotNull
  ComponentPopupBuilder setAdText(@Nullable @PopupAdvertisement String text);

  /**
   * Adds "advertising" text to the bottom with the given {@link javax.swing.SwingConstants} alignment.
   * The default alignment is {@link javax.swing.SwingConstants#LEFT}.
   */
  @NotNull
  ComponentPopupBuilder setAdText(@Nullable @PopupAdvertisement String text, int textAlignment);

  /**
   * Puts the component in the bottom row, in place of the {@link #setAdText} label.
   * The default is no component.
   */
  @NotNull
  ComponentPopupBuilder setAdvertiser(@Nullable JComponent advertiser);

  /**
   * Paints a shadow around the popup.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setShowShadow(boolean show);

  /**
   * Shows the component at the right end of the header.
   * Both {@link #setCancelButton} and {@link #setCouldPin} replace it.
   * The default is no button.
   */
  @NotNull
  ComponentPopupBuilder setCommandButton(@NotNull ActiveComponent commandButton);

  /**
   * Shows the "open in tool window" button at the right end of the header, which calls the callback.
   * It replaces both {@link #setCommandButton} and {@link #setCancelButton}.
   * The default is no button.
   */
  @NotNull
  ComponentPopupBuilder setCouldPin(@Nullable Processor<? super JBPopup> callback);

  /**
   * Registers the listeners on the popup content with {@link JComponent#WHEN_IN_FOCUSED_WINDOW}.
   * The default is an empty list.
   */
  @NotNull
  ComponentPopupBuilder setKeyboardActions(@NotNull List<? extends Pair<ActionListener, KeyStroke>> keyboardActions);

  /**
   * Adds the component to the left side of the header, for example an action toolbar.
   * The default is no component.
   */
  @NotNull
  ComponentPopupBuilder setSettingButtons(@NotNull Component button);

  /**
   * Lets a dialog use the popup window as its parent.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setMayBeParent(boolean mayBeParent);

  /**
   * Closes the popup when its window loses the activation.
   * The default is {@code true}.
   */
  ComponentPopupBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation);

  /**
   * Allows defining custom strategy for processing {@link JBPopup#dispatchKeyEvent(KeyEvent)}.
   * <p>
   * The handler returns {@code true} to consume the event.
   * The default is no handler.
   */
  @NotNull ComponentPopupBuilder setKeyEventHandler(@NotNull BooleanFunction<? super KeyEvent> handler);

  /**
   * Paints the popup border.
   * The default is {@code true}.
   */
  @NotNull
  ComponentPopupBuilder setShowBorder(boolean show);

  /**
   * Shows the popup as a window of the normal level, and not of the popup level.
   * The default is {@code false}.
   */
  @NotNull
  ComponentPopupBuilder setNormalWindowLevel(boolean b);

  /**
   * Allows the popup header to be always active.
   * <p>
   *   Useful for long-living popups that may need to accept input even if when inactive.
   *   Normally it's not necessary to set this flag, because the popup will become active, for example, if clicked.
   *   But in some environments (Wayland!) it may not happen automatically.
   * </p>
   * @param b the new value (the default is {@code false})
   * @return this builder
   */
  @NotNull
  ComponentPopupBuilder setHeaderAlwaysFocusable(boolean b);

  /**
   * Paints the popup border with the color.
   * The default is the color of the theme.
   */
  default @NotNull ComponentPopupBuilder setBorderColor(Color color) {
    return this;
  }

  /**
   * Set a handler to be called when popup is closed via {@link JBPopup#closeOk(InputEvent)}.
   * <p>
   * The default is no handler.
   */
  @NotNull
  ComponentPopupBuilder setOkHandler(@Nullable Runnable okHandler);
}

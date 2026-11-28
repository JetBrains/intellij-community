// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.DynamicBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.platform.icons.IconDescriptor;
import com.intellij.platform.icons.scale.IconScale;
import com.intellij.platform.icons.swing.SwingIconKt;
import com.intellij.util.BitUtil;
import com.intellij.util.SmartFMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeListenerProxy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * The presentation of an action in a specific place in the user interface.
 *
 * @see AnAction
 * @see ActionPlaces
 */
public final class Presentation implements Cloneable {
  private static final Logger LOG = Logger.getInstance(Presentation.class);

  public static final Supplier<String> NULL_STRING = () -> null;
  public static final Supplier<TextWithMnemonic> NULL_TEXT_WITH_MNEMONIC = () -> null;

  // Property keys for the PropertyChangeListener API
  public static final @NonNls String PROP_TEXT = "text";
  public static final @NonNls String PROP_TEXT_WITH_SUFFIX = "textWithSuffix";
  public static final @NonNls String PROP_MNEMONIC_KEY = "mnemonicKey";
  public static final @NonNls String PROP_MNEMONIC_INDEX = "mnemonicIndex";
  public static final @NonNls String PROP_DESCRIPTION = "description";
  public static final @NonNls String PROP_ICON = "icon";
  /** value: {@link IconDescriptor} descriptor, see {@link #getIconDescriptor()} */
  @ApiStatus.Experimental
  public static final @NonNls String PROP_ICON_DESCRIPTOR = "iconDescriptor";
  /** value: {@link IconDescriptor} descriptor, see {@link #getHoveredIconDescriptor()} */
  @ApiStatus.Experimental
  public static final @NonNls String PROP_HOVERED_ICON_DESCRIPTOR = "hoveredIconDescriptor";
  /** value: {@link IconDescriptor} descriptor, see {@link #getSelectedIconDescriptor()} */
  @ApiStatus.Experimental
  public static final @NonNls String PROP_SELECTED_ICON_DESCRIPTOR = "selectedIconDescriptor";
  public static final @NonNls String PROP_DISABLED_ICON = "disabledIcon";
  public static final @NonNls String PROP_SELECTED_ICON = "selectedIcon";
  public static final @NonNls String PROP_HOVERED_ICON = "hoveredIcon";
  public static final @NonNls String PROP_VISIBLE = "visible";
  public static final @NonNls String PROP_ENABLED = "enabled";
  // Do not add Key constants here, especially with PROP_ prefix. Find a better place.

  private static final int IS_ENABLED = 0x1;
  private static final int IS_VISIBLE = 0x2;
  private static final int IS_KEEP_POPUP_IF_REQUESTED = 0x4;
  private static final int IS_KEEP_POPUP_IF_PREFERRED = 0x8;
  private static final int IS_POPUP_GROUP = 0x10;
  private static final int IS_PERFORM_GROUP = 0x20;
  private static final int IS_HIDE_GROUP_IF_EMPTY = 0x40;
  private static final int IS_DISABLE_GROUP_IF_EMPTY = 0x80;
  private static final int IS_APPLICATION_SCOPE = 0x100;
  private static final int IS_PREFER_INJECTED_PSI = 0x200;
  private static final int IS_ENABLED_IN_MODAL_CONTEXT = 0x400;
  private static final int IS_RW_LOCK_REQUIRED = 0x800;
  private static final int IS_TEMPLATE = 0x1000;

  private int myFlags = IS_ENABLED | IS_VISIBLE | IS_DISABLE_GROUP_IF_EMPTY | IS_RW_LOCK_REQUIRED;
  private @NotNull Supplier<@ActionDescription String> descriptionSupplier = NULL_STRING;
  private @NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier = NULL_TEXT_WITH_MNEMONIC;
  private @NotNull SmartFMap<String, Object> myUserMap = SmartFMap.emptyMap();

  private @Nullable Supplier<? extends @Nullable Icon> icon;
  private Icon disabledIcon;
  private Icon hoveredIcon;
  private Icon selectedIcon;

  /**
   * The descriptor of the experimental icons pipeline: set by {@link #setIconDescriptorSupplier}, or a
   * {@link BridgedIconDescriptor} of the legacy icon that {@link #setIcon} or {@link #setIconSupplier} set later.
   */
  private @Nullable Supplier<? extends @Nullable IconDescriptor> iconDescriptor;
  /** The descriptor of a presentation whose icon was removed with {@link #setIconDescriptor}, while a legacy icon is set. */
  private static final Supplier<@Nullable IconDescriptor> NO_ICON_DESCRIPTOR = () -> null;
  private @Nullable IconDescriptor hoveredIconDescriptor;
  private @Nullable IconDescriptor selectedIconDescriptor;
  /**
   * The last Swing icon that {@link #getIcon} created from the main descriptor under the experimental icons pipeline, and that
   * descriptor. The descriptors of the hovered and the selected state do not use it.
   */
  private @Nullable SwingIconOfDescriptor lastDescriptorSwingIcon;

  private @NotNull FList<PropertyChangeListener> myListeners = FList.emptyList();

  private static final @NotNull NotNullLazyValue<Boolean> outRemoveMnemonics = NotNullLazyValue.createValue(() -> {
    return SystemInfoRt.isMac && DynamicBundle.LanguageBundleEP.EP_NAME.hasAnyExtensions();
  });

  public static @NotNull Presentation newTemplatePresentation() {
    Presentation presentation = new Presentation();
    presentation.myFlags = BitUtil.set(presentation.myFlags, IS_TEMPLATE, true);
    return presentation;
  }

  public Presentation() {
  }

  public Presentation(@NotNull @ActionText String text) {
    TextWithMnemonic textWithMnemonic = TextWithMnemonic.fromPlainText(text);
    textWithMnemonicSupplier = () -> textWithMnemonic;
  }

  public Presentation(@NotNull Supplier<@ActionText String> dynamicText) {
    textWithMnemonicSupplier = () -> TextWithMnemonic.fromPlainText(dynamicText.get());
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    myListeners = myListeners.prepend(l);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    myListeners = myListeners.without(l);
  }

  /**
   * DO NOT USE as <code>presentation1.setText(presentation2.getText())</code>,
   * this will skip mnemonic and might break for texts with <code>'_'</code> or <code>'&'</code> symbols.
   * <p>
   * Use <code>presentation1.setTextWithMnemonic(presentation2.getTextWithPossibleMnemonic())</code>
   * or  <code>presentation1.setText(presentation2.getTextWithMnemonic())</code> to copy text between two presentations.
   * Use <code>presentation1.setText(presentation2.getText(), false)</code> to copy text without mnemonic.
   * <p>
   * This applies to AnAction constructors.
   *
   * @return Text without mnemonic.
   */
  public @ActionText String getText() {
    return getText(false);
  }

  public @ActionText String getText(boolean withSuffix) {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.getText(withSuffix);
  }

  @ApiStatus.Internal
  public boolean hasText() {
    return textWithMnemonicSupplier.get() != null;
  }

  /**
   * Sets the presentation text.
   *
   * @param text               presentation text. Use it if you need to localize text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Title) Supplier<String> text, boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(text, mayContainMnemonic));
  }

  /**
   * Sets the presentation text.
   *
   * @param text               presentation text.
   * @param mayContainMnemonic if true, the text has {@linkplain TextWithMnemonic#parse(String) text-with-mnemonic} format, otherwise
   *                           it's a plain text and no mnemonic will be used.
   */
  public void setText(@Nullable @ActionText String text, boolean mayContainMnemonic) {
    setTextWithMnemonic(getTextWithMnemonic(() -> text, mayContainMnemonic));
  }

  public @NotNull Supplier<TextWithMnemonic> getTextWithMnemonic(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> text,
                                                                 boolean mayContainMnemonic) {
    if (text == NULL_STRING) {
      return NULL_TEXT_WITH_MNEMONIC;
    }
    else if (mayContainMnemonic) {
      return () -> {
        String s = text.get();
        if (s == null) {
          return null;
        }
        TextWithMnemonic parsed = TextWithMnemonic.parse(s);
        UISettings uiSettings = UISettings.getInstanceOrNull();
        boolean mnemonicsDisabled = uiSettings != null && uiSettings.getDisableMnemonicsInControls();
        return mnemonicsDisabled ? parsed.dropMnemonic(outRemoveMnemonics.getValue()) : parsed;
      };
    }
    else {
      return () -> {
        String s = text.get();
        return s == null ? null : TextWithMnemonic.fromPlainText(s);
      };
    }
  }

  /**
   * Sets the presentation text
   *
   * @param textWithMnemonicSupplier text with mnemonic to set
   */
  public void setTextWithMnemonic(@NotNull Supplier<TextWithMnemonic> textWithMnemonicSupplier) {
    if (myListeners.isEmpty()) {
      this.textWithMnemonicSupplier = textWithMnemonicSupplier;
      return;
    }

    String oldText = getText();
    String oldTextWithSuffix = getText(true);
    int oldMnemonic = getMnemonic();
    int oldIndex = getDisplayedMnemonicIndex();
    this.textWithMnemonicSupplier = textWithMnemonicSupplier;

    fireObjectPropertyChange(PROP_TEXT, oldText, getText());
    fireObjectPropertyChange(PROP_TEXT_WITH_SUFFIX, oldTextWithSuffix, getText(true));
    fireObjectPropertyChange(PROP_MNEMONIC_KEY, oldMnemonic, getMnemonic());
    fireObjectPropertyChange(PROP_MNEMONIC_INDEX, oldIndex, getDisplayedMnemonicIndex());
  }

  /**
   * Sets the text with mnemonic.
   *
   * @see #setText(String, boolean)
   */
  public void setText(@Nullable @ActionText String text) {
    setText(() -> text, true);
  }

  /**
   * Sets the text with mnemonic supplier. Use it if you need to localize text.
   */
  public void setText(@NotNull @Nls(capitalization = Nls.Capitalization.Title) Supplier<String> text) {
    setText(text, true);
  }

  /**
   * Sets {@code supplier} as the fallback of the text set before: the text is computed on read,
   * and {@code supplier} is called when the text set before is {@code null}.
   * Neither supplier is evaluated and listeners are not notified,
   * so it is meant for a template presentation while the actions are registered.
   */
  @ApiStatus.Internal
  public void initFallbackText(@NotNull Supplier<String> supplier) {
    Supplier<TextWithMnemonic> original = textWithMnemonicSupplier;
    Supplier<TextWithMnemonic> fallback = getTextWithMnemonic(supplier, true);
    textWithMnemonicSupplier = () -> {
      TextWithMnemonic result = original.get();
      return result == null ? fallback.get() : result;
    };
  }

  /**
   * @return the text with mnemonic, properly escaped, so it could be passed to {@link #setText(String)} (e.g. to copy the presentation).
   */
  public @ActionText @Nullable String getTextWithMnemonic() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? null : textWithMnemonic.toString();
  }

  public @NotNull Supplier<TextWithMnemonic> getTextWithPossibleMnemonic() {
    return textWithMnemonicSupplier;
  }

  public void restoreTextWithMnemonic(Presentation presentation) {
    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
  }

  public @ActionDescription String getDescription() {
    return descriptionSupplier.get();
  }

  public void setDescription(@NotNull Supplier<@ActionDescription String> dynamicDescription) {
    if (myListeners.isEmpty()) {
      descriptionSupplier = dynamicDescription;
      return;
    }
    Supplier<String> oldDescription = descriptionSupplier;
    descriptionSupplier = dynamicDescription;
    fireObjectPropertyChange(PROP_DESCRIPTION, oldDescription.get(), descriptionSupplier.get());
  }

  public void setDescription(@ActionDescription String description) {
    if (myListeners.isEmpty()) {
      descriptionSupplier = () -> description;
      return;
    }
    Supplier<String> oldDescriptionSupplier = descriptionSupplier;
    descriptionSupplier = () -> description;
    fireObjectPropertyChange(PROP_DESCRIPTION, oldDescriptionSupplier.get(), description);
  }

  /**
   * Sets {@code supplier} as the description, with the description set before as its fallback.
   * The description is computed on read: {@link #getDescription()} calls {@code supplier}.
   * When {@code supplier} returns an empty description, the description set before is returned, unless it is {@code null}.
   * Neither supplier is evaluated and listeners are not notified,
   * so it is meant for a template presentation while the actions are registered.
   */
  @ApiStatus.Internal
  public void initDescriptionWithFallback(@NotNull Supplier<@ActionDescription String> supplier) {
    Supplier<String> fallback = descriptionSupplier;
    descriptionSupplier = () -> {
      String description = supplier.get();
      if (Strings.isNotEmpty(description)) {
        return description;
      }
      String fallbackDescription = fallback.get();
      return fallbackDescription == null ? description : fallbackDescription;
    };
  }

  /**
   * Under the experimental icons pipeline, the icon follows the descriptor. A descriptor that bridges the legacy icon gives
   * that icon itself, so {@code getIcon()} returns the icon that {@link #setIcon} set. Another descriptor gives a Swing icon
   * of it, and the same instance while the descriptor stays equal.
   */
  public @Nullable Icon getIcon() {
    if (ExperimentalIconRendering.isEnabled()) {
      Supplier<? extends IconDescriptor> iconDescriptor = this.iconDescriptor;
      if (iconDescriptor == null || iconDescriptor instanceof BridgedIconDescriptor) {
        // the descriptor is a bridge of the legacy icon, or the icon was set while the pipeline was off
        Supplier<? extends Icon> icon = this.icon;
        return icon == null ? null : icon.get();
      }
      return toSwingIcon(iconDescriptor.get());
    }
    Supplier<? extends Icon> icon = this.icon;
    return icon == null ? null : icon.get();
  }

  private @Nullable Icon toSwingIcon(@Nullable IconDescriptor descriptor) {
    if (descriptor == null) {
      return null;
    }
    SwingIconOfDescriptor cached = lastDescriptorSwingIcon;
    if (cached != null && cached.descriptor.equals(descriptor)) {
      return cached.icon;
    }
    Icon icon = SwingIconKt.createSwingIcon(descriptor, IconScale.Default);
    lastDescriptorSwingIcon = new SwingIconOfDescriptor(descriptor, icon);
    return icon;
  }

  private record SwingIconOfDescriptor(@NotNull IconDescriptor descriptor, @NotNull Icon icon) {
  }

  public @Nullable Supplier<? extends @Nullable Icon> getIconSupplier() {
    return icon;
  }

  /**
   * Icon descriptor for the experimental icons pipeline (see {@link ExperimentalIconRendering}).
   *
   * @return {@code null} when the pipeline is off; otherwise the descriptor, a descriptor bridged from the legacy icon when
   * only an icon is set, or {@code null} when there is neither or {@link #setIconDescriptor} removed the icon
   */
  @ApiStatus.Experimental
  public @Nullable IconDescriptor getIconDescriptor() {
    if (!ExperimentalIconRendering.isEnabled()) {
      return null;
    }
    Supplier<? extends IconDescriptor> iconDescriptor = this.iconDescriptor;
    if (iconDescriptor != null) {
      return iconDescriptor.get();
    }
    // The icon was set while the pipeline was off. Convert it on each call, and keep no state for this rare case.
    Supplier<? extends Icon> iconSupplier = this.icon;
    Icon icon = iconSupplier == null ? null : iconSupplier.get();
    return icon == null ? null : SwingIconKt.toIconDescriptor(icon);
  }

  /** The raw supplier, not gated by {@link ExperimentalIconRendering}. */
  @ApiStatus.Experimental
  public @Nullable Supplier<? extends @Nullable IconDescriptor> getIconDescriptorSupplier() {
    return iconDescriptor;
  }

  @ApiStatus.Experimental
  public void setIconDescriptor(@Nullable IconDescriptor iconDescriptor) {
    setIconDescriptorSupplier(iconDescriptor == null ? null : () -> iconDescriptor);
  }

  /**
   * Sets the descriptor supplier. Under the experimental icons pipeline, {@code null} removes the icon, also when a legacy
   * icon is set: {@link #getIconDescriptor} does not bridge that icon again. The pipeline derives {@link #getIcon} from the
   * descriptor, so a change fires both {@link #PROP_ICON_DESCRIPTOR} and {@link #PROP_ICON}.
   */
  @ApiStatus.Experimental
  public void setIconDescriptorSupplier(@Nullable Supplier<? extends @Nullable IconDescriptor> iconDescriptor) {
    if (!ExperimentalIconRendering.isEnabled()) {
      this.iconDescriptor = iconDescriptor;
      return;
    }

    Supplier<? extends @Nullable IconDescriptor> value = iconDescriptor == null && this.icon != null ? NO_ICON_DESCRIPTOR : iconDescriptor;
    if (myListeners.isEmpty()) {
      this.iconDescriptor = value;
      return;
    }

    // the values that getIconDescriptor and getIcon give, so that the events match what a listener reads
    IconDescriptor oldValue = getIconDescriptor();
    Icon oldIcon = getIcon();
    this.iconDescriptor = value;
    IconDescriptor newValue = getIconDescriptor();
    if (Objects.equals(oldValue, newValue)) return;
    fireObjectPropertyChange(PROP_ICON_DESCRIPTOR, oldValue, newValue);
    fireObjectPropertyChange(PROP_ICON, oldIcon, getIcon());
  }

  /**
   * Icon descriptor for the hovered state in the experimental icons pipeline.
   * This method does not read {@link ExperimentalIconRendering}. A component calls it only while the pipeline is on.
   *
   * @return the descriptor, or {@code null} when none is set. A component then uses the base descriptor.
   */
  @ApiStatus.Experimental
  public @Nullable IconDescriptor getHoveredIconDescriptor() {
    return hoveredIconDescriptor;
  }

  /**
   * Sets the icon descriptor for the hovered state in the experimental icons pipeline.
   * The presentation fires {@link #PROP_HOVERED_ICON_DESCRIPTOR} when the value changes while the pipeline is on.
   *
   * @param hoveredIconDescriptor the descriptor, or {@code null} to use the base descriptor.
   */
  @ApiStatus.Experimental
  public void setHoveredIconDescriptor(@Nullable IconDescriptor hoveredIconDescriptor) {
    if (myListeners.isEmpty() || !ExperimentalIconRendering.isEnabled()) {
      this.hoveredIconDescriptor = hoveredIconDescriptor;
      return;
    }

    IconDescriptor old = this.hoveredIconDescriptor;
    this.hoveredIconDescriptor = hoveredIconDescriptor;
    fireObjectPropertyChange(PROP_HOVERED_ICON_DESCRIPTOR, old, this.hoveredIconDescriptor);
  }

  /**
   * Icon descriptor for the selected state in the experimental icons pipeline.
   * This method does not read {@link ExperimentalIconRendering}. A component calls it only while the pipeline is on.
   *
   * @return the descriptor, or {@code null} when none is set. A component then uses the base descriptor.
   */
  @ApiStatus.Experimental
  public @Nullable IconDescriptor getSelectedIconDescriptor() {
    return selectedIconDescriptor;
  }

  /**
   * Sets the icon descriptor for the selected state in the experimental icons pipeline.
   * The presentation fires {@link #PROP_SELECTED_ICON_DESCRIPTOR} when the value changes while the pipeline is on.
   *
   * @param selectedIconDescriptor the descriptor, or {@code null} to use the base descriptor.
   */
  @ApiStatus.Experimental
  public void setSelectedIconDescriptor(@Nullable IconDescriptor selectedIconDescriptor) {
    if (myListeners.isEmpty() || !ExperimentalIconRendering.isEnabled()) {
      this.selectedIconDescriptor = selectedIconDescriptor;
      return;
    }

    IconDescriptor old = this.selectedIconDescriptor;
    this.selectedIconDescriptor = selectedIconDescriptor;
    fireObjectPropertyChange(PROP_SELECTED_ICON_DESCRIPTOR, old, this.selectedIconDescriptor);
  }

  /**
   * Sets the properties of {@code other} as the fallback of the properties that this presentation does not set.
   * The icon and the icon descriptor are copied when this presentation has none.
   * The text and the description are computed on read: the value of this presentation is returned when it is not empty,
   * otherwise the value of {@code other} is returned when it is not empty.
   * The user data of {@code other} is added to this presentation.
   * Neither supplier is evaluated and listeners are not notified,
   * so it is meant for a template presentation while an action stub becomes an action.
   */
  @ApiStatus.Internal // do not expose
  public void copyUnsetTemplateProperties(@NotNull Presentation other) {
    if (icon == null) {
      icon = other.icon;
    }
    if (iconDescriptor == null) {
      iconDescriptor = other.iconDescriptor;
    }
    Supplier<TextWithMnemonic> ownText = textWithMnemonicSupplier;
    Supplier<TextWithMnemonic> otherText = other.textWithMnemonicSupplier;
    textWithMnemonicSupplier = () -> {
      TextWithMnemonic result = ownText.get();
      if (result != null && !result.getText().isEmpty()) {
        return result;
      }
      TextWithMnemonic fallback = otherText.get();
      return fallback != null && !fallback.getText().isEmpty() ? fallback : result;
    };
    Supplier<@ActionDescription String> ownDescription = descriptionSupplier;
    Supplier<@ActionDescription String> otherDescription = other.descriptionSupplier;
    descriptionSupplier = () -> {
      String result = ownDescription.get();
      if (Strings.isNotEmpty(result)) {
        return result;
      }
      String fallback = otherDescription.get();
      return Strings.isNotEmpty(fallback) ? fallback : result;
    };
    myUserMap = myUserMap.plusAll(other.myUserMap);
  }

  /**
   * Sets the legacy icon. Under the experimental icons pipeline, the icon also replaces the descriptor, so that an icon set
   * in {@link AnAction#update} wins over the descriptor of the template presentation. To set both, set the icon first.
   */
  public void setIcon(@Nullable Icon icon) {
    Supplier<Icon> supplier = icon == null ? null : () -> icon;
    if (myListeners.isEmpty()) {
      this.icon = supplier;
      bridgeIconDescriptor(supplier, icon);
      return;
    }

    // the values of getIcon: under the experimental icons pipeline, the icon follows the descriptor
    Icon oldIcon = getIcon();
    Supplier<? extends @Nullable IconDescriptor> oldDescriptor = iconDescriptor;
    this.icon = supplier;
    bridgeIconDescriptor(supplier, icon);
    fireObjectPropertyChange(PROP_ICON, oldIcon, getIcon());
    fireBridgedIconDescriptorChange(oldDescriptor);
  }

  /** Sets the legacy icon supplier, see {@link #setIcon}. */
  public void setIconSupplier(@Nullable Supplier<? extends @Nullable Icon> icon) {
    if (myListeners.isEmpty()) {
      this.icon = icon;
      bridgeIconDescriptor(icon, null);
      return;
    }

    // the values of getIcon: under the experimental icons pipeline, the icon follows the descriptor
    Icon oldIcon = getIcon();
    Supplier<? extends @Nullable IconDescriptor> oldDescriptor = iconDescriptor;
    this.icon = icon;
    bridgeIconDescriptor(icon, null);
    Icon newIcon = getIcon();
    if (!Objects.equals(oldIcon, newIcon)) {
      fireObjectPropertyChange(PROP_ICON, oldIcon, newIcon);
    }
    fireBridgedIconDescriptorChange(oldDescriptor);
  }

  /**
   * Fires {@link #PROP_ICON_DESCRIPTOR} if {@link #bridgeIconDescriptor} replaced the descriptor {@code oldDescriptor} with a
   * descriptor of another value. The legacy icon can stay equal while the descriptor changes, for example when an icon
   * replaces an explicit descriptor.
   */
  private void fireBridgedIconDescriptorChange(@Nullable Supplier<? extends @Nullable IconDescriptor> oldDescriptor) {
    Supplier<? extends @Nullable IconDescriptor> newDescriptor = iconDescriptor;
    if (newDescriptor == oldDescriptor) return;
    IconDescriptor oldValue = oldDescriptor == null ? null : oldDescriptor.get();
    IconDescriptor newValue = newDescriptor == null ? null : newDescriptor.get();
    if (Objects.equals(oldValue, newValue)) return;
    fireObjectPropertyChange(PROP_ICON_DESCRIPTOR, oldValue, newValue);
  }

  /**
   * Sets the icon and the descriptor together, and then fires the events, so that a listener never reads one of them new
   * and the other old.
   */
  private void setIconAndDescriptor(@Nullable Supplier<? extends @Nullable Icon> icon,
                                    @Nullable Supplier<? extends @Nullable IconDescriptor> iconDescriptor) {
    if (myListeners.isEmpty()) {
      this.icon = icon;
      this.iconDescriptor = iconDescriptor;
      return;
    }

    boolean descriptorEvents = ExperimentalIconRendering.isEnabled();
    // the values of getIcon: under the experimental icons pipeline, the icon follows the descriptor
    Icon oldIcon = getIcon();
    IconDescriptor oldDescriptor = descriptorEvents ? getIconDescriptor() : null;
    this.icon = icon;
    this.iconDescriptor = iconDescriptor;
    Icon newIcon = getIcon();
    if (!Objects.equals(oldIcon, newIcon)) {
      fireObjectPropertyChange(PROP_ICON, oldIcon, newIcon);
    }
    if (descriptorEvents) {
      IconDescriptor newDescriptor = getIconDescriptor();
      if (!Objects.equals(oldDescriptor, newDescriptor)) {
        fireObjectPropertyChange(PROP_ICON_DESCRIPTOR, oldDescriptor, newDescriptor);
      }
    }
  }

  /**
   * Under the experimental icons pipeline, makes the descriptor a bridge of the legacy icon {@code source}. A bridge of the
   * same supplier, or of the same {@code constantIcon}, is kept. Does nothing, and allocates nothing, while the pipeline is off.
   */
  private void bridgeIconDescriptor(@Nullable Supplier<? extends @Nullable Icon> source, @Nullable Icon constantIcon) {
    if (!ExperimentalIconRendering.isEnabled()) return;
    if (source == null) {
      iconDescriptor = null;
      return;
    }
    if (iconDescriptor instanceof BridgedIconDescriptor bridged && bridged.isBridgeOf(source, constantIcon)) return;
    iconDescriptor = new BridgedIconDescriptor(source, constantIcon);
  }

  /**
   * The descriptor of a legacy icon supplier. It reads the supplier on each call, as {@link #getIcon} does while the
   * pipeline is off, so a supplier whose icon changes is followed. It converts an icon only once, and again only for
   * another icon.
   */
  private static final class BridgedIconDescriptor implements Supplier<@Nullable IconDescriptor> {
    private final @NotNull Supplier<? extends @Nullable Icon> source;
    private final @Nullable Icon constantIcon;
    private @Nullable Icon lastIcon;
    private @Nullable IconDescriptor lastDescriptor;

    BridgedIconDescriptor(@NotNull Supplier<? extends @Nullable Icon> source, @Nullable Icon constantIcon) {
      this.source = source;
      this.constantIcon = constantIcon;
    }

    boolean isBridgeOf(@NotNull Supplier<? extends @Nullable Icon> source, @Nullable Icon constantIcon) {
      return this.source == source || (constantIcon != null && this.constantIcon == constantIcon);
    }

    @Override
    public synchronized @Nullable IconDescriptor get() {
      Icon icon = source.get();
      if (icon == null) {
        return null;
      }
      if (icon != lastIcon) {
        lastDescriptor = SwingIconKt.toIconDescriptor(icon);
        lastIcon = icon;
      }
      return lastDescriptor;
    }
  }

  public @Nullable Icon getDisabledIcon() {
    return disabledIcon;
  }

  public void setDisabledIcon(@Nullable Icon icon) {
    Icon oldDisabledIcon = disabledIcon;
    disabledIcon = icon;
    fireObjectPropertyChange(PROP_DISABLED_ICON, oldDisabledIcon, disabledIcon);
  }

  public @Nullable Icon getHoveredIcon() {
    return hoveredIcon;
  }

  public void setHoveredIcon(@Nullable Icon hoveredIcon) {
    Icon old = this.hoveredIcon;
    this.hoveredIcon = hoveredIcon;
    fireObjectPropertyChange(PROP_HOVERED_ICON, old, this.hoveredIcon);
  }

  public @Nullable Icon getSelectedIcon() {
    return selectedIcon;
  }

  public void setSelectedIcon(@Nullable Icon selectedIcon) {
    Icon old = this.selectedIcon;
    this.selectedIcon = selectedIcon;
    fireObjectPropertyChange(PROP_SELECTED_ICON, old, this.selectedIcon);
  }

  /**
   * @return an extended key code for a mnemonic character, or {@code KeyEvent.VK_UNDEFINED} if mnemonic is not set
   */
  public int getMnemonic() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? 0 : textWithMnemonic.getMnemonicCode();
  }

  /**
   * @return a mnemonic index in the whole text, or {@code -1} if mnemonic is not set
   */
  public int getDisplayedMnemonicIndex() {
    TextWithMnemonic textWithMnemonic = textWithMnemonicSupplier.get();
    return textWithMnemonic == null ? -1 : textWithMnemonic.getMnemonicIndex();
  }

  /** @see Presentation#setVisible(boolean) */
  public boolean isVisible() {
    return BitUtil.isSet(myFlags, IS_VISIBLE);
  }

  /**
   * Sets whether the action is visible in menus, toolbars and popups or not.
   */
  public void setVisible(boolean visible) {
    assertNotTemplatePresentation();
    boolean oldVisible = BitUtil.isSet(myFlags, IS_VISIBLE);
    myFlags = BitUtil.set(myFlags, IS_VISIBLE, visible);
    fireBooleanPropertyChange(PROP_VISIBLE, oldVisible, visible);
  }

  /** @see Presentation#setPopupGroup(boolean) */
  public boolean isPopupGroup() {
    return BitUtil.isSet(myFlags, IS_POPUP_GROUP);
  }

  /**
   * For an action group presentation sets whether the action group is a popup group or not.
   * A popup action group is shown as a submenu, a toolbar button that shows a popup when clicked, etc.
   * A non-popup action group child actions are injected into the parent group.
   */
  public void setPopupGroup(boolean popup) {
    myFlags = BitUtil.set(myFlags, IS_POPUP_GROUP, popup);
  }

  /** @see Presentation#setPerformGroup(boolean) */
  public boolean isPerformGroup() {
    return BitUtil.isSet(myFlags, IS_PERFORM_GROUP);
  }

  /**
   * For an action group presentation sets whether the action group is "performable" as an ordinary action or not.
   *
   * @see com.intellij.openapi.actionSystem.ex.ActionUtil#SUPPRESS_SUBMENU
   * @see com.intellij.openapi.actionSystem.ex.ActionUtil#HIDE_DROPDOWN_ICON
   */
  public void setPerformGroup(boolean performing) {
    myFlags = BitUtil.set(myFlags, IS_PERFORM_GROUP, performing);
  }

  /** @see Presentation#setHideGroupIfEmpty(boolean) */
  public boolean isHideGroupIfEmpty() {
    return BitUtil.isSet(myFlags, IS_HIDE_GROUP_IF_EMPTY);
  }

  /**
   * For an action group presentation sets whether the action group will be hidden if no visible children are present.
   * The default is {@code false}.
   */
  public void setHideGroupIfEmpty(boolean hide) {
    myFlags = BitUtil.set(myFlags, IS_HIDE_GROUP_IF_EMPTY, hide);
  }

  /** @see Presentation#setHideGroupIfEmpty(boolean) */
  public boolean isDisableGroupIfEmpty() {
    return BitUtil.isSet(myFlags, IS_DISABLE_GROUP_IF_EMPTY);
  }

  /**
   * For an action group presentation sets whether the action group will be shown as disabled if no visible children are present.
   * The default is {@code true}.
   */
  public void setDisableGroupIfEmpty(boolean disable) {
    myFlags = BitUtil.set(myFlags, IS_DISABLE_GROUP_IF_EMPTY, disable);
  }

  /** @see Presentation#setApplicationScope(boolean) */
  public boolean isApplicationScope() {
    return BitUtil.isSet(myFlags, IS_APPLICATION_SCOPE);
  }

  /**
   * For an action presentation sets whether the action is to be performed in the application scope.
   * In the application scope, action activities can outlast a project where the action is performed.
   * The default is {@code false}.
   */
  public void setApplicationScope(boolean applicationScope) {
    myFlags = BitUtil.set(myFlags, IS_APPLICATION_SCOPE, applicationScope);
  }

  /**
   * @see Presentation#setRWLockRequired
   */
  @ApiStatus.Experimental
  public boolean isRWLockRequired() {
    if (!Registry.is("actions.allow.update.and.perform.without.rw.lock")) {
      return true;
    }
    return BitUtil.isSet(myFlags, IS_RW_LOCK_REQUIRED);
  }

  /**
   * For an action presentation sets whether the action requires {@link <a href="https://jb.gg/ij-platform-threading">the Read-Write lock</a>}.
   * <p>
   * <ul>
   *   <li>If {@code true}, the action is updated in read action and performed in write-intent read action on the EDT.
   *   Such actions may hinder the performance of the IDE, but they allow access to PSI.</li>
   *   <li>If {@code false}, the action is updated and performed without locks.
   *   Such actions can be executed and updated quickly, but they do not allow access to PSI and Document.</li>
   * </ul>
   * <p>
   * Default value is {@code true}.
   * <p>
   * This property needs to be set for {@link AnAction#getTemplatePresentation()}, because it affects {@link AnAction#update}
   */
  @ApiStatus.Experimental
  public void setRWLockRequired(boolean rwLockRequired) {
    if (!isTemplate()) {
      LOG.error("setRWLockRequired() should be called only for template presentations");
    }
    myFlags = BitUtil.set(myFlags, IS_RW_LOCK_REQUIRED, rwLockRequired);
  }

  /**
   * For an action presentation in a popup sets whether a popup is closed or kept open
   * when the action is performed.
   * <p>
   * {@link com.intellij.openapi.actionSystem.ToggleAction} use {@link KeepPopupOnPerform#Always} by default.
   * The behavior is controlled by the {@link UISettings#getKeepPopupsForToggles} property.
   *
   * @see KeepPopupOnPerform
   * @see UISettings#getKeepPopupsForToggles
   */
  public void setKeepPopupOnPerform(@NotNull KeepPopupOnPerform mode) {
    boolean requestedBit = mode == KeepPopupOnPerform.IfRequested || mode == KeepPopupOnPerform.Always;
    boolean preferredBit = mode == KeepPopupOnPerform.IfPreferred || mode == KeepPopupOnPerform.Always;
    myFlags = BitUtil.set(myFlags, IS_KEEP_POPUP_IF_REQUESTED, requestedBit);
    myFlags = BitUtil.set(myFlags, IS_KEEP_POPUP_IF_PREFERRED, preferredBit);
  }

  /** @see Presentation#setKeepPopupOnPerform(KeepPopupOnPerform) */
  public @NotNull KeepPopupOnPerform getKeepPopupOnPerform() {
    boolean requestedBit = BitUtil.isSet(myFlags, IS_KEEP_POPUP_IF_REQUESTED);
    boolean preferedBit = BitUtil.isSet(myFlags, IS_KEEP_POPUP_IF_PREFERRED);
    return requestedBit && preferedBit ? KeepPopupOnPerform.Always :
           requestedBit ? KeepPopupOnPerform.IfRequested :
           preferedBit ? KeepPopupOnPerform.IfPreferred :
           KeepPopupOnPerform.Never;
  }

  /** @see Presentation#setPreferInjectedPsi(boolean) */
  @ApiStatus.Internal
  public boolean isPreferInjectedPsi() {
    return BitUtil.isSet(myFlags, IS_PREFER_INJECTED_PSI);
  }

  /**
   * For an action presentation sets whether the action prefers to be updated and performed with the injected {@code DataContext}.
   * Injected data context returns {@link InjectedDataKeys} data for regular data keys, if present.
   * The default is {@code false}.
   */
  @ApiStatus.Internal
  public void setPreferInjectedPsi(boolean preferInjectedPsi) {
    myFlags = BitUtil.set(myFlags, IS_PREFER_INJECTED_PSI, preferInjectedPsi);
  }

  /** @see Presentation#setEnabledInModalContext(boolean) */
  @ApiStatus.Internal
  public boolean isEnabledInModalContext() {
    return BitUtil.isSet(myFlags, IS_ENABLED_IN_MODAL_CONTEXT);
  }

  /**
   * For an action presentation sets whether the action can be performed in the modal context.
   * The default is {@code false}.
   */
  @ApiStatus.Internal
  public void setEnabledInModalContext(boolean enabledInModalContext) {
    myFlags = BitUtil.set(myFlags, IS_ENABLED_IN_MODAL_CONTEXT, enabledInModalContext);
  }

  /**
   * Template presentations must be returned by {@link AnAction#getTemplatePresentation()} only.
   * Template presentations assert that their enabled and visible flags are never updated
   * because menus and shortcut processing use different defaults,
   * so values from template presentations are silently ignored.
   */
  public boolean isTemplate() {
    return BitUtil.isSet(myFlags, IS_TEMPLATE);
  }

  /** @see Presentation#setEnabled(boolean) */
  public boolean isEnabled() {
    return BitUtil.isSet(myFlags, IS_ENABLED);
  }

  /**
   * Sets whether the action is enabled or not. If an action is disabled, {@link AnAction#actionPerformed} is not be called.
   * In case when action represents a button or a menu item, the representing button or item will be greyed out.
   */
  public void setEnabled(boolean enabled) {
    assertNotTemplatePresentation();
    boolean oldEnabled = BitUtil.isSet(myFlags, IS_ENABLED);
    myFlags = BitUtil.set(myFlags, IS_ENABLED, enabled);
    fireBooleanPropertyChange(PROP_ENABLED, oldEnabled, enabled);
  }

  public void setEnabledAndVisible(boolean enabled) {
    setEnabled(enabled);
    setVisible(enabled);
  }

  private void fireBooleanPropertyChange(@NotNull String propertyName, boolean oldValue, boolean newValue) {
    if (myListeners.isEmpty() || oldValue == newValue) return;
    PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
    doFirePropertyChange(event, myListeners);
  }

  private void fireObjectPropertyChange(@NotNull String propertyName, Object oldValue, Object newValue) {
    if (myListeners.isEmpty() || Objects.equals(oldValue, newValue)) return;
    PropertyChangeEvent event = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
    doFirePropertyChange(event, myListeners);
  }

  private static void doFirePropertyChange(@NotNull PropertyChangeEvent event,
                                           @NotNull FList<PropertyChangeListener> listeners) {
    for (PropertyChangeListener listener : listeners.size() == 1 ? listeners : ContainerUtil.reverse(listeners)) {
      if (listener instanceof PropertyChangeListenerProxy p &&
          !event.getPropertyName().equals(p.getPropertyName())) {
        continue;
      }
      listener.propertyChange(event);
    }
  }

  void assertNotTemplatePresentation() {
    if (BitUtil.isSet(myFlags, IS_TEMPLATE)) {
      LOG.warnInProduction(new Throwable("Template presentations must not be used directly"));
    }
  }

  @Override
  public Presentation clone() {
    try {
      Presentation clone = (Presentation)super.clone();
      clone.myFlags = BitUtil.set(clone.myFlags, IS_TEMPLATE, false);
      clone.myListeners = FList.emptyList();
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public void copyFrom(@NotNull Presentation presentation) {
    copyFrom(presentation, null, false, false);
  }

  public void copyFrom(@NotNull Presentation presentation, @Nullable Component customComponent) {
    copyFrom(presentation, customComponent, true, false);
  }

  public void copyFrom(@NotNull Presentation presentation, @Nullable Component customComponent, boolean allFlags) {
    copyFrom(presentation, customComponent, true, allFlags);
  }

  private void copyFrom(@NotNull Presentation presentation,
                        @Nullable Component customComponent,
                        boolean forceNullComponent,
                        boolean allFlags) {
    if (presentation == this) {
      return;
    }

    boolean oldEnabled = isEnabled(), oldVisible = isVisible();
    if (allFlags) {
      myFlags = BitUtil.set(presentation.myFlags, IS_TEMPLATE, isTemplate());
    }
    else {
      myFlags = BitUtil.set(myFlags, IS_ENABLED, BitUtil.isSet(presentation.myFlags, IS_ENABLED));
      myFlags = BitUtil.set(myFlags, IS_VISIBLE, BitUtil.isSet(presentation.myFlags, IS_VISIBLE));
    }
    fireBooleanPropertyChange(PROP_ENABLED, oldEnabled, isEnabled());
    fireBooleanPropertyChange(PROP_VISIBLE, oldVisible, isVisible());

    setTextWithMnemonic(presentation.getTextWithPossibleMnemonic());
    setDescription(presentation.descriptionSupplier);

    if (lastDescriptorSwingIcon == null) {
      // the descriptor of the other presentation is usually equal, so reuse its Swing icon
      lastDescriptorSwingIcon = presentation.lastDescriptorSwingIcon;
    }
    setIconAndDescriptor(presentation.icon, presentation.iconDescriptor);

    setSelectedIcon(presentation.getSelectedIcon());
    setDisabledIcon(presentation.getDisabledIcon());
    setHoveredIcon(presentation.getHoveredIcon());

    setSelectedIconDescriptor(presentation.selectedIconDescriptor);
    setHoveredIconDescriptor(presentation.hoveredIconDescriptor);

    if (!myUserMap.equals(presentation.myUserMap)) {
      Set<String> allKeys = new HashSet<>(presentation.myUserMap.keySet());
      allKeys.addAll(myUserMap.keySet());
      if (!allKeys.isEmpty()) {
        for (String key : allKeys) {
          if (key.equals(CustomComponentAction.COMPONENT_KEY.toString()) && (customComponent != null || forceNullComponent)) {
            putClientProperty(key, customComponent);
          }
          else {
            putClientProperty(key, presentation.getClientProperty(key));
          }
        }
      }
    }
  }

  public @Nullable <T> T getClientProperty(@NotNull Key<T> key) {
    //noinspection unchecked
    return (T)myUserMap.get(key.toString());
  }

  public <T> void putClientProperty(@NotNull Key<T> key, @Nullable T value) {
    putClientProperty(key.toString(), value);
  }

  /** @deprecated Use {@link #getClientProperty(Key)} instead */
  @Deprecated
  public @Nullable Object getClientProperty(@NonNls @NotNull String key) {
    return myUserMap.get(key);
  }

  /** @deprecated Use {@link #putClientProperty(Key, Object)} instead */
  @Deprecated
  public void putClientProperty(@NonNls @NotNull String key, @Nullable Object value) {
    Object oldValue;
    synchronized (this) {
      oldValue = myUserMap.get(key);
      if (Comparing.equal(oldValue, value)) return;
      if (key.equals(CustomComponentAction.COMPONENT_KEY.toString()) && oldValue != null) {
        LOG.error("Trying to reset custom component in a presentation", new Throwable());
      }
      myUserMap = value == null ? myUserMap.minus(key) : myUserMap.plus(key, value);
    }
    fireObjectPropertyChange(key, oldValue, value);
  }

  /** @deprecated The feature is dropped. See {@link com.intellij.ide.actions.WeighingActionGroup} */
  @Deprecated(forRemoval = true)
  public void setWeight(double ignore) {
  }

  public boolean isEnabledAndVisible() {
    return isEnabled() && isVisible();
  }

  @Override
  public @NonNls String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getText()).append(" (").append(descriptionSupplier.get()).append(")");
    sb.append(", flags=[");
    int start = sb.length();
    appendFlag(myFlags, IS_TEMPLATE, sb, start, "template");
    appendFlag(myFlags, IS_ENABLED, sb, start, "enabled");
    appendFlag(myFlags, IS_VISIBLE, sb, start, "visible");
    if (BitUtil.isSet(myFlags, IS_KEEP_POPUP_IF_REQUESTED) &&
        BitUtil.isSet(myFlags, IS_KEEP_POPUP_IF_PREFERRED)) {
      appendFlag(1, 1, sb, start, "keep_popup_always");
    }
    else {
      appendFlag(myFlags, IS_KEEP_POPUP_IF_REQUESTED, sb, start, "keep_popup_if_requested");
      appendFlag(myFlags, IS_KEEP_POPUP_IF_PREFERRED, sb, start, "keep_popup_if_preferred");
    }
    appendFlag(myFlags, IS_POPUP_GROUP, sb, start, "popup_group");
    appendFlag(myFlags, IS_PERFORM_GROUP, sb, start, "perform_group");
    appendFlag(myFlags, IS_HIDE_GROUP_IF_EMPTY, sb, start, "hide_group_if_empty");
    appendFlag(myFlags, IS_DISABLE_GROUP_IF_EMPTY, sb, start, "disable_group_if_empty");
    appendFlag(myFlags, IS_APPLICATION_SCOPE, sb, start, "application_scope");
    appendFlag(myFlags, IS_PREFER_INJECTED_PSI, sb, start, "prefer_injected_psi");
    appendFlag(myFlags, IS_ENABLED_IN_MODAL_CONTEXT, sb, start, "enabled_in_modal_context");
    appendFlag(myFlags, IS_RW_LOCK_REQUIRED, sb, start, "lock_required");
    sb.append("]");
    return sb.toString();
  }

  private static void appendFlag(int flags, int mask, @NotNull StringBuilder sb, int start, @NotNull String maskName) {
    if (!BitUtil.isSet(flags, mask)) return;
    if (sb.length() > start) sb.append(", ");
    sb.append(maskName);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.intellij.openapi.editor.markup.EffectType.*;
import static com.intellij.openapi.editor.markup.TextAttributesEffectsBuilder.EffectSlot.*;

/**
 * Allows to build effects for the TextAttributes. Allows to cover effects on the current state and slip effects under it.
 */
public final class TextAttributesEffectsBuilder {
  private static final Logger LOG = Logger.getInstance(TextAttributesEffectsBuilder.class);

  public enum EffectSlot {
    FRAME_SLOT, UNDERLINE_SLOT, STRIKE_SLOT
  }

  // this probably could be a property of the EffectType
  private static final Map<EffectType, EffectSlot> EFFECT_SLOTS_MAP = ContainerUtil.<EffectType, EffectSlot>immutableMapBuilder()
    .put(STRIKEOUT, STRIKE_SLOT)
    .put(BOXED, FRAME_SLOT).put(ROUNDED_BOX, FRAME_SLOT).put(SLIGHTLY_WIDER_BOX, FRAME_SLOT)
    .put(BOLD_LINE_UNDERSCORE, UNDERLINE_SLOT)
    .put(LINE_UNDERSCORE, UNDERLINE_SLOT)
    .put(WAVE_UNDERSCORE, UNDERLINE_SLOT)
    .put(BOLD_DOTTED_LINE, UNDERLINE_SLOT)
    .build();

  private final Map<EffectSlot, EffectDescriptor> myEffectsMap = new HashMap<>(EffectSlot.values().length);

  private TextAttributesEffectsBuilder() {}

  /**
   * Creates a builder without any effects
   */
  @NotNull
  public static TextAttributesEffectsBuilder create() {
    return new TextAttributesEffectsBuilder();
  }

  /**
   * Creates a builder with effects from {@code deepestAttributes}
   */
  @NotNull
  public static TextAttributesEffectsBuilder create(@NotNull TextAttributes deepestAttributes) {
    return create().coverWith(deepestAttributes);
  }

  /**
   * Applies effects from {@code attributes} above current state of the merger. Effects may override mutually exclusive ones. E.g
   * If current state has underline and we applying attributes with wave underline, underline effect will be removed.
   */
  @NotNull
  public final TextAttributesEffectsBuilder coverWith(@NotNull TextAttributes attributes) {
    attributes.forEachAdditionalEffect(this::coverWith);
    coverWith(attributes.getEffectType(), attributes.getEffectColor());
    return this;
  }

  /**
   * Applies effects from {@code attributes} if effect slots are not used.
   */
  @NotNull
  public final TextAttributesEffectsBuilder slipUnder(@NotNull TextAttributes attributes) {
    slipUnder(attributes.getEffectType(), attributes.getEffectColor());
    attributes.forEachAdditionalEffect(this::slipUnder);
    return this;
  }

  /**
   * Applies effect with {@code effectType} and {@code effectColor} to the current state. Effects may override mutually exclusive ones. E.g
   * If current state has underline and we applying attributes with wave underline, underline effect will be removed.
   */
  @NotNull
  public TextAttributesEffectsBuilder coverWith(@Nullable EffectType effectType, @Nullable Color effectColor) {
    return mutateBuilder(effectType, effectColor, myEffectsMap::put);
  }

  /**
   * Applies effect with {@code effectType} and {@code effectColor} to the current state if effect slot is not used.
   */
  @NotNull
  public TextAttributesEffectsBuilder slipUnder(@Nullable EffectType effectType, @Nullable Color effectColor) {
    return mutateBuilder(effectType, effectColor, myEffectsMap::putIfAbsent);
  }

  @NotNull
  private TextAttributesEffectsBuilder mutateBuilder(@Nullable EffectType effectType,
                                                     @Nullable Color effectColor,
                                                     @NotNull BiConsumer<? super EffectSlot, ? super EffectDescriptor> slotMutator) {
    if (effectColor != null && effectType != null) {
      EffectSlot slot = EFFECT_SLOTS_MAP.get(effectType);
      if (slot != null) {
        slotMutator.accept(slot, EffectDescriptor.create(effectType, effectColor));
      }
      else {
        LOG.debug("Effect " + effectType + " is not supported by builder");
      }
    }
    return this;
  }

  /**
   * @return map of {@link EffectType} => {@link Color} representation of builder state
   */
  @NotNull
  Map<EffectType, Color> getEffectsMap() {
    if (myEffectsMap.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<EffectType, Color> result = new HashMap<>();
    myEffectsMap.forEach((key, val) -> {
      if (val != null) {
        result.put(val.effectType, val.effectColor);
      }
    });
    return result;
  }

  /**
   * Applies effects from the current state to the target attributes
   *
   * @param targetAttributes passed targetAttributes
   * @apiNote this method is not a thread safe, builder can't be modified in some other thread when applying to something
   */
  @NotNull
  public TextAttributes applyTo(@NotNull final TextAttributes targetAttributes) {
    Iterator<EffectDescriptor> effectsIterator = myEffectsMap.values().iterator();
    if (!effectsIterator.hasNext()) {
      targetAttributes.setEffectColor(null);
      targetAttributes.setEffectType(BOXED);
      targetAttributes.setAdditionalEffects(Collections.emptyMap());
    }
    else {
      EffectDescriptor mainEffectDescriptor = effectsIterator.next();
      targetAttributes.setEffectType(mainEffectDescriptor.effectType);
      targetAttributes.setEffectColor(mainEffectDescriptor.effectColor);

      int effectsLeft = myEffectsMap.size() - 1;
      if (effectsLeft == 0) {
        targetAttributes.setAdditionalEffects(Collections.emptyMap());
      }
      else if (effectsLeft == 1) {
        EffectDescriptor additionalEffect = effectsIterator.next();
        targetAttributes.setAdditionalEffects(Collections.singletonMap(additionalEffect.effectType, additionalEffect.effectColor));
      }
      else {
        Map<EffectType, Color> effectsMap = new HashMap<>(effectsLeft);
        effectsIterator.forEachRemaining(it -> effectsMap.put(it.effectType, it.effectColor));
        targetAttributes.setAdditionalEffects(effectsMap);
      }
    }
    return targetAttributes;
  }

  @Nullable
  @Contract("null -> null")
  public EffectDescriptor getEffectDescriptor(@Nullable EffectSlot effectSlot) {
    return myEffectsMap.get(effectSlot);
  }

  public static final class EffectDescriptor {
    @NotNull
    public final EffectType effectType;
    @NotNull
    public final Color effectColor;

    private EffectDescriptor(@NotNull EffectType effectType, @NotNull Color effectColor) {
      this.effectType = effectType;
      this.effectColor = effectColor;
    }

    @NotNull
    static EffectDescriptor create(@NotNull EffectType effectType, @NotNull Color effectColor) {
      return new EffectDescriptor(effectType, effectColor);
    }
  }
}

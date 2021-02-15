// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.CodeStyleBundle;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;

/**
 * Holds settings tokens used by built-in IJ arrangers.
 *
 * @author Denis Zhdanov
 */
public final class StdArrangementTokens {

  private static final Map<String, StdArrangementSettingsToken>  TOKENS_BY_ID = new HashMap<>();

  /**
   * Forces nested classes initialization - otherwise it's possible that, say, {@link #byId(String)} returns null for valid
   * id just because corresponding nested class hasn't been initialized yet.
   */
  private static final NotNullLazyValue<Integer> NESTED_CLASSES_INITIALIZER = NotNullLazyValue.createValue(() -> {
    int dummy = 0;
    for (Class<?> clazz : StdArrangementTokens.class.getClasses()) {
      try {
        dummy += clazz.getDeclaredFields()[0].get(null).hashCode();
      }
      catch (IllegalAccessException e) {
        assert false;
      }
    }
    return dummy;
  });

  private StdArrangementTokens() {
  }

  @Nullable
  public static ArrangementSettingsToken byId(@NotNull String id) {
    NESTED_CLASSES_INITIALIZER.getValue();
    return TOKENS_BY_ID.get(id);
  }

  private static NotNullLazyValue<Set<ArrangementSettingsToken>> collectFields(@NotNull final Class<?> clazz) {
    return NotNullLazyValue.createValue(() -> {
      Set<ArrangementSettingsToken> result = new HashSet<>();
      for (Field field : clazz.getFields()) {
        if (ArrangementSettingsToken.class.isAssignableFrom(field.getType())) {
          try {
            result.add((ArrangementSettingsToken)field.get(null));
          }
          catch (IllegalAccessException e) {
            assert false : e;
          }
        }
      }
      return result;
    });
  }

  private static StdArrangementSettingsToken invertible(@NotNull String id,
                                                        @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) @NotNull String displayNameKey,
                                                        @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) @NotNull String invertedNameKey,
                                                        @NotNull StdArrangementTokenType type) {
    StdArrangementSettingsToken result = StdInvertibleArrangementSettingsToken.invertibleToken(
      id, CodeStyleBundle.message(displayNameKey), CodeStyleBundle.message(invertedNameKey), type
    );
    TOKENS_BY_ID.put(id, result);
    return result;
  }

  private static StdArrangementSettingsToken compositeToken(@NonNls @NotNull String id,
                                                            @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) @NotNull String key,
                                                            ArrangementSettingsToken @NotNull ... alternativeTokens) {
    StdArrangementSettingsToken result = CompositeArrangementToken.create(id, CodeStyleBundle.message(key),
                                                                          StdArrangementTokenType.MODIFIER, alternativeTokens);
    TOKENS_BY_ID.put(id, result);
    return result;
  }

  private static StdArrangementSettingsToken token(@NotNull String id, @NotNull @PropertyKey(resourceBundle = CodeStyleBundle.BUNDLE) String key,
                                                   @NotNull StdArrangementTokenType type) {
    StdArrangementSettingsToken result = StdArrangementSettingsToken.tokenByBundle(id, key, type);
    TOKENS_BY_ID.put(id, result);
    return result;
  }

  public static final class General {
    @NotNull public static final ArrangementSettingsToken TYPE  = token("TYPE", "arrangement.settings.text.general.type", StdArrangementTokenType.GENERAL);
    @NotNull public static final ArrangementSettingsToken MODIFIER = token("MODIFIER", "arrangement.settings.text.general.modifier", StdArrangementTokenType.GENERAL);
    @NotNull public static final ArrangementSettingsToken ORDER = token("ORDER", "arrangement.settings.text.general.order", StdArrangementTokenType.GENERAL);
    @NotNull public static final ArrangementSettingsToken ALIAS = token("ALIAS", "arrangement.settings.text.general.sequence", StdArrangementTokenType.GENERAL);

    private General() {
    }
  }

  public static final class Section {
    @NotNull public static final ArrangementSettingsToken START_SECTION =
      token("SECTION_START", "arrangement.settings.text.section.start", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken END_SECTION =
      token("SECTION_END", "arrangement.settings.text.section.end", StdArrangementTokenType.ENTRY_TYPE);

    private Section() {

    }
  }

  public static final class Regexp {
    @NotNull public static final StdArrangementSettingsToken NAME = token("NAME", "arrangement.settings.text.general.name",
                                                                          StdArrangementTokenType.REG_EXP);

    @NotNull public static final StdArrangementSettingsToken XML_NAMESPACE =
      token("XML_NAMESPACE", "arrangement.settings.text.general.xml.namespace", StdArrangementTokenType.REG_EXP);

    @NotNull public static final StdArrangementSettingsToken TEXT = token("TEXT", "arrangement.settings.text.general.text",
                                                                          StdArrangementTokenType.REG_EXP);

    private Regexp() {
    }
  }
  public static final class EntryType {
    @NotNull public static final ArrangementSettingsToken CLASS           = invertible("CLASS", "arrangement.settings.text.entry.type.class", "arrangement.settings.text.entry.type.class.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken ANONYMOUS_CLASS = invertible("ANONYMOUS_CLASS", "arrangement.settings.text.entry.type.anonymous.class", "arrangement.settings.text.entry.type.anonymous.class.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken FIELD           = invertible("FIELD", "arrangement.settings.text.entry.type.field", "arrangement.settings.text.entry.type.field.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken CONSTRUCTOR     = invertible("CONSTRUCTOR", "arrangement.settings.text.entry.type.constructor", "arrangement.settings.text.entry.type.constructor.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken METHOD          = invertible("METHOD", "arrangement.settings.text.entry.type.method", "arrangement.settings.text.entry.type.method.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken ENUM            = invertible("ENUM", "arrangement.settings.text.entry.type.enum", "arrangement.settings.text.entry.type.enum.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken INTERFACE       = invertible("INTERFACE", "arrangement.settings.text.entry.type.interface", "arrangement.settings.text.entry.type.interface.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken CONST           = invertible("CONST", "arrangement.settings.text.entry.type.const", "arrangement.settings.text.entry.type.const.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken VAR             = invertible("VAR", "arrangement.settings.text.entry.type.var", "arrangement.settings.text.entry.type.var.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken PROPERTY        = invertible("PROPERTY", "arrangement.settings.text.entry.type.property", "arrangement.settings.text.entry.type.property.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken EVENT_HANDLER   = invertible("EVENT_HANDLER", "arrangement.settings.text.entry.type.event.handler", "arrangement.settings.text.entry.type.event.handler.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken STATIC_INIT     = invertible("STATIC_INIT", "arrangement.settings.text.entry.type.static.init", "arrangement.settings.text.entry.type.static.init.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken INIT_BLOCK      = invertible("INITIALIZER_BLOCK", "arrangement.settings.text.entry.type.initializer.block", "arrangement.settings.text.entry.type.initializer.block.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken NAMESPACE       = invertible("NAMESPACE", "arrangement.settings.text.entry.type.namespace", "arrangement.settings.text.entry.type.namespace.inverted", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final ArrangementSettingsToken TRAIT           = invertible("TRAIT", "arrangement.settings.text.entry.type.trait", "arrangement.settings.text.entry.type.trait.inverted", StdArrangementTokenType.ENTRY_TYPE);

    // xml use only two entry types -> invertible tokens make no sense
    @NotNull public static final ArrangementSettingsToken XML_TAG         =
      token("XML_TAG", "arrangement.settings.text.entry.type.xml.tag", StdArrangementTokenType.ENTRY_TYPE);
    @NotNull public static final StdArrangementSettingsToken XML_ATTRIBUTE   =
      token("XML_ATTRIBUTE", "arrangement.settings.text.entry.type.xml.attribute", StdArrangementTokenType.ENTRY_TYPE);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(EntryType.class);

    private EntryType() {
    }

    @NotNull
    public static Set<ArrangementSettingsToken> values() {
      return TOKENS.getValue();
    }
  }
  public static final class Modifier {
    @NotNull public static final ArrangementSettingsToken PUBLIC          = invertible("PUBLIC", "arrangement.settings.text.modifier.public", "arrangement.settings.text.modifier.public.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken PROTECTED       = invertible("PROTECTED", "arrangement.settings.text.modifier.protected", "arrangement.settings.text.modifier.protected.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken PRIVATE         = invertible("PRIVATE", "arrangement.settings.text.modifier.private", "arrangement.settings.text.modifier.private.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken PACKAGE_PRIVATE = invertible("PACKAGE_PRIVATE", "arrangement.settings.text.modifier.package.private", "arrangement.settings.text.modifier.package.private.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken STATIC          = invertible("STATIC", "arrangement.settings.text.modifier.static", "arrangement.settings.text.modifier.static.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken FINAL           = invertible("FINAL", "arrangement.settings.text.modifier.final", "arrangement.settings.text.modifier.final.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken READONLY        = invertible("READONLY", "arrangement.settings.text.modifier.readonly", "arrangement.settings.text.modifier.readonly.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken TRANSIENT       = invertible("TRANSIENT", "arrangement.settings.text.modifier.transient", "arrangement.settings.text.modifier.transient.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken VOLATILE        = invertible("VOLATILE", "arrangement.settings.text.modifier.volatile", "arrangement.settings.text.modifier.volatile.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken SYNCHRONIZED    = invertible("SYNCHRONIZED", "arrangement.settings.text.modifier.synchronized", "arrangement.settings.text.modifier.synchronized.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken ABSTRACT        = invertible("ABSTRACT", "arrangement.settings.text.modifier.abstract", "arrangement.settings.text.modifier.abstract.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken OVERRIDE        = invertible("OVERRIDE", "arrangement.settings.text.modifier.override", "arrangement.settings.text.modifier.override.inverted", StdArrangementTokenType.MODIFIER);
    @NotNull public static final ArrangementSettingsToken GETTER          = compositeToken("GETTER", "arrangement.settings.text.modifier.getter",
                                                                                           METHOD, PUBLIC);
    @NotNull public static final ArrangementSettingsToken SETTER          = compositeToken("SETTER", "arrangement.settings.text.modifier.setter",
                                                                                           METHOD, PUBLIC);
    @NotNull public static final ArrangementSettingsToken OVERRIDDEN      = compositeToken("OVERRIDDEN", "arrangement.settings.text.modifier.overridden",
                                                                                           METHOD, PUBLIC, PROTECTED);
    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(Modifier.class);

    public static final Set<ArrangementSettingsToken> MODIFIER_AS_TYPE = ContainerUtil.newHashSet(GETTER, SETTER, OVERRIDDEN);

    private Modifier() {
    }

    @NotNull
    public static Set<ArrangementSettingsToken> values() {
      return TOKENS.getValue();
    }
  }
  public static final class Grouping {
    @NotNull public static final ArrangementSettingsToken GETTERS_AND_SETTERS                     =
      token("GETTERS_AND_SETTERS", "arrangement.settings.groups.getters.and.setters.together", StdArrangementTokenType.GROUPING);
    @NotNull public static final ArrangementSettingsToken OVERRIDDEN_METHODS                      =
      token("OVERRIDDEN_METHODS", "arrangement.settings.groups.overridden.methods", StdArrangementTokenType.GROUPING);
    @NotNull public static final ArrangementSettingsToken DEPENDENT_METHODS                       =
      token("DEPENDENT_METHODS", "arrangement.settings.groups.dependent.methods", StdArrangementTokenType.GROUPING);
    @NotNull public static final ArrangementSettingsToken GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER =
      token("GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER", "arrangement.settings.groups.property.field", StdArrangementTokenType.GROUPING);

    private Grouping() {
    }
  }
  public static final class Order {
    @NotNull public static final ArrangementSettingsToken KEEP    = token("KEEP", "arrangement.settings.order.type.keep",
                                                                          StdArrangementTokenType.ORDER);
    @NotNull public static final ArrangementSettingsToken BY_NAME = token("BY_NAME", "arrangement.settings.order.type.by.name",
                                                                          StdArrangementTokenType.ORDER);
    @NotNull public static final ArrangementSettingsToken DEPTH_FIRST   = token("DEPTH_FIRST",
                                                                                "arrangement.settings.order.type.depth.first",
                                                                                StdArrangementTokenType.ORDER);
    @NotNull public static final ArrangementSettingsToken BREADTH_FIRST = token("BREADTH_FIRST",
                                                                                "arrangement.settings.order.type.breadth.first",
                                                                                StdArrangementTokenType.ORDER);

    private Order() {
    }
  }
}

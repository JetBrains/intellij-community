/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokenUiRole.*;

/**
 * Holds settings tokens used by built-in IJ arrangers.
 *
 * @author Denis Zhdanov
 * @since 3/6/13 3:16 PM
 */
public class StdArrangementTokens {
  
  private static final Map<String, ArrangementSettingsToken>                    TOKENS_BY_ID   = ContainerUtilRt.newHashMap();
  private static final Map<ArrangementSettingsToken, StdArrangementTokenUiRole> ROLES_BY_TOKEN = ContainerUtilRt.newHashMap();

  /**
   * Forces nested classes initialization - otherwise it's possible that, say, {@link #byId(String)} returns null for valid
   * id just because corresponding nested class hasn't been initialized yet.
   */
  private static final NotNullLazyValue<Integer> NESTED_CLASSES_INITIALIZER = new NotNullLazyValue<Integer>() {
    @NotNull
    @Override
    protected Integer compute() {
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
    }
  };

  private StdArrangementTokens() {
  }

  @Nullable
  public static ArrangementSettingsToken byId(@NotNull String id) {
    NESTED_CLASSES_INITIALIZER.getValue();
    return TOKENS_BY_ID.get(id);
  }

  @Nullable
  public static StdArrangementTokenUiRole role(@NotNull ArrangementSettingsToken token) {
    NESTED_CLASSES_INITIALIZER.getValue();
    return ROLES_BY_TOKEN.get(token);
  }

  @NotNull
  private static ArrangementSettingsToken token(@NotNull String id, @NotNull StdArrangementTokenUiRole role) {
    ArrangementSettingsToken result = new ArrangementSettingsToken(id, id.toLowerCase().replace("_", " "));
    TOKENS_BY_ID.put(id, result);
    ROLES_BY_TOKEN.put(result, role);
    return result;
  }

  @NotNull
  private static ArrangementSettingsToken token(@NotNull String id,
                                                @NotNull @PropertyKey(resourceBundle = ApplicationBundle.BUNDLE) String key,
                                                @NotNull StdArrangementTokenUiRole role)
  {
    ArrangementSettingsToken result = new ArrangementSettingsToken(id, ApplicationBundle.message(key));
    TOKENS_BY_ID.put(id, result);
    ROLES_BY_TOKEN.put(result, role);
    return result;
  }

  private static NotNullLazyValue<Set<ArrangementSettingsToken>> collectFields(@NotNull final Class<?> clazz) {
    return new NotNullLazyValue<Set<ArrangementSettingsToken>>() {
      @NotNull
      @Override
      protected Set<ArrangementSettingsToken> compute() {
        Set<ArrangementSettingsToken> result = ContainerUtilRt.newHashSet();
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
      }
    };
  }

  public static class General {

    @NotNull public static final ArrangementSettingsToken TYPE     = token("TYPE", "arrangement.settings.text.general.type", LABEL);
    @NotNull public static final ArrangementSettingsToken MODIFIER = token("MODIFIER", "arrangement.settings.text.general.modifier", LABEL);
    @NotNull public static final ArrangementSettingsToken ORDER    = token("ORDER", "arrangement.settings.text.general.order", LABEL);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(General.class);

    private General() {
    }

    public static boolean is(@NotNull ArrangementSettingsToken token) {
      return TOKENS.getValue().contains(token);
    }
  }
  
  public static class Regexp {

    @NotNull public static final ArrangementSettingsToken NAME = token("NAME", "arrangement.settings.text.general.name", TEXT_FIELD);

    @NotNull public static final ArrangementSettingsToken XML_NAMESPACE =
      token("XML_NAMESPACE", "arrangement.settings.text.general.xml.namespace", TEXT_FIELD);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(Regexp.class);

    private Regexp() {
    }

    public static boolean is(@NotNull ArrangementSettingsToken token) {
      return TOKENS.getValue().contains(token);
    }
  }

  public static class EntryType {

    @NotNull public static final ArrangementSettingsToken CLASS           = token("CLASS", BULB);
    @NotNull public static final ArrangementSettingsToken ANONYMOUS_CLASS = token("ANONYMOUS_CLASS", BULB);
    @NotNull public static final ArrangementSettingsToken FIELD           = token("FIELD", BULB);
    @NotNull public static final ArrangementSettingsToken CONSTRUCTOR     = token("CONSTRUCTOR", BULB);
    @NotNull public static final ArrangementSettingsToken METHOD          = token("METHOD", BULB);
    @NotNull public static final ArrangementSettingsToken ENUM            = token("ENUM", BULB);
    @NotNull public static final ArrangementSettingsToken INTERFACE       = token("INTERFACE", BULB);
    @NotNull public static final ArrangementSettingsToken CONST           = token("CONST", BULB);
    @NotNull public static final ArrangementSettingsToken VAR             = token("VAR", BULB);
    @NotNull public static final ArrangementSettingsToken PROPERTY        = token("PROPERTY", BULB);
    @NotNull public static final ArrangementSettingsToken EVENT_HANDLER   = token("EVENT_HANDLER", BULB);
    @NotNull public static final ArrangementSettingsToken STATIC_INIT     = token("STATIC_INIT", BULB);
    @NotNull public static final ArrangementSettingsToken NAMESPACE       = token("NAMESPACE", BULB);
    @NotNull public static final ArrangementSettingsToken TRAIT           = token("TRAIT", BULB);
    @NotNull public static final ArrangementSettingsToken XML_TAG         =
      token("XML_TAG", "arrangement.settings.text.entry.type.xml.tag", BULB);
    @NotNull public static final ArrangementSettingsToken XML_ATTRIBUTE   =
      token("XML_ATTRIBUTE", "arrangement.settings.text.entry.type.xml.attribute", BULB);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(EntryType.class);

    private EntryType() {
    }

    @NotNull
    public static Set<ArrangementSettingsToken> values() {
      return TOKENS.getValue();
    }

    public static boolean is(@NotNull ArrangementSettingsToken token) {
      return TOKENS.getValue().contains(token);
    }
  }

  public static class Modifier {

    @NotNull public static final ArrangementSettingsToken PUBLIC          = token("PUBLIC", BULB);
    @NotNull public static final ArrangementSettingsToken PROTECTED       = token("PROTECTED", BULB);
    @NotNull public static final ArrangementSettingsToken PRIVATE         = token("PRIVATE", BULB);
    @NotNull public static final ArrangementSettingsToken PACKAGE_PRIVATE = token("PACKAGE_PRIVATE", BULB);
    @NotNull public static final ArrangementSettingsToken STATIC          = token("STATIC", BULB);
    @NotNull public static final ArrangementSettingsToken FINAL           = token("FINAL", BULB);
    @NotNull public static final ArrangementSettingsToken TRANSIENT       = token("TRANSIENT", BULB);
    @NotNull public static final ArrangementSettingsToken VOLATILE        = token("VOLATILE", BULB);
    @NotNull public static final ArrangementSettingsToken SYNCHRONIZED    = token("SYNCHRONIZED", BULB);
    @NotNull public static final ArrangementSettingsToken ABSTRACT        = token("ABSTRACT", BULB);
    @NotNull public static final ArrangementSettingsToken OVERRIDE        = token("OVERRIDE", BULB);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(Modifier.class);

    private Modifier() {
    }

    @NotNull
    public static Set<ArrangementSettingsToken> values() {
      return TOKENS.getValue();
    }

    public static boolean is(@NotNull ArrangementSettingsToken token) {
      return TOKENS.getValue().contains(token);
    }
  }

  public static class Grouping {

    @NotNull public static final ArrangementSettingsToken GETTERS_AND_SETTERS                     =
      token("GETTERS_AND_SETTERS", "arrangement.settings.groups.getters.and.setters.together", CHECKBOX);
    @NotNull public static final ArrangementSettingsToken OVERRIDDEN_METHODS                      =
      token("OVERRIDDEN_METHODS", "arrangement.settings.groups.overridden.methods", CHECKBOX);
    @NotNull public static final ArrangementSettingsToken DEPENDENT_METHODS                       =
      token("DEPENDENT_METHODS", "arrangement.settings.groups.dependent.methods", CHECKBOX);
    @NotNull public static final ArrangementSettingsToken GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER =
      token("GROUP_PROPERTY_FIELD_WITH_GETTER_SETTER", "arrangement.settings.groups.property.field", CHECKBOX);

    private Grouping() {
    }
  }

  public static class Order {

    @NotNull public static final ArrangementSettingsToken KEEP    = token("KEEP", "arrangement.settings.order.type.keep",
                                                                          COMBO_BOX);
    @NotNull public static final ArrangementSettingsToken BY_NAME = token("BY_NAME", "arrangement.settings.order.type.by.name",
                                                                                COMBO_BOX);
    @NotNull public static final ArrangementSettingsToken DEPTH_FIRST   = token("DEPTH_FIRST",
                                                                                "arrangement.settings.order.type.depth.first",
                                                                                COMBO_BOX);
    @NotNull public static final ArrangementSettingsToken BREADTH_FIRST = token("BREADTH_FIRST",
                                                                                "arrangement.settings.order.type.breadth.first",
                                                                                COMBO_BOX);

    private static final NotNullLazyValue<Set<ArrangementSettingsToken>> TOKENS = collectFields(Order.class);

    private Order() {
    }

    public static boolean is(@NotNull ArrangementSettingsToken token) {
      return TOKENS.getValue().contains(token);
    }
  }
}

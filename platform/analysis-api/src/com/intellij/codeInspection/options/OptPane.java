// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Top-level options pane with children components stacked vertically.
 * This record does not implement {@link OptComponent} on purpose. It's expected that the pane is used
 * as a top-level component only.
 *
 * @param components list of components on the pane
 */
public record OptPane(@NotNull List<@NotNull OptRegularComponent> components) {
  public OptPane {
    Set<String> ids = new HashSet<>();
    traverse(components, comp -> {
      if (comp instanceof OptControl ctl && !ids.add(ctl.bindId())) {
        throw new IllegalArgumentException("Repeating control identifier inside the pane: " + ctl.bindId());
      }
      return true;
    });
  }

  /**
   * An empty pane that contains no options at all
   */
  public static final OptPane EMPTY = new OptPane(List.of());

  /**
   * @param bindId ID to find
   * @return control with given ID; null if none
   */
  public @Nullable OptControl findControl(@NotNull String bindId) {
    var processor = new Predicate<OptComponent>() {
      OptControl found = null;

      @Override
      public boolean test(OptComponent cmp) {
        if (cmp instanceof OptControl ctl && ctl.bindId().equals(bindId)) {
          found = ctl;
          return false;
        }
        return true;
      }
    };
    traverse(components, processor);
    return processor.found;
  }

  private static boolean traverse(@NotNull List<? extends @NotNull OptComponent> components, @NotNull Predicate<@NotNull OptComponent> processor) {
    for (OptComponent component : components) {
      if (!processor.test(component) || !traverse(component.children(), processor)) return false;
    }
    return true;
  }

  /**
   * Transform this pane to a tab
   * 
   * @param label tab title
   * @return a {@link OptTab} object that contains all the controls from this pane
   */
  public OptTab asTab(@NotNull @NlsContexts.TabTitle String label) {
    return new OptTab(new PlainMessage(label), components());
  }

  /* DSL */

  /**
   * @param components components to add to the pane. Controls in the pane are stacked vertically.
   * @return the pane
   * @see InspectionProfileEntry#getOptionsPane()
   */
  public static @NotNull OptPane pane(@NotNull OptRegularComponent @NotNull ... components) {
    return new OptPane(List.of(components));
  }

  /* Controls */

  /**
   * @param bindId   identifier of binding variable used by inspection; the corresponding variable is expected to be boolean
   * @param label    label to display next to a checkbox
   * @param children optional list of children controls to display next to checkbox. They are disabled if checkbox is unchecked
   * @return a checkbox
   */
  public static @NotNull OptCheckbox checkbox(@Language("jvm-field-name") @NotNull String bindId,
                                              @NotNull @NlsContexts.Label String label,
                                              @NotNull OptRegularComponent @NotNull ... children) {
    return new OptCheckbox(bindId, new PlainMessage(label), List.of(children), null);
  }

  /**
   * @param checkboxes checkboxes to display at the panel
   * @return a panel of checkboxes, whose children components are rendered on the right side and only visible when a particular checkbox is
   * selected.
   */
  public static @NotNull OptCheckboxPanel checkboxPanel(@NotNull OptCheckbox @NotNull ... checkboxes) {
    return new OptCheckboxPanel(List.of(checkboxes));
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be int
   * @param splitLabel label to display around the control
   * @param minValue   minimal allowed value of the variable
   * @param maxValue   maximal allowed value of the variable
   * @return an edit box to enter a number
   */
  public static @NotNull OptNumber number(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int minValue,
                                          int maxValue) {
    return new OptNumber(bindId, new PlainMessage(splitLabel), minValue, maxValue);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @return an edit box to enter a string
   */
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel) {
    return new OptString(bindId, new PlainMessage(splitLabel), null, -1);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param width      width of the control in approximate number of characters; if -1 then it will be determined automatically
   * @return an edit box to enter a string
   */
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int width) {
    return new OptString(bindId, new PlainMessage(splitLabel), null, width);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param validator  validator for content; can validate max-length or be something more complicated
   *                   (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return an edit box to enter a string
   */
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          @NotNull StringValidator validator) {
    return new OptString(bindId, new PlainMessage(splitLabel), validator, -1);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param width      width of the control in approximate number of characters; if -1 then it will be determined automatically
   * @param validator  validator for content; can validate max-length or be something more complicated
   *                   (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return an edit box to enter a string
   */
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int width,
                                          @NotNull StringValidator validator) {
    return new OptString(bindId, new PlainMessage(splitLabel), validator, width);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be a string, int or enum
   * @param splitLabel label to display around the control
   * @param options    drop-down options
   * @return a drop-down control to select a single option from
   * @see #option(String, String)
   * @see #dropdown(String, String, Class, Function)
   */
  public static @NotNull OptDropdown dropdown(@Language("jvm-field-name") @NotNull String bindId,
                                              @NotNull @NlsContexts.Label String splitLabel,
                                              @NotNull OptDropdown.Option @NotNull ... options) {
    return new OptDropdown(bindId, new PlainMessage(splitLabel), List.of(options));
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be a string, int or enum
   * @param splitLabel label to display around the control
   * @param values     drop-down options
   * @param keyExtractor a function to extract a key string from the option (which will be written to the binding variable)
   * @param presentableTextExtractor a function to extract a presentable text from the option
   * @return a drop-down control to select a single option from
   * @see #dropdown(String, String, Class, Function)
   */
  public static @NotNull <T> OptDropdown dropdown(@Language("jvm-field-name") @NotNull String bindId,
                                                  @NotNull @NlsContexts.Label String splitLabel,
                                                  @NotNull Collection<T> values,
                                                  @NotNull Function<? super T, @NotNull @NonNls String> keyExtractor,
                                                  @NotNull Function<? super T, @NotNull @Nls String> presentableTextExtractor) {
    return new OptDropdown(bindId, new PlainMessage(splitLabel),
                           ContainerUtil.map(values, c -> option(keyExtractor.apply(c), presentableTextExtractor.apply(c))));
  }

  /**
   * @param bindId                   identifier of binding variable used by inspection; the corresponding variable is expected to be a string or enum
   * @param splitLabel               label to display around the control
   * @param enumClass                enum class to populate drop-down from (take all values in declaration order)
   * @param presentableTextExtractor a function to extract presentable name from enum constant
   * @return a drop-down control to select a single option from
   * @see #option(Enum, String)
   */
  public static @NotNull <T extends Enum<T>> OptDropdown dropdown(@Language("jvm-field-name") @NotNull String bindId,
                                                                  @NotNull @NlsContexts.Label String splitLabel,
                                                                  @NotNull Class<T> enumClass,
                                                                  @NotNull Function<@NotNull T, @NotNull @Nls String> presentableTextExtractor) {
    return new OptDropdown(bindId, new PlainMessage(splitLabel),
                           ContainerUtil.map(enumClass.getEnumConstants(), c -> option(c, presentableTextExtractor.apply(c))));
  }

  /**
   * @param key   key to assign to a string variable
   * @param label label for a given option
   * @return an option for a drop-down control
   * @see #dropdown(String, String, OptDropdown.Option...)
   */
  public static @NotNull OptDropdown.Option option(@NotNull String key, @NotNull @Nls String label) {
    return new OptDropdown.Option(key, new PlainMessage(label));
  }

  /**
   * @param key   key to assign to an enum variable
   * @param label label for a given option
   * @return an option for a drop-down control
   * @see #dropdown(String, String, OptDropdown.Option...)
   */
  public static @NotNull OptDropdown.Option option(@NotNull Enum<?> key, @NotNull @Nls String label) {
    return new OptDropdown.Option(key.name(), new PlainMessage(label));
  }

  /**
   * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be {@code Set<String>} or
   *               {@code List<String>}.
   * @param label  label above the control
   * @return editable sorted list of unique strings
   */
  public static @NotNull OptSet stringSet(@Language("jvm-field-name") @NotNull String bindId, @NotNull @NlsContexts.Label String label) {
    return new OptSet(bindId, new PlainMessage(label), null);
  }

  /**
   * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be {@code Set<String>} or
   *                  {@code List<String>}.
   * @param label     label above the control
   * @param validator optional validator for content; can validate max-length or be something more complicated
   *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return editable sorted list of unique strings
   */
  public static @NotNull OptSet stringSet(@Language("jvm-field-name") @NotNull String bindId, @NotNull @NlsContexts.Label String label,
                                          @NotNull StringValidator validator) {
    return new OptSet(bindId, new PlainMessage(label), validator);
  }

  /**
   * @param bindId         identifier of binding variable used by inspection; the corresponding variable is expected to be
   *                       {@code Map<String, String>}.
   * @param label          label above the control
   * @return editable two-column table of strings; strings in the left column are unique and sorted
   */
  public static @NotNull OptMap stringMap(@Language("jvm-field-name") @NotNull String bindId, @NotNull @NlsContexts.Label String label) {
    return new OptMap(bindId, new PlainMessage(label), null, null);
  }

  /**
   * @param bindId         identifier of binding variable used by inspection; the corresponding variable is expected to be
   *                       {@code Map<String, String>}.
   * @param label          label above the control
   * @param keyValidator   optional validator for keys column
   * @param valueValidator optional validator for values column
   * @return editable two-column table of strings; strings in the left column are unique and sorted
   */
  public static @NotNull OptMap stringMap(@Language("jvm-field-name") @NotNull String bindId, @NotNull @NlsContexts.Label String label,
                                          @NotNull StringValidator keyValidator, @NotNull StringValidator valueValidator) {
    return new OptMap(bindId, new PlainMessage(label), keyValidator, valueValidator);
  }

  /* Layout elements */

  /**
   * @param label label to display above the group
   * @param children list of child components
   * @return a group of controls with a name
   */
  public static @NotNull OptGroup group(@NotNull @NlsContexts.Label String label, @NotNull OptRegularComponent @NotNull ... children) {
    return new OptGroup(new PlainMessage(label), List.of(children));
  }

  /**
   * @param children list of child components
   * @return a horizontal stack of controls
   */
  public static @NotNull OptHorizontalStack horizontalStack(@NotNull OptRegularComponent @NotNull ... children) {
    return new OptHorizontalStack(List.of(children));
  }

  /**
   * @return an unlabeled horizontal separator
   */
  public static @NotNull OptSeparator separator() {
    return new OptSeparator(null);
  }

  /**
   * @param label to display
   * @return an labeled horizontal separator
   */
  public static @NotNull OptSeparator separator(@NotNull @NlsContexts.Label String label) {
    return new OptSeparator(new PlainMessage(label));
  }

  /**
   * @param tabs tabs description
   * @return set of tabs
   * @see #tab(String, OptComponent...)
   * @see #asTab(String) 
   */
  public static @NotNull OptTabSet tabs(@NotNull OptTab @NotNull ... tabs) {
    return new OptTabSet(List.of(tabs));
  }

  /**
   * @param label tab label
   * @param children tab content
   * @return tab description
   * @see #tabs(OptTab...) 
   */
  public static @NotNull OptTab tab(@NotNull @NlsContexts.TabTitle String label, @NotNull OptRegularComponent @NotNull ... children) {
    return new OptTab(new PlainMessage(label), List.of(children));
  }

  /**
   * @param displayName link label
   * @param configurableID ID of configurable to display
   * @return a component, which represents a link to a settings page
   */
  public static @NotNull OptSettingLink settingLink(@NotNull @NlsContexts.Label String displayName,
                                                    @NotNull @NonNls String configurableID) {
    return new OptSettingLink(displayName, configurableID, null);
  }

  /**
   * @param displayName link label
   * @param configurableID ID of configurable to display
   * @param controlLabel label of the control to focus on
   * @return a component, which represents a link to a settings page
   */
  public static @NotNull OptSettingLink settingLink(@NotNull @NlsContexts.Label String displayName,
                                                    @NotNull @NonNls String configurableID,
                                                    @NotNull @Nls String controlLabel) {
    return new OptSettingLink(displayName, configurableID, controlLabel);
  }
}

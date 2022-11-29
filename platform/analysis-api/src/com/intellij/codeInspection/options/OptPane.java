// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Top-level options pane with children components stacked vertically.
 * This record does not implement {@link OptComponent} on purpose. It's expected that the pane is used
 * as a top-level component only.
 *
 * @param components list of components on the pane
 */
public record OptPane(@NotNull List<@NotNull OptComponent> components) {
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

  private static boolean traverse(@NotNull List<@NotNull OptComponent> components, @NotNull Predicate<@NotNull OptComponent> processor) {
    for (OptComponent component : components) {
      if (!processor.test(component) || !traverse(component.children(), processor)) return false;
    }
    return true;
  }

  /* DSL */

  /**
   * @param components components to add to the pane. Controls in the pane are stacked vertically.
   * @return the pane
   * @see InspectionProfileEntry#getOptionsPane()
   */
  public static @NotNull OptPane pane(@NotNull OptComponent @NotNull ... components) {
    return new OptPane(List.of(components));
  }

  /* Controls */

  /**
   * @param bindId   identifier of binding variable used by inspection; the corresponding variable is expected to be boolean
   * @param label    label to display next to a checkbox
   * @param children optional list of children controls to display next to checkbox. They are disabled if checkbox is unchecked
   * @return a checkbox
   */
  public static @NotNull OptCheckbox checkbox(@NotNull String bindId,
                                              @NotNull @NlsContexts.Label String label,
                                              @NotNull OptComponent @NotNull ... children) {
    return new OptCheckbox(bindId, new PlainMessage(label), List.of(children));
  }

  /**
   * @param bindId ID to bind the custom control to.
   * @return a custom control
   */
  public static @NotNull OptCustom custom(@NotNull String bindId) {
    return new OptCustom(bindId);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be int or long
   * @param splitLabel label to display around the control
   * @param minValue   minimal allowed value of the variable
   * @param maxValue   maximal allowed value of the variable
   * @return an edit box to enter a number
   */
  public static @NotNull OptNumber number(@NotNull String bindId,
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
  public static @NotNull OptString string(@NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel) {
    return new OptString(bindId, new PlainMessage(splitLabel), null, -1);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param width      width of the control in approximate number of characters; if -1 then it will be determined automatically
   * @return an edit box to enter a string
   */
  public static @NotNull OptString string(@NotNull String bindId,
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
  public static @NotNull OptString string(@NotNull String bindId,
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
  public static @NotNull OptString string(@NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int width,
                                          @NotNull StringValidator validator) {
    return new OptString(bindId, new PlainMessage(splitLabel), validator, width);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be a string or enum
   * @param splitLabel label to display around the control
   * @param options    drop-down options
   * @return a drop-down control to select a single option from
   * @see #option(String, String)
   */
  public static @NotNull OptDropdown dropdown(@NotNull String bindId,
                                              @NotNull @NlsContexts.Label String splitLabel,
                                              @NotNull OptDropdown.Option @NotNull ... options) {
    return new OptDropdown(bindId, new PlainMessage(splitLabel), List.of(options));
  }

  /**
   * @param key   key to assign to a string variable
   * @param label label for a given option
   * @return an option for a drop-down control
   * @see #dropdown(String, String, OptDropdown.Option...)
   */
  public static @NotNull OptDropdown.Option option(@NotNull String key, @NotNull @NlsContexts.Label String label) {
    return new OptDropdown.Option(key, new PlainMessage(label));
  }

  /**
   * @param key   key to assign to an enum variable
   * @param label label for a given option
   * @return an option for a drop-down control
   * @see #dropdown(String, String, OptDropdown.Option...)
   */
  public static @NotNull OptDropdown.Option option(@NotNull Enum<?> key, @NotNull @NlsContexts.Label String label) {
    return new OptDropdown.Option(key.name(), new PlainMessage(label));
  }

  /**
   * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be {@code Set<String>} or
   *               {@code List<String>}.
   * @param label  label above the control
   * @return editable sorted list of unique strings
   */
  public static @NotNull OptSet stringSet(@NotNull String bindId, @NotNull @NlsContexts.Label String label) {
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
  public static @NotNull OptSet stringSet(@NotNull String bindId, @NotNull @NlsContexts.Label String label,
                                          @NotNull StringValidator validator) {
    return new OptSet(bindId, new PlainMessage(label), validator);
  }

  /**
   * @param bindId         identifier of binding variable used by inspection; the corresponding variable is expected to be
   *                       {@code Map<String, String>}.
   * @param label          label above the control
   * @return editable two-column table of strings; strings in the left column are unique and sorted
   */
  public static @NotNull OptMap stringMap(@NotNull String bindId, @NotNull @NlsContexts.Label String label) {
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
  public static @NotNull OptMap stringMap(@NotNull String bindId, @NotNull @NlsContexts.Label String label,
                                          @NotNull StringValidator keyValidator, @NotNull StringValidator valueValidator) {
    return new OptMap(bindId, new PlainMessage(label), keyValidator, valueValidator);
  }

  /* Layout elements */

  /**
   * @param label label to display above the group
   * @param children list of child components
   * @return a group of controls with a name
   */
  public static @NotNull OptGroup group(@NotNull @NlsContexts.Label String label, @NotNull OptComponent @NotNull ... children) {
    return new OptGroup(new PlainMessage(label), List.of(children));
  }

  /**
   * @param children list of child components
   * @return a horizontal stack of controls
   */
  public static @NotNull OptHorizontalStack horizontalStack(@NotNull OptComponent @NotNull ... children) {
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
   */
  public static @NotNull OptTabSet tabs(@NotNull OptTabSet.TabInfo @NotNull ... tabs) {
    return new OptTabSet(List.of(tabs));
  }

  /**
   * @param label tab label
   * @param content tab content
   * @return tab description
   * @see #tabs(OptTabSet.TabInfo...) 
   */
  public static @NotNull OptTabSet.TabInfo tab(@NotNull @NlsContexts.Label String label, @NotNull OptComponent @NotNull ... children) {
    return new OptTabSet.TabInfo(new PlainMessage(label), List.of(children));
  }
}

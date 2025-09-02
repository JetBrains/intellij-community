// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Top-level options pane with children components stacked vertically.
 * This record does not implement {@link OptComponent} on purpose. It's expected that the pane is used
 * as a top-level component only.
 *
 * @param components list of components on the pane
 * @param helpId identifier of online help page for this component; null if no online help page is associated with this component
 */
public record OptPane(@NotNull List<@NotNull OptRegularComponent> components, @Nullable String helpId) {
  public OptPane {
    Set<String> ids = new HashSet<>();
    traverse(components, comp -> {
      if (comp instanceof OptControl ctl && !ids.add(ctl.bindId())) {
        throw new IllegalArgumentException("Repeating control identifier inside the pane: " + ctl.bindId());
      }
      return true;
    });
  }

  public OptPane(@NotNull List<@NotNull OptRegularComponent> components) {
    this(components, null);
  }

  /**
   * An empty pane that contains no options at all
   */
  public static final OptPane EMPTY = new OptPane(List.of());

  /**
   * @param helpId identifier of online help page for this component; null if no online help page is associated with this component
   * @return OptPane with a specified helpId.
   */
  public @NotNull OptPane withHelpId(@Nullable String helpId) {
    return new OptPane(components(), helpId);
  }
  
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

  /**
   * @return list of all controls (recursively) within this pane
   */
  public @NotNull List<@NotNull OptControl> allControls() {
    List<@NotNull OptControl> controls = new ArrayList<>();
    traverse(components, component -> {
      if (component instanceof OptControl control) {
        controls.add(control);
      }
      return true;
    });
    return controls;
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
  @Contract(pure = true)
  public @NotNull OptTab asTab(@NotNull @NlsContexts.TabTitle String label) {
    return new OptTab(new PlainMessage(label), components());
  }

  /**
   * Transform this pane to a checkbox with dependent controls
   *
   * @param bindId checkbox bindId
   * @param label checkbox title
   * @return a {@link OptCheckbox} object that contains all the controls from this pane as children 
   */
  @Contract(pure = true)
  public @NotNull OptCheckbox asCheckbox(@Language("jvm-field-name") @NotNull String bindId,
                                         @NotNull @NlsContexts.Label String label) {
    return new OptCheckbox(bindId, new PlainMessage(label), components(), null);
  }
  
  /**
   * @param bindPrefix prefix to add to bindId values
   * @return an equivalent component but every control has bindId prefixed with bindPrefix and dot.
   * Could be useful to compose a complex form from independent parts. To process prefixed options,
   * use {@link OptionController#onPrefix(String, OptionController)}
   */
  @Contract(pure = true)
  public @NotNull OptPane prefix(@NotNull String bindPrefix) {
    return new OptPane(ContainerUtil.map(components(), c -> c.prefix(bindPrefix)));
  }

  /**
   * @param components to prepend
   * @return a new OptPane containing all the specified components, then the components from this pane
   */
  @Contract(pure = true)
  public @NotNull OptPane prepend(@NotNull OptRegularComponent @NotNull ... components) {
    var newComponents = new ArrayList<>(Arrays.asList(components));
    newComponents.addAll(components());
    return new OptPane(newComponents);
  }

  /**
   * @param components to append
   * @return a new OptPane containing all components from this pane, then all the specified components
   */
  @Contract(pure = true)
  public @NotNull OptPane append(@NotNull OptRegularComponent @NotNull ... components) {
    var newComponents = new ArrayList<>(components());
    ContainerUtil.addAll(newComponents, components);
    return new OptPane(newComponents);
  }

  /* DSL */

  /**
   * @param components components to add to the pane. Controls in the pane are stacked vertically.
   * @return the pane
   * @see InspectionProfileEntry#getOptionsPane()
   */
  @Contract(pure = true)
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
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static @NotNull OptCheckboxPanel checkboxPanel(@NotNull OptCheckbox @NotNull ... checkboxes) {
    return new OptCheckboxPanel(List.of(checkboxes));
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be int or double
   * @param splitLabel label to display around the control
   * @param minValue   minimal allowed value of the variable
   * @param maxValue   maximal allowed value of the variable
   * @return an edit box to enter a number
   */
  @Contract(pure = true)
  public static @NotNull OptNumber number(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int minValue,
                                          int maxValue) {
    return new OptNumber(bindId, new PlainMessage(splitLabel), minValue, maxValue, null);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @return an edit box to enter a string
   */
  @Contract(pure = true)
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel) {
    return new OptString(bindId, new PlainMessage(splitLabel), null, -1, null);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param width      width of the control in approximate number of characters; if -1 then it will be determined automatically
   * @return an edit box to enter a string
   */
  @Contract(pure = true)
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int width) {
    return new OptString(bindId, new PlainMessage(splitLabel), null, width, null);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param validator  validator for content; can validate max-length or be something more complicated
   *                   (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return an edit box to enter a string
   */
  @Contract(pure = true)
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          @NotNull StringValidator validator) {
    return new OptString(bindId, new PlainMessage(splitLabel), validator, -1, null);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param splitLabel label to display around the control
   * @param width      width of the control in approximate number of characters; if -1 then it will be determined automatically
   * @param validator  validator for content; can validate max-length or be something more complicated
   *                   (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return an edit box to enter a string
   */
  @Contract(pure = true)
  public static @NotNull OptString string(@Language("jvm-field-name") @NotNull String bindId,
                                          @NotNull @NlsContexts.Label String splitLabel,
                                          int width,
                                          @NotNull StringValidator validator) {
    return new OptString(bindId, new PlainMessage(splitLabel), validator, width, null);
  }

  /**
   * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be string
   * @param label     label to display around the control
   * @param separator separator to split the string by in multi-line mode
   * @return an expandable edit box to enter a string; in expanded mode the string is being split by separator
   */
  @Contract(pure = true)
  public static OptExpandableString expandableString(@Language("jvm-field-name") @NotNull String bindId,
                                                     @NotNull @NlsContexts.Label String label,
                                                     @NotNull String separator) {
    return new OptExpandableString(bindId, new PlainMessage(label), separator, null);
  }

  /**
   * @param bindId     identifier of binding variable used by inspection; the corresponding variable is expected to be a string, int or enum
   * @param splitLabel label to display around the control
   * @param options    drop-down options
   * @return a drop-down control to select a single option from
   * @see #option(String, String)
   * @see #dropdown(String, String, Class, Function)
   */
  @Contract(pure = true)
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
  @Contract(pure = true)
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
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static @NotNull OptDropdown.Option option(@NotNull String key, @NotNull @Nls String label) {
    return new OptDropdown.Option(key, new PlainMessage(label));
  }

  /**
   * @param key   key to assign to an enum variable
   * @param label label for a given option
   * @return an option for a drop-down control
   * @see #dropdown(String, String, OptDropdown.Option...)
   */
  @Contract(pure = true)
  public static @NotNull OptDropdown.Option option(@NotNull Enum<?> key, @NotNull @Nls String label) {
    return new OptDropdown.Option(key.name(), new PlainMessage(label));
  }

  /**
   * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
   * @param label  label above the control
   * @return editable sorted list of unique strings
   */
  @Contract(pure = true)
  public static @NotNull OptStringList stringList(@Language("jvm-field-name") @NotNull String bindId,
                                                  @NotNull @NlsContexts.Label String label) {
    return new OptStringList(bindId, new PlainMessage(label), null, null);
  }

  /**
   * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
   * @param label     label above the control
   * @param validator optional validator for content; can validate max-length or be something more complicated
   *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return editable sorted list of unique strings
   */
  @Contract(pure = true)
  public static @NotNull OptStringList stringList(@Language("jvm-field-name") @NotNull String bindId,
                                                  @NotNull @NlsContexts.Label String label,
                                                  @NotNull StringValidator validator) {
    return new OptStringList(bindId, new PlainMessage(label), validator, null);
  }

  /**
   * @param label   label above the control
   * @param columns lists for every column
   * @return new table
   * @see #column(String, String) 
   * @see #column(String, String, StringValidator) 
   */
  public static @NotNull OptTable table(@NotNull @NlsContexts.Label String label, @NotNull OptTableColumn @NotNull ... columns) {
    return new OptTable(new PlainMessage(label), List.of(columns), null);
  }

  /**
   * @param bindId   identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<OptElement>}.
   * @param elements list of elements present in the list
   * @param mode     selection mode
   * @return a list with elements to select
   */
  public static @NotNull OptMultiSelector multiSelector(@Language("jvm-field-name") @NotNull String bindId,
                                                        @NotNull List<? extends OptMultiSelector.OptElement> elements,
                                                        @NotNull OptMultiSelector.SelectionMode mode) {
    return new OptMultiSelector(bindId, elements, mode);
  }

  /**
   * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
   * @param name   name of the table column
   * @return editable table column
   * @see #table(String, OptTableColumn...) 
   */
  @Contract(pure = true)
  public static @NotNull OptTableColumn column(@Language("jvm-field-name") @NotNull String bindId,
                                               @NotNull @NlsContexts.ColumnName String name) {
    return new OptTableColumn(bindId, new PlainMessage(name), null);
  }

  /**
   * @param bindId    identifier of binding variable used by inspection; the corresponding variable is expected to be a mutable {@code List<String>}.
   * @param name      name of the table column
   * @param validator optional validator for content; can validate max-length or be something more complicated
   *                  (e.g., validate that a string is a class-name which is a subclass of specific class)
   * @return editable table column
   * @see #table(String, OptTableColumn...)
   */
  @Contract(pure = true)
  public static @NotNull OptTableColumn column(@Language("jvm-field-name") @NotNull String bindId,
                                               @NotNull @NlsContexts.ColumnName String name,
                                               @NotNull StringValidator validator) {
    return new OptTableColumn(bindId, new PlainMessage(name), validator);
  }

  /* Layout elements */

  /**
   * @param label    label to display above the group
   * @param children list of child components
   * @return a group of controls with a name
   */
  @Contract(pure = true)
  public static @NotNull OptGroup group(@NotNull @NlsContexts.Label String label, @NotNull OptRegularComponent @NotNull ... children) {
    return new OptGroup(new PlainMessage(label), List.of(children));
  }

  /**
   * @param children list of child components
   * @return a horizontal stack of controls
   */
  @Contract(pure = true)
  public static @NotNull OptHorizontalStack horizontalStack(@NotNull OptRegularComponent @NotNull ... children) {
    return new OptHorizontalStack(List.of(children));
  }

  /**
   * @return an unlabeled horizontal separator
   */
  @Contract(pure = true)
  public static @NotNull OptSeparator separator() {
    return new OptSeparator(null);
  }

  /**
   * @param label to display
   * @return an labeled horizontal separator
   */
  @Contract(pure = true)
  public static @NotNull OptSeparator separator(@NotNull @NlsContexts.Label String label) {
    return new OptSeparator(new PlainMessage(label));
  }

  /**
   * @param tabs tabs description
   * @return set of tabs
   * @see #tab(String, OptComponent...)
   * @see #asTab(String) 
   */
  @Contract(pure = true)
  public static @NotNull OptTabSet tabs(@NotNull OptTab @NotNull ... tabs) {
    return new OptTabSet(List.of(tabs));
  }

  /**
   * @param label tab label
   * @param children tab content
   * @return tab description
   * @see #tabs(OptTab...) 
   */
  @Contract(pure = true)
  public static @NotNull OptTab tab(@NotNull @NlsContexts.TabTitle String label, @NotNull OptRegularComponent @NotNull ... children) {
    return new OptTab(new PlainMessage(label), List.of(children));
  }

  /**
   * @param displayName link label
   * @param configurableID ID of configurable to display
   * @return a component, which represents a link to a settings page
   */
  @Contract(pure = true)
  public static @NotNull OptSettingLink settingLink(@NotNull @NlsContexts.Label String displayName,
                                                    @NotNull @NonNls String configurableID) {
    return new OptSettingLink(new PlainMessage(displayName), configurableID, null);
  }

  /**
   * @param displayName link label
   * @param configurableID ID of configurable to display
   * @param controlLabel label of the control to focus on
   * @return a component, which represents a link to a settings page
   */
  @Contract(pure = true)
  public static @NotNull OptSettingLink settingLink(@NotNull @NlsContexts.Label String displayName,
                                                    @NotNull @NonNls String configurableID,
                                                    @NotNull @Nls String controlLabel) {
    return new OptSettingLink(new PlainMessage(displayName), configurableID, controlLabel);
  }
}

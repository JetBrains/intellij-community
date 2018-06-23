// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.ide.ui.UINumericRange;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This interface represents a named configurable component that provides a Swing form
 * to configure some settings via the Settings dialog.
 * <p/>
 * To register a custom implementation of this interface use the following extension points:
 * {@code com.intellij.applicationConfigurable} for settings, which are global for IDE,
 * and {@code com.intellij.projectConfigurable} for project settings, which are applied
 * to the current project only.  They differ only in the constructor implementation.
 * Classes for IDE settings must have a default constructor with no arguments,
 * while classes for project settings must declare a constructor with a single argument
 * of the {@link com.intellij.openapi.project.Project Project} type.
 * <p/>
 * The following attributes are available for both extension points mentioned above:
 * <dl>
 *   <dt>{@link ConfigurableEP#instanceClass instance}</dt>
 *   <dd>This attribute specifies a qualified name of a custom implementation of this interface.
 *   The constructor will be determined automatically from the tag name:
 *   <br>{@code <extensions defaultExtensionNs="com.intellij">}
 *   <br>{@code &nbsp;&nbsp;&nbsp;&nbsp;<projectConfigurable instance="fully.qualified.class.name"/>}
 *   <br>{@code </extensions>}</dd>
 *   <dt>{@link ConfigurableEP#providerClass provider}</dt>
 *   <dd>This attribute can be used instead of the {@code instance} attribute.
 *   It specifies a qualified name of a custom implementation of the {@link ConfigurableProvider} interface,
 *   which provides another way to create a configurable component:
 *   <br>{@code <extensions defaultExtensionNs="com.intellij">}
 *   <br>{@code &nbsp;&nbsp;&nbsp;&nbsp;<projectConfigurable provider="fully.qualified.class.name"/>}
 *   <br>{@code </extensions>}</dd>
 *   <dt><strike>{@link ConfigurableEP#implementationClass implementation}</strike></dt>
 *   <dd>This attribute is deprecated and replaced with two attributes above.
 *   In fact, it works as the {@code instance} attribute.</dd>
 *   <dt>{@link ConfigurableEP#nonDefaultProject nonDefaultProject}</dt>
 *   <dd>This attribute is applicable to the {@code projectConfigurable} extension only.
 *   If it is set to {@code true}, the corresponding project settings will be shown for a real project only,
 *   not for the {@link com.intellij.openapi.project.ProjectManager#getDefaultProject() template project},
 *   which provides default settings for all the new projects.</dd>
 *   <dt>{@link ConfigurableEP#displayName displayName}</dt>
 *   <dd>This attribute specifies the setting name visible to users.
 *   If the display name is not set, a configurable component will be instantiated to retrieve its name dynamically.
 *   This causes a loading of plugin classes and increases the delay before showing the settings dialog.
 *   It is highly recommended specifying the display name in XML to improve UI responsiveness.</dd>
 *   <dt>{@link ConfigurableEP#key key} and {@link ConfigurableEP#bundle bundle}</dt>
 *   <dd>These attributes specify the display name too, if the specified key is declared in the specified resource bundle.</dd>
 *   <dt>{@link ConfigurableEP#id id}</dt>
 *   <dd>This attribute specifies the {@link SearchableConfigurable#getId() unique identifier}
 *   for the configurable component.  It is also recommended specifying the identifier in XML.</dd>
 *   <dt>{@link ConfigurableEP#parentId parentId}</dt>
 *   <dd>This attribute is used to create a hierarchy of settings.
 *   If it is set, the configurable component will be a child of the specified parent component.</dd>
 *   <dt>{@link ConfigurableEP#groupId groupId}</dt>
 *   <dd>This attribute specifies a top-level group, which the configurable component belongs to.
 *   If this attribute is not set, the configurable component will be added to the Other Settings group.
 *   The following groups are supported:
 *   <dl>
 *     <dt>ROOT {@code groupId="root"}</dt>
 *     <dd>This is the invisible root group that contains all other groups.
 *     Usually, you should not place your settings here.</dd>
 *     <dt>Appearance & Behavior {@code groupId="appearance"}</dt>
 *     <dd>This group contains settings to personalize IDE appearance and behavior:
 *     change themes and font size, tune the keymap, and configure plugins and system settings,
 *     such as password policies, HTTP proxy, updates and more.</dd>
 *     <dt>Editor {@code groupId="editor"}</dt>
 *     <dd>This group contains settings to personalize source code appearance by changing fonts,
 *     highlighting styles, indents, etc.  Here you can customize the editor from line numbers,
 *     caret placement and tabs to source code inspections, setting up templates and file encodings.</dd>
 *     <dt>Default Project / Project Settings {@code groupId="project"}</dt>
 *     <dd>This group is intended to store some project-related settings, but now it is rarely used.</dd>
 *     <dt>Build, Execution, Deployment {@code groupId="build"}</dt>
 *     <dd>This group contains settings to configure you project integration with the different build tools,
 *     modify the default compiler settings, manage server access configurations, customize the debugger behavior, etc.</dd>
 *     <dt>Build Tools {@code groupId="build.tools"}</dt>
 *     <dd>This is subgroup of the group above. Here you can configure your project integration
 *     with the different build tools, such as Maven, Gradle, or Gant.</dd>
 *     <dt>Languages & Frameworks {@code groupId="language"}</dt>
 *     <dd>This group is intended to configure the settings related to specific frameworks and technologies used in your project.</dd>
 *     <dt>Tools {@code groupId="tools"}</dt>
 *     <dd>This group contains settings to configure integration with third-party applications,
 *     specify the SSH Terminal connection settings, manage server certificates and tasks, configure diagrams layout, etc.</dd>
 *     <dt>Other Settings {@code groupId="other"}</dt>
 *     <dd>This group contains settings that are related to non-bundled custom plugins and are not assigned to any other category.</dd>
 *   </dl>
 *   The {@code parentId} and the {@code groupId} attributes should not be used together and the {@code parentId} has precedence.
 *   Currently, it is possible to specify a group identifier in the {@code parentId} attribute.</dd>
 *   <dt>{@link ConfigurableEP#groupWeight groupWeight}</dt>
 *   <dd>This attribute specifies the weight of a configurable component within a group or a parent configurable component.
 *   The default weight is {@code 0}. If one child in a group or a parent configurable component has non-zero weight,
 *   all children will be sorted descending by their weight. And if the weights are equal,
 *   the components will be sorted ascending by their display name.</dd>
 *   <dt>{@link ConfigurableEP#dynamic dynamic}</dt>
 *   <dd>This attribute states that a custom configurable component implements the {@link Composite} interface
 *   and its children are dynamically calculated by calling the {@code getConfigurables} method.
 *   It is needed to improve performance, because we do not want to load any additional classes during the building a setting tree.</dd>
 *   <dt>{@link ConfigurableEP#childrenEPName childrenEPName}</dt>
 *   <dd>This attribute specifies a name of the extension point that will be used to calculate children.</dd>
 *   <dt>{@link ConfigurableEP#children configurable}</dt>
 *   <dd>This is not an attribute, this is an inner tag. It specifies children directly in the main tag body. For example,
 *   <br>{@code <projectConfigurable id="tasks" nonDefaultProject="true" instance="com.intellij.tasks.config.TaskConfigurable">}
 *   <br>{@code &nbsp;&nbsp;&nbsp;&nbsp;<configurable id="tasks.servers" instance="com.intellij.tasks.config.TaskRepositoriesConfigurable"/>}
 *   <br>{@code </projectConfigurable>}
 *   <br>is similar to the following declarations
 *   <br>{@code <projectConfigurable id="tasks" nonDefaultProject="true" instance="com.intellij.tasks.config.TaskConfigurable"/>}
 *   <br>{@code <projectConfigurable parentId="tasks" id="tasks.servers" nonDefaultProject="true" instance="com.intellij.tasks.config.TaskRepositoriesConfigurable"/>}</dd>
 * </dl>
 *
 * @see ConfigurableEP
 * @see SearchableConfigurable
 */
public interface Configurable extends UnnamedConfigurable {

  ExtensionPointName<ConfigurableEP<Configurable>> APPLICATION_CONFIGURABLE = ExtensionPointName.create("com.intellij.applicationConfigurable");

  ExtensionPointName<ConfigurableEP<Configurable>> PROJECT_CONFIGURABLE = ExtensionPointName.create("com.intellij.projectConfigurable");

  /**
   * Returns the visible name of the configurable component.
   * Note, that this method must return the display name
   * that is equal to the display name declared in XML
   * to avoid unexpected errors.
   *
   * @return the visible name of the configurable component
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  String getDisplayName();

  /**
   * Returns the topic in the help file which is shown when help for the configurable is requested.
   *
   * @return the help topic, or {@code null} if no help is available
   */
  @Nullable
  @NonNls
  default String getHelpTopic() {
    return null;
  }

  /**
   * This interface represents a configurable component that has child components.
   * It is not recommended to use this approach to specify children of a configurable component,
   * because it causes loading additional classes during the building a setting tree.
   * Use XML attributes instead if possible.
   */
  @FunctionalInterface
  interface Composite {
    @NotNull
    Configurable[] getConfigurables();
  }

  /**
   * This marker interface notifies the Settings dialog to not add scroll bars to the Swing form.
   * Required when the Swing form contains its own scrollable components.
   */
  interface NoScroll {
    // see ConfigurableCardPanel#create(Configurable)
  }

  /**
   * This marker interface notifies the Settings dialog to not add an empty border to the Swing form.
   * Required when the Swing form is a tabbed pane.
   */
  interface NoMargin {
    // see ConfigurableCardPanel#create(Configurable)
  }

  /**
   * Allows to dynamically define if current configurable settings apply to current project or to the IDE and update "For current project" 
   * indicator accordingly.
   */
  interface VariableProjectAppLevel {
    /**
     * @return True if current settings apply to the current project (enable "For current project" indicator), false for application-level
     *         (IDE) settings.
     */
    boolean isProjectLevel();
  }

  default boolean isModified(@NotNull JTextField textField, @NotNull String value) {
    return !StringUtil.equals(textField.getText().trim(), value);
  }

  default boolean isModified(@NotNull JTextField textField, int value, @NotNull UINumericRange range) {
    try {
      return range.fit(Integer.parseInt(textField.getText().trim())) != value;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  default boolean isModified(@NotNull JToggleButton toggleButton, boolean value) {
    return toggleButton.isSelected() != value;
  }

  default <T> boolean isModified(@NotNull ComboBox<T> comboBox, T value) {
    return !Comparing.equal(comboBox.getSelectedItem(), value);
  }

  /**
   * @return component which should be focused when the dialog appears
   *         on the screen.
   */
  default JComponent getPreferredFocusedComponent() {
    return null;
  }

  interface TopComponentController {
    void setLeftComponent(@Nullable Component component);

    void showProgress(boolean start);
  }

  interface TopComponentProvider {
    default boolean isAvailable() {
      return true;
    }

    @NotNull
    Component getCenterComponent(@NotNull TopComponentController controller);
  }
}

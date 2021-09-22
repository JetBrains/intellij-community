Configurables allow adding settings of your plugin to the IDEA Settings dialog.

### XML Declaration

First of all, you should create a custom implementation of the `com.intellij.openapi.options.Configurable` interface and register it in the `plugin.xml` file. Two following XML tags enable you to specify the extension points: `applicationConfigurable` for settings, which are global for IDE, and `projectConfigurable` for project settings, which are applied to the current project only. They differ only in the constructor implementation. Classes for IDE settings must have a default constructor with no arguments, while classes for project settings must declare a constructor with a single argument of the `Project` type.

Consider the following attributes for both tags mentioned above:

`instance`

This attribute specifies a qualified name for your implementation of the `Configurable` interface. The constructor will be determined automatically from the tag name.

`provider`

This attribute can be used instead of the `instance` attribute. It specifies a qualified name for your implementation of the `com.intellij.openapi.options.ConfigurableProvider` interface, which provides another way to create your configurable component.

<del>`implementation`</del>

This attribute is deprecated and replaced with two attributes above. In fact, it works as the `instance` attribute.

`nonDefaultProject`

This attribute is applicable to the `projectConfigurable` tag only. If it is set to `true`, the corresponding project settings will be shown for a real project only, not for the default pseudo project, which provides default settings for all the new projects.

`displayName`

This attribute specifies the setting name visible to users. If you do not set the display name, a configurable component will be instantiated to retrieve its name dynamically. This causes a loading of plugin classes and increases the delay before showing the settings dialog. I highly recommend specifiyng the display name in XML to improve UI responsiveness.

`key` and `bundle`

These attributes specify the display name too, if the specified key is declared in the specified resource bundle.

`id`

This attribute specifies the unique identifier for the configurable component. I also recommend specifiyng the identifier in XML.

`parentId`

This attribute is used to create a hierarchy of settings. If it is set, the configurable component will be a child of the specified parent component. **NB!:** Currently, it is not possible to use IDE settings as a parent for project settings and vice versa. I'm going to fix it in IDEA 15.

`groupId`

This attribute specifies a top-level group, which the configurable component belongs to. If this attribute is not set, the configurable component will be added to the Other Settings group.

ROOT `groupId="root"`

This is the invisible root group that contains all other groups. Usually, you should not place your settings here.

Appearance & Behavior `groupId="appearance"`

This group contains settings to personalize IDE appearance and behavior: change themes and font size, tune the keymap, and configure plugins and system settings, such as password policies, HTTP proxy, updates and more.

Editor `groupId="editor"`

This group contains settings to personalize source code appearance by changing fonts, highlighting styles, indents, etc. Here you can customize the editor from line numbers, caret placement and tabs to source code inspections, setting up templates and file encodings.

Default Project / Project Settings `groupId="project"`

This group is intended to store some project-related settings, but now it is rarely used.

Build, Execution, Deployment `groupId="build"`

This group contains settings to configure you project integration with the different build tools, modify the default compiler settings, manage server access configurations, customize the debugger behavior, etc.

Build Tools `groupId="build.tools"`

This is subgroup of the group above. Here you can configure your project integration with the different build tools, such as Maven, Gradle, or Gant.

Languages & Frameworks `groupId="language"`

This group is intended to configure the settings related to specific frameworks and technologies used in your project.

Tools `groupId="tools"`

This group contains settings to configure integration with third-party applications, specify the SSH Terminal connection settings, manage server certificates and tasks, configure diagrams layout, etc.

Other Settings `groupId="null"`

This group contains settings that are related to non-bundled custom plugins and are not assigned to any other category. **NB!:** The group id will be renamed to `"other"` in IDEA 15.

**NB!:** I'm going to join the `parentId` and the `groupId` attributes, because only one of them can be used. For now the `parentId` has precedence.

`groupWeight`

This attribute specifies the weight of your configurable component within a group or a parent configurable component. The default weight is `0`. If one child in a group or a parent configurable component has non-zero weight, all children will be sorted descending by their weight. If the weights are equal, the components will be sorted ascending by their display name. **NB!:** I'm going to rename it to `parentWeight` later.

`dynamic`

This attribute states that your configurable component implements the `Configurable.Composite` interface and its children are dynamically calculated by calling the `getConfigurables` method. It is needed to improve performance, because we do not want to load any additional classes during the building a setting tree. **NB!:** By this reason, I do not recommend to use this approach to specify children of a configurable component. I hope later it will be deprecated.

`childrenEPName`

This attribute specifies a name of the extension point that will be used to calculate children.

`configurable`

This is not an attribute, this is an inner tag. It specifies children directly in the main tag body. For example,

<projectConfigurable groupId="tools" id="tasks" displayName="Tasks" nonDefaultProject="true"
instance="com.intellij.tasks.config.TaskConfigurable">
<configurable id="tasks.servers" displayName="Servers"
instance="com.intellij.tasks.config.TaskRepositoriesConfigurable"/>
&lt;/projectConfigurable&gt;

is similar to the following declarations

<projectConfigurable groupId="tools" id="tasks" displayName="Tasks" nonDefaultProject="true"
instance="com.intellij.tasks.config.TaskConfigurable"/>

<projectConfigurable parentId="tasks" id="tasks.servers" displayName="Servers" nonDefaultProject="true"
instance="com.intellij.tasks.config.TaskRepositoriesConfigurable"/>

### Java Implementation

IDEA tries to use a background thread to instantiate a custom implementation of the `Configurable` interface registered in the `plugin.xml` file. However, in some cases it is impossible, so do not run a long task in the constructor of the configurable component.

The `UnnamedConfigurable` interface represents a component that enables you to configure settings.

`JComponent createComponent()`

This method creates a Swing form that enables user to configure settings. It is called on EDT, so it should not take a long time.

`void reset()`

This method sets values of all components on the created Swing form to default values. It is called on EDT immediately after the form creation or later upon user's request. It also should not take a long time. If you need some time to calculate default values, do it in a background thread, but do not forget to disable form editing.

`boolean isModified()`

This method indicates whether the form was modified or not. It is called very often, so you can try to optimize it.

`void apply() throws ConfigurationException`

This method configures your plugin from a form's values. It is called on EDT upon user's request and may throw `ConfigurationException`, which mean that values cannot be applied and the form should not be closed.

`void disposeUIResources()`

This method is called on closing the Settings dialog if the Swing form created. You should dispose all your resources here to avoid memory leaks. For example, remove global listeners or dependencies.

The `Configurable` interface extends the `UnnamedConfigurable` interface and adds the following methods:

`String getDisplayName()`

This method returns the visible name of the settings component. Note, that this method must return the display name that is equal to the display name declared in XML to avoid unexpected errors.

`String getHelpTopic()`

This method returns the unique name of the topic in the help file. It may return `null` if there are no any corresponding topic.

The `SearchableConfigurable` interface extends the `Configurable` interface and adds the following methods:

`String getId()`

This method returns the unique identifier for your settings component. Note, that this method must return the identifier that is equal to the identifier declared in XML to avoid unexpected errors.

`Runnable enableSearch(String option)`

This method returns an action to perform when this configurable component is opened if a search filter query is entered by the user in the Settings dialog.

The `Configurable.NoScroll` interface is a marker interface that notifies the Settings dialog to not add scroll bars to the configurable component. It is useful for complex components, which use scroll bars internally.

The `Configurable.NoMargin` interface is a marker interface which notifies the Settings dialog to not add an empty border to the configurable component. It is useful for tabbed panes.

The <del>`Configurable.Assistant`</del> interface is deprecated. This marker interface was intended to hide a configurable component from the Settings dialog. However, it makes no sense to register it as extension if you don't want to see it

The <del>`NonDefaultProjectConfigurable`</del> interface is deprecated. This marker interface was replaced with the `nonDefaultProject` attribute.

The <del>`OptionalConfigurable`</del> interface is deprecated. This interface was intended to hide a configurable component from the Settings dialog at runtime depending on the result of the `needDisplay` method. It can be used for simple configurable components. However, I recommend to use the following approach instead.

The `ConfigurableProvider` class is intended to hide a configurable component from the Settings dialog at runtime. A configurable component should not extend this abstract class. Instead, you should create a light-weight implementation with the following methods:

`boolean canCreateConfigurable()`

This method specifies whether it is possible to create a configurable component or not.

`Configurable createConfigurable()`

This method creates a configurable component and returns it. Note, that this method will be called if and only if the method above returns `true`.
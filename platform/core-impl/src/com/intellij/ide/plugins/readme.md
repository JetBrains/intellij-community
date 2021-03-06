## "Plugin" vs "Module"
Currently, plugin it is entity that consists of one or more [IJ IDEA modules](https://www.jetbrains.com/help/idea/creating-and-managing-modules.html).
Currently, IJ IDEA module is not reflected in any way in a plugin — only as part of build script, where plugin layout is described.
It is going to be changed — IJ Module will be integral part of plugin subsystem.
Plugin descriptor will reference all modules that forms its content, and plugin descriptor itself it is a specific form of module descriptor.

Every plugin it is a module, but not every module it is a plugin.
Term "optional descriptor" in a new format is equivalent to module dependency of plugin. 

## The `package` attribute
The `package` attribute determines JVM package where all module classes are located. Recursively — `com.example` implies `com.example.sub` also.

Required for new format version, optional for old one.

If package is specified:

 * All module dependencies of plugin must also specify package, and it must be different. For example, if `com.example` is set for main plugin descriptor, `com.example` cannot be used for any module dependency. Classes in modules specified in [dependencies](#the-dependencies-element), must be in a different package.
 * Icon class generator uses specified package for icon class but not `icons`. If you already have icon class, recommended moving existing icon class before running icon class generator to ensure that all references to icon class are updated by IntelliJ IDEA refactoring. Also, icon class generator uses the same icon class name as before if old one is detected in place.

## The `content` element
The `content` element determines content of the plugin. Plugin consists of modules. Module, where `plugin.xml` is located, is implicitly added, and you don't have to specify it.

```xml
<content>
  <module name="intellij.clouds.docker.file" package="com.intellij.docker.dockerFile"/>
</content>
```

| Element | Minimum | Maximum   | Description                        |
|---------|---------|-----------|------------------------------------|
| module  | 0       | unbounded | A module which should be included. |

### The `content.module` element
| Attribute | Type   | Use      | Description                                                                                                                               |
|-----------|--------|----------|-------------------------------------------------------------------------------------------------------------------------------------------|
| name      | string | required | The name of the module.                                                                                                                   |
| package   | string | required | The package of the module. Duplicates the `package` specified in the referenced module, but for now it is required for technical reasons. |

There is an important difference between content specified for a plugin and for a module.
 * For a plugin, the referenced module _is not added_ to classpath but just forms plugin content. Still, if some another plugin depends on this plugin, it can use classloader of that module. For time being, included into plugin module, doesn’t have own classloader, but it will be changed in the future and explicit dependency on plugin’s module must be added if needed.
 * For a module, the referenced module doesn't have own classloader and is added directly to classpath. In the future `content` will be prohibited for modules, but for now it is way to include some module directly to classpath.

## The `dependencies` element
The `dependencies` element determines dependencies of the module.

```xml
<dependencies>
  <plugin id="org.jetbrains.plugins.yaml"/>
</dependencies>
```

| Element | Minimum | Maximum   | Description                                       |
|---------|---------|-----------|---------------------------------------------------|
| module  | 0       | unbounded | A module upon which a dependency should be added. |
| plugin  | 0       | unbounded | A plugin upon which a dependency should be added. |

### The `dependencies.plugin` element
| Attribute | Type   | Use      | Description           |
|-----------|--------|----------|-----------------------|
| id        | string | required | The id of the plugin. |

Not used for now and not supported. [Marketplace](https://github.com/JetBrains/intellij-plugin-verifier/tree/master/intellij-plugin-structure) is not yet ready to support a new format.

Old format

```xml
<depends>Docker</depends>
```

is still used.

### The `dependencies.module` element

| Attribute | Type   | Use      | Description             |
|-----------|--------|----------|-------------------------|
| name      | string | required | The name of the module. |

The module must have descriptor file with the same name as module, e.g. for module `intellij.clouds.docker.compose` must be a descriptor file `intellij.clouds.docker.compose.xml` in the module root.

Module dependency is always optional. If module depends on an unavailable plugin, it will be not loaded.
In this meaning for now module dependency makes sense only for plugins, but not for modules. It will be changed in the future, when all plugins will act as a module.

The module added to classpath of dependent module but dependency itself is not able to access the dependent module (as it was earlier). Currently, it is packaged into the same JAR, but packaging it is implementation details that are hidden and maybe changed in any moment or maybe different in a different execution environment. The same classloader configuration is guaranteed, but not packaging, and independence from packaging it is a one of the goal of a new descriptor format.

In the old plugin descriptor format:
 * tag `depends` with `config-file` it is `dependency.module`.
 * tag `depends` without `optional` it is `dependency.plugin`.

Note: for now you also must specify dependency in an old format.

## InternalIgnoreDependencyViolation

Annotation `InternalIgnoreDependencyViolation` allows class to be used among various plugins.
By default, classloader of extension class must be equal to classloader of plugin where extension is defined.
It means that class of extension must be located in a module where corresponding plugin descriptor is located. 

Generally, IJ Platform prohibits referencing extension class belonging to `plugin1` from the `plugin.xml` in `plugin2`.
However, sometimes this kind of dependency violation is necessary, in which case the corresponding extension must be annotated with this annotation.

For example:

##### `plugin.xml`
```xml:plugin.xml
  <findUsageHandler implementation="com.plugin1.MyHandler"/>
```

##### `MyHandler.java`
```java
  @InternalIgnoreDependencyViolation
  final class MyHandler {
  }
```
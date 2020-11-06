## InternalIgnoreDependencyViolation

Annotation `InternalIgnoreDependencyViolation` allows class to be used among various plugins.
By default, classloader of extension class must be equal to classloader of plugin where extension is defined.
It means that class of extension must be located in a module where corresponding plugin descriptor is located. 

Generally, IJ Platform prohibits referencing extension class belonging to `plugin1` from the `plugin.xml` in `plugin2`.
However, sometimes this kind of dependency violation is necessary, in which case the corresponding extension must be annotated with this annotation.

For example:

#### `plugin.xml`
```xml:plugin.xml
  <findUsageHandler implementation="com.plugin1.MyHandler"/>
```

#### `MyHandler.java`
```java
  @InternalIgnoreDependencyViolation
  final class MyHandler {
  }
```

## Package for Plugin

Attribute `package` specifies JVM package where all plugin classes are located. Recursively — `com.example` implies `com.example.sub` also.

 * If package is specified, all plugin optional descriptors must also specify package, and it must be different. For example, if `com.example` specified for main plugin descriptor, `com.example` cannot be used anymore for any optional descriptor. Classes referenced by optional descriptors, must be in a different package.
 * Icon class generator will use specified package for icon class but not `icons`. If you already have icon class, recommended moving existing icon class before running icon class generator to ensure that all references to icon class are updated by IDEA refactoring and icon class generator will use the same icon class name as before.
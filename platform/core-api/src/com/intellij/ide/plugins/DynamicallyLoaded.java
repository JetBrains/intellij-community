// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates classes/interfaces which are packaged into the plugin's JAR files but aren't statically referenced from main plugin classes.
 * Such classes may be loaded either by custom classloaders or by the standard plugin classloader but only if some conditions are met
 * (e.g. the plugin is running under a specific version of the IDE). Classes marked with this annotation will be excluded from bytecode
 * verification procedures because they may contain references to classes which aren't available in the plugin's code and libraries.
 *
 * @author nik
 */
@Target(ElementType.TYPE)
public @interface DynamicallyLoaded {
}

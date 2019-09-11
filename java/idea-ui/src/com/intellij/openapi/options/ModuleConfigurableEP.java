// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.module.Module;

/**
 * To provide an additional tab for a module editor register implementation of {@link Configurable} in the plugin.xml:
 * <p/>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;moduleConfigurable instance="class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * A new instance of the specified class will be created each time then the Project Structure dialog is opened
 *
 * @author nik
 */
public final class ModuleConfigurableEP extends ConfigurableEP<Configurable> {
  public ModuleConfigurableEP(Module module) {
    super(module);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.build;

import com.intellij.util.ParameterizedRunnable;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated the build scripts don't use Groovy anymore, so a path to the config directory must be passed instead of using groovy scripts
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public final class GroovyModelInitializer implements ParameterizedRunnable<JpsModel> {
  private final File myScriptFile;

  public GroovyModelInitializer(File scriptFile) {
    myScriptFile = scriptFile;
  }

  @Override
  public void run(JpsModel model) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("project", model.getProject());
    variables.put("global", model.getGlobal());
    try {
      new GroovyShell(new Binding(variables)).evaluate(myScriptFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

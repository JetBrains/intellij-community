/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * @author nik
 */
public class GroovyModelInitializer implements ParameterizedRunnable<JpsModel> {
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

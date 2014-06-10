/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverter implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter");

  public static BytecodeAnalysisConverter getInstance() {
    return ApplicationManager.getApplication().getComponent(BytecodeAnalysisConverter.class);
  }

  PersistentStringEnumerator internalKeyEnumerator;

  IntIdEquation convert(Equation<Key, Value> equation) throws IOException {
    Result<Key, Value> rhs = equation.rhs;
    IntIdResult result;
    if (rhs instanceof Final) {
      result = new IntIdFinal(((Final<Key, Value>)rhs).value);
    } else {
      Pending<Key, Value> pending = (Pending<Key, Value>)rhs;
      Set<Set<Key>> deltaOrig = pending.delta;
      IntIdComponent[] components = new IntIdComponent[deltaOrig.size()];
      int componentI = 0;
      for (Set<Key> keyComponent : deltaOrig) {
        int[] ids = new int[keyComponent.size()];
        int idI = 0;
        for (Key id : keyComponent) {
          ids[idI] = internalKeyEnumerator.enumerate(Util.internalKeyString(id));
          idI++;
        }
        IntIdComponent intIdComponent = new IntIdComponent(ids);
        components[componentI] = intIdComponent;
        componentI++;
      }
      result = new IntIdPending(pending.infinum, components);
    }
    int key = internalKeyEnumerator.enumerate(Util.internalKeyString(equation.id));
    return new IntIdEquation(key, result);
  }

  @Override
  public void initComponent() {
    try {
      File dir = new File(PathManager.getIndexRoot(), "bytecodeKeys");
      final File internalKeysFile = new File(dir, "faba.internalIds");
      internalKeyEnumerator = new PersistentStringEnumerator(internalKeysFile);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void disposeComponent() {
    try {
      internalKeyEnumerator.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "BytecodeAnalysisConverter";
  }
}

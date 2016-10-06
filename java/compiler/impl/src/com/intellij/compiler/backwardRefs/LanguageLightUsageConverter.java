/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerElement;
import com.sun.tools.javac.util.Convert;
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator;
import org.jetbrains.jps.backwardRefs.LightUsage;

public interface LanguageLightUsageConverter {
  LanguageLightUsageConverter[] INSTANCES = new LanguageLightUsageConverter[]{new Java()};

  LightUsage asLightUsage(CompilerElement element, ByteArrayEnumerator names);

  class Java implements LanguageLightUsageConverter {
    @Override
    public LightUsage asLightUsage(CompilerElement element, ByteArrayEnumerator names) {
      if (element instanceof CompilerElement.CompilerClass) {
        return new LightUsage.LightClassUsage(id(((CompilerElement.CompilerClass)element).getJavacName(), names));

      }
      else if (element instanceof CompilerElement.CompilerMethod) {
        final CompilerElement.CompilerMethod method = (CompilerElement.CompilerMethod)element;
        return new LightUsage.LightMethodUsage(id(method.getJavacClassName(), names),
                                               id(method.getJavacMethodName(), names),
                                               method.getJavacParameterCount());

      }
      else if (element instanceof CompilerElement.CompilerField) {
        final CompilerElement.CompilerField field = (CompilerElement.CompilerField)element;
        return new LightUsage.LightFieldUsage(id(field.getJavacClassName(), names),
                                              id(field.getJavacName(), names));

      }
      else if (element instanceof CompilerElement.CompilerFunExpr) {
        final CompilerElement.CompilerFunExpr field = (CompilerElement.CompilerFunExpr)element;
        return new LightUsage.LightFunExprUsage(id(field.getJavacClassName(), names));

      }
      return null;
    }

    private static int id(String name, ByteArrayEnumerator names) {
      return names.enumerate(Convert.string2utf(name));
    }
  }
}

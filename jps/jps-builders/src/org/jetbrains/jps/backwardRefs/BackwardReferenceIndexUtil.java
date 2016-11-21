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
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BackwardReferenceIndexUtil {
  static void registerFile(String filePath,
                           Set<? extends JavacRef> refs,
                           List<JavacDef> defs,
                           BackwardReferenceIndexWriter writer) {
    final int fileId = writer.enumeratePath(filePath);
    int funExprId = 0;

    final List<LightRef> definitions = new ArrayList<LightRef>(defs.size());
    for (JavacDef def : defs) {
      if (def instanceof JavacDef.JavacClassDef) {
        JavacRef.JavacClass sym = (JavacRef.JavacClass)def.getDefinedElement();
        final LightRef.JavaLightClassRef aClass = writer.asClassUsage(sym);
        definitions.add(aClass);

        final JavacRef[] superClasses = ((JavacDef.JavacClassDef)def).getSuperClasses();
        final LightRef.JavaLightClassRef[] lightSuperClasses = new LightRef.JavaLightClassRef[superClasses.length];
        for (int i = 0; i < superClasses.length; i++) {
          JavacRef superClass = superClasses[i];
          lightSuperClasses[i] = writer.asClassUsage(superClass);
        }

        writer.writeHierarchy(fileId, aClass, lightSuperClasses);
      }
      else if (def instanceof JavacDef.JavacFunExprDef) {
        final LightRef.JavaLightClassRef functionalType = writer.asClassUsage(def.getDefinedElement());
        int id = funExprId++;
        LightRef.JavaLightFunExprDef result = new LightRef.JavaLightFunExprDef(id);
        definitions.add(result);
        writer.writeHierarchy(fileId, result, functionalType);
      }
    }

    writer.writeClassDefinitions(fileId, definitions);
    writer.writeReferences(fileId, refs);
  }
}

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

import com.intellij.openapi.util.Factory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BackwardReferenceIndexUtil {
  static void registerFile(String filePath,
                           TObjectIntHashMap<? extends JavacRef> refs,
                           List<JavacDef> defs,
                           final BackwardReferenceIndexWriter writer) {
    final int fileId = writer.enumeratePath(filePath);
    int funExprId = 0;

    final Map<LightRef, Boolean> definitions = new HashMap<>(defs.size());
    final Map<LightRef, Collection<LightRef>> backwardHierarchyMap = new HashMap<>();

    for (JavacDef def : defs) {
      if (def instanceof JavacDef.JavacClassDef) {
        JavacRef.JavacClass sym = (JavacRef.JavacClass)def.getDefinedElement();
        final LightRef.JavaLightClassRef aClass = writer.asClassUsage(sym);
        definitions.put(aClass, Boolean.valueOf(sym.isAnonymous()));

        final JavacRef[] superClasses = ((JavacDef.JavacClassDef)def).getSuperClasses();
        for (JavacRef superClass : superClasses) {
          LightRef.JavaLightClassRef superClassRef = writer.asClassUsage(superClass);

          backwardHierarchyMap.computeIfAbsent(superClassRef, k -> new SmartList<>()).add(aClass);
        }
      }
      else if (def instanceof JavacDef.JavacFunExprDef) {
        final LightRef.JavaLightClassRef functionalType = writer.asClassUsage(def.getDefinedElement());
        int id = funExprId++;
        LightRef.JavaLightFunExprDef result = new LightRef.JavaLightFunExprDef(id);
        definitions.put(result, Boolean.TRUE);

        ContainerUtil.getOrCreate(backwardHierarchyMap, functionalType,
                                  (Factory<Collection<LightRef>>)() -> new SmartList<>()).add(result);
      }
    }

    Map<LightRef, Integer> convertedRefs = new THashMap<>();
    refs.forEachEntry((ref, count) -> {
      final LightRef lightRef = writer.enumerateNames(ref);
      if (lightRef != null) {
        convertedRefs.put(lightRef, count);
      }
      return true;
    });
    writer.writeData(fileId, new CompiledFileData(backwardHierarchyMap, convertedRefs, definitions));
  }
}

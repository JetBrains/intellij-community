/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.io.IOException;
import java.util.*;

public class BackwardReferenceIndexUtil {
  static void registerFile(String filePath,
                           TObjectIntHashMap<? extends JavacRef> refs,
                           List<JavacDef> defs,
                           final JavacReferenceIndexWriter writer) {

    try {
      final int fileId = writer.enumeratePath(filePath);
      int funExprId = 0;

      final Map<LightRef, Void> definitions = new HashMap<>(defs.size());
      final Map<LightRef, Collection<LightRef>> backwardHierarchyMap = new HashMap<>();
      final Map<SignatureData, Collection<LightRef>> signatureData = new THashMap<>();

      final AnonymousClassEnumerator anonymousClassEnumerator = new AnonymousClassEnumerator();

      for (JavacDef def : defs) {
        if (def instanceof JavacDef.JavacClassDef) {
          JavacRef.JavacClass sym = (JavacRef.JavacClass)def.getDefinedElement();

          final LightRef.LightClassHierarchyElementDef aClass;
          if (sym.isAnonymous()) {
            final JavacRef[] classes = ((JavacDef.JavacClassDef)def).getSuperClasses();
            aClass = anonymousClassEnumerator.addAnonymous(sym.getName(), writer.asClassUsage(classes[0]));
          } else {
            aClass = writer.asClassUsage(sym);
          }
          definitions.put(aClass, null);

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
          definitions.put(result, null);

          ContainerUtil.getOrCreate(backwardHierarchyMap, functionalType,
                                    (Factory<Collection<LightRef>>)() -> new SmartList<>()).add(result);
        }
        else if (def instanceof JavacDef.JavacMemberDef) {
          final LightRef ref = writer.enumerateNames(def.getDefinedElement(), name -> anonymousClassEnumerator.getLightRefIfAnonymous(name));
          final LightRef.JavaLightClassRef returnType = writer.asClassUsage(((JavacDef.JavacMemberDef)def).getReturnType());
          if (ref != null && returnType != null) {
            final SignatureData data = new SignatureData(returnType.getName(), ((JavacDef.JavacMemberDef)def).getIteratorKind(), ((JavacDef.JavacMemberDef)def).isStatic());
            signatureData.computeIfAbsent(data, element -> new SmartList<>()).add(ref);
          }
        }
      }

      Map<LightRef, Integer> convertedRefs = new THashMap<>();
      IOException[] exception = new IOException[]{null};
      refs.forEachEntry((ref, count) -> {
        final LightRef lightRef;
        try {
          lightRef = writer.enumerateNames(ref, name -> anonymousClassEnumerator.getLightRefIfAnonymous(name));
          if (lightRef != null) {
            Integer old = convertedRefs.get(lightRef);
            convertedRefs.put(lightRef, old == null ? count : (old + count));
          }
        }
        catch (IOException e) {
          exception[0] = e;
          return false;
        }
        return true;
      });
      if (exception[0] != null) {
        throw exception[0];
      }
      writer.writeData(fileId, new CompiledFileData(backwardHierarchyMap, convertedRefs, definitions, signatureData));
    }
    catch (IOException e) {
      writer.setRebuildCause(e);
    }
  }

  private static class AnonymousClassEnumerator {
    private THashMap<String, LightRef.LightClassHierarchyElementDef> myAnonymousName2Id = null;

    private LightRef.JavaLightAnonymousClassRef addAnonymous(String internalName,
                                                             LightRef.JavaLightClassRef base) {
      if (myAnonymousName2Id == null) {
        myAnonymousName2Id = new THashMap<>();
      }
      final int anonymousIdx = myAnonymousName2Id.size();
      myAnonymousName2Id.put(internalName, base);
      return new LightRef.JavaLightAnonymousClassRef(anonymousIdx);
    }

    private Integer getLightRefIfAnonymous(String className) {
      if (myAnonymousName2Id == null) return null;
      final LightRef.LightClassHierarchyElementDef ref = myAnonymousName2Id.get(className);
      return ref == null ? null : ref.getName();
    }
  }
}

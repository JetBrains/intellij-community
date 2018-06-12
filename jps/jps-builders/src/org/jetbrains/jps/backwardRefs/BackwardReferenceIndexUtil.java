// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BackwardReferenceIndexUtil {
  private static final Logger LOG = Logger.getInstance(BackwardReferenceIndexUtil.class);

  static void registerFile(String filePath,
                           TObjectIntHashMap<? extends JavacRef> refs,
                           Collection<JavacDef> defs,
                           Collection<JavacTypeCast> casts,
                           Collection<JavacRef> implicitToString,
                           final BackwardReferenceIndexWriter writer) {

    try {
      final int fileId = writer.enumeratePath(filePath);
      int funExprId = 0;

      Map<LightRef, Void> definitions = new HashMap<>(defs.size());
      Map<LightRef, Collection<LightRef>> backwardHierarchyMap = new HashMap<>();
      Map<SignatureData, Collection<LightRef>> signatureData = new THashMap<>();
      Map<LightRef, Collection<LightRef>> castMap = new THashMap<>();
      Map<LightRef, Void> implicitToStringMap = new THashMap<>();

      final AnonymousClassEnumerator anonymousClassEnumerator = new AnonymousClassEnumerator();

      for (JavacDef def : defs) {
        if (def instanceof JavacDef.JavacClassDef) {
          JavacRef.JavacClass sym = (JavacRef.JavacClass)def.getDefinedElement();

          final LightRef.LightClassHierarchyElementDef aClass;
          if (sym.isAnonymous()) {
            final JavacRef[] classes = ((JavacDef.JavacClassDef)def).getSuperClasses();
            if (classes.length == 0) {
              LOG.info("Seems that compilation will finish with errors in anonymous class inside file " + filePath);
              continue;
            }
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

      for (JavacTypeCast cast : casts) {
        LightRef enumeratedCastType = writer.enumerateNames(cast.getCastType(), name -> null);
        if (enumeratedCastType == null) continue;
        LightRef enumeratedOperandType = writer.enumerateNames(cast.getOperandType(), name -> null);
        if (enumeratedOperandType == null) continue;
        castMap.computeIfAbsent(enumeratedCastType, t -> new SmartList<>()).add(enumeratedOperandType);
      }

      for (JavacRef ref : implicitToString) {
        implicitToStringMap.put(writer.asClassUsage(ref), null);
      }

      writer.writeData(fileId, new CompiledFileData(backwardHierarchyMap, castMap, convertedRefs, definitions, signatureData, implicitToStringMap));
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

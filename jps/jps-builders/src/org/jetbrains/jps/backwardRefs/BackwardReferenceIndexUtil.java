// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class BackwardReferenceIndexUtil {
  private static final Logger LOG = Logger.getInstance(BackwardReferenceIndexUtil.class);

  static void registerFile(String filePath,
                           Iterable<Map.Entry<? extends JavacRef, Integer>> refs,
                           Collection<? extends JavacDef> defs,
                           Collection<? extends JavacTypeCast> casts,
                           Collection<? extends JavacRef> implicitToString,
                           final JavaBackwardReferenceIndexWriter writer) {

    try {
      final int fileId = writer.enumeratePath(filePath);
      int funExprId = 0;

      Map<CompilerRef, Void> definitions = new HashMap<>(defs.size());
      Map<CompilerRef, Collection<CompilerRef>> backwardHierarchyMap = new HashMap<>();
      Map<SignatureData, Collection<CompilerRef>> signatureData = new HashMap<>();
      Map<CompilerRef, Collection<CompilerRef>> castMap = new HashMap<>();
      Map<CompilerRef, Void> implicitToStringMap = new HashMap<>();

      final AnonymousClassEnumerator anonymousClassEnumerator = new AnonymousClassEnumerator();

      for (JavacDef def : defs) {
        if (def instanceof JavacDef.JavacClassDef) {
          JavacRef.JavacClass sym = (JavacRef.JavacClass)def.getDefinedElement();
          if (sym.isPackageInfo()) {
            continue; // skip special 'package-info' class
          }
          final CompilerRef.CompilerClassHierarchyElementDef aClass;
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
            CompilerRef.JavaCompilerClassRef superClassRef = writer.asClassUsage(superClass);

            backwardHierarchyMap.computeIfAbsent(superClassRef, k -> new SmartList<>()).add(aClass);
          }
        }
        else if (def instanceof JavacDef.JavacFunExprDef) {
          final CompilerRef.JavaCompilerClassRef functionalType = writer.asClassUsage(def.getDefinedElement());
          int id = funExprId++;
          CompilerRef.JavaCompilerFunExprDef result = new CompilerRef.JavaCompilerFunExprDef(id);
          definitions.put(result, null);

          backwardHierarchyMap.computeIfAbsent(functionalType, __ -> new SmartList<>()).add(result);
        }
        else if (def instanceof JavacDef.JavacMemberDef) {
          final CompilerRef
            ref = writer.enumerateNames(def.getDefinedElement(), name -> anonymousClassEnumerator.getCompilerRefIfAnonymous(name));
          final CompilerRef.JavaCompilerClassRef returnType = writer.asClassUsage(((JavacDef.JavacMemberDef)def).getReturnType());
          if (ref != null) {
            final SignatureData data = new SignatureData(returnType.getName(), ((JavacDef.JavacMemberDef)def).getIteratorKind(), ((JavacDef.JavacMemberDef)def).isStatic());
            signatureData.computeIfAbsent(data, element -> new SmartList<>()).add(ref);
          }
        }
      }

      Map<CompilerRef, Integer> convertedRefs = new HashMap<>();
      for (Map.Entry<? extends JavacRef, Integer> entry : refs) {
        JavacRef ref = entry.getKey();
        int count = entry.getValue();
        CompilerRef compilerRef = writer.enumerateNames(ref, name -> anonymousClassEnumerator.getCompilerRefIfAnonymous(name));
        if (compilerRef != null) {
          convertedRefs.merge(compilerRef, count, (oldValue, value) -> oldValue + value);
        }
      }

      for (JavacTypeCast cast : casts) {
        CompilerRef enumeratedCastType = writer.enumerateNames(cast.getCastType(), name -> null);
        if (enumeratedCastType == null) continue;
        CompilerRef enumeratedOperandType = writer.enumerateNames(cast.getOperandType(), name -> null);
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

  private static final class AnonymousClassEnumerator {
    private Map<String, CompilerRef.CompilerClassHierarchyElementDef> myAnonymousName2Id = null;

    private CompilerRef.JavaCompilerAnonymousClassRef addAnonymous(String internalName,
                                                                   CompilerRef.JavaCompilerClassRef base) {
      if (myAnonymousName2Id == null) {
        myAnonymousName2Id = new HashMap<>();
      }
      final int anonymousIdx = myAnonymousName2Id.size();
      myAnonymousName2Id.put(internalName, base);
      return new CompilerRef.JavaCompilerAnonymousClassRef(anonymousIdx);
    }

    private Integer getCompilerRefIfAnonymous(String className) {
      if (myAnonymousName2Id == null) return null;
      final CompilerRef.CompilerClassHierarchyElementDef ref = myAnonymousName2Id.get(className);
      return ref == null ? null : ref.getName();
    }
  }
}

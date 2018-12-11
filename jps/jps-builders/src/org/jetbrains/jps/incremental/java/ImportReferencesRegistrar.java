// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.java;

import gnu.trove.TObjectIntHashMap;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import java.util.*;

public class ImportReferencesRegistrar implements JavacFileReferencesRegistrar {
  @Override
  public void initialize() {
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean onlyImports() {
    return true;
  }

  @Override
  public void registerFile(CompileContext context,
                           String filePath,
                           TObjectIntHashMap<JavacRef> refs,
                           Collection<JavacDef> defs,
                           Collection<JavacTypeCast> casts,
                           Collection<JavacRef> implicitToString) {
    if (refs.isEmpty() || defs.isEmpty()) {
      return;
    }
    final Set<String> classImports = new HashSet<>();
    final Set<String> fieldImports = new HashSet<>();
    final Set<String> methodImports = new HashSet<>();
    for (Object key : refs.keys()) {
      final JavacRef ref = (JavacRef)key;
      if (ref instanceof JavacRef.JavacClass) {
        classImports.add(ref.getName());
        final JavacRef.ImportProperties props = ref.getImportProperties();
        if (props != null && props.isStatic() && props.isOnDemand()) {
          final String memberImport = ref.getName() + ".*";
          fieldImports.add(memberImport);
          methodImports.add(memberImport);
        }
      }
      else if (ref instanceof JavacRef.JavacField) {
        fieldImports.add(ref.getOwnerName() + "." + ref.getName());
      }
      else if (ref instanceof JavacRef.JavacMethod) {
        methodImports.add(ref.getOwnerName() + "." + ref.getName());
      }
    }
    final List<String> definedClasses = new ArrayList<>();
    for (JavacDef def : defs) {
      if (def instanceof JavacDef.JavacClassDef) {
        final JavacRef element = def.getDefinedElement();
        if (element instanceof JavacRef.JavacClass) {
          definedClasses.add(element.getName());
        }
      }
    }
    if (!definedClasses.isEmpty() && (!classImports.isEmpty() || !fieldImports.isEmpty() || !methodImports.isEmpty())) {
      final Callbacks.Backend deps = JavaBuilderUtil.getDependenciesRegistrar(context);
      for (String aClass : definedClasses) {
        deps.registerImports(aClass, classImports, fieldImports, methodImports);
      }
    }
  }
}
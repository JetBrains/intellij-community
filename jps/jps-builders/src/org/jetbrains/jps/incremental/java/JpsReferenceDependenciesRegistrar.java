// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.java;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.javac.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacTypeCast;

import javax.lang.model.element.Modifier;
import java.util.*;

/**
 * register dependencies that are not discoverable from bytecode:
 * - references caused  by import statements
 * - references to fields initialized with compile-time constant values. Such values can be inlined into referencing bytecode
 */
public final class JpsReferenceDependenciesRegistrar implements JavacFileReferencesRegistrar {
  @Override
  public void initialize() {
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void registerFile(CompileContext context,
                           String filePath,
                           Iterable<Object2IntMap.Entry<? extends JavacRef>> refs,
                           Collection<? extends JavacDef> defs,
                           Collection<? extends JavacTypeCast> casts,
                           Collection<? extends JavacRef> implicitToString) {
    final Set<String> definedClasses = new HashSet<>();
    for (JavacDef def : defs) {
      if (def instanceof JavacDef.JavacClassDef) {
        final JavacRef element = def.getDefinedElement();
        if (element instanceof JavacRef.JavacClass) {
          definedClasses.add(element.getName());
        }
      }
    }
    if (definedClasses.isEmpty()) {
      return;
    }

    Iterator<Object2IntMap.Entry<? extends JavacRef>> iterator = refs.iterator();
    if (iterator.hasNext()) {
      final Set<String> classImports = new HashSet<>();
      final Set<String> staticImports = new HashSet<>();
      final Map<String, List<Callbacks.ConstantRef>> cRefs = new HashMap<>();

      while (iterator.hasNext()) {
        JavacRef ref = iterator.next().getKey();
        final JavacRef.ImportProperties importProps = ref.getImportProperties();
        if (importProps != null) { // the reference comes from import list
          if (ref instanceof JavacRef.JavacClass) {
            classImports.add(ref.getName());
            if (importProps.isStatic() && importProps.isOnDemand()) {
              staticImports.add(ref.getName() + ".*");
            }
          }
          else {
            if (ref instanceof JavacRef.JavacField || ref instanceof JavacRef.JavacMethod) {
              staticImports.add(ref.getOwnerName() + "." + ref.getName());
            }
          }
        }
        else if (ref instanceof JavacRef.JavacField && ref.getModifiers().contains(Modifier.FINAL)) {
          final JavacRef.JavacField fieldRef = (JavacRef.JavacField)ref;
          final String descriptor = fieldRef.getDescriptor();
          if (descriptor != null && definedClasses.contains(fieldRef.getContainingClass()) && !definedClasses.contains(fieldRef.getOwnerName())) {
            List<Callbacks.ConstantRef> refsList = cRefs.get(fieldRef.getContainingClass());
            if (refsList == null) {
              refsList = new ArrayList<>();
              cRefs.put(fieldRef.getContainingClass(), refsList);
            }
            refsList.add(Callbacks.createConstantReference(fieldRef.getOwnerName(), fieldRef.getName(), descriptor));
          }
        }
      }

      if (!classImports.isEmpty() || !staticImports.isEmpty()) {
        final Callbacks.Backend reg = JavaBuilderUtil.getDependenciesRegistrar(context);
        for (String aClass : definedClasses) {
          reg.registerImports(aClass, classImports, staticImports);
        }
      }
      if (!cRefs.isEmpty()) {
        final Callbacks.Backend reg = JavaBuilderUtil.getDependenciesRegistrar(context);
        for (String aClass : definedClasses) {
          final List<Callbacks.ConstantRef> classCRefs = cRefs.get(aClass);
          reg.registerConstantReferences(aClass, classCRefs != null? classCRefs : Collections.emptyList());
        }
      }
    }
  }
}
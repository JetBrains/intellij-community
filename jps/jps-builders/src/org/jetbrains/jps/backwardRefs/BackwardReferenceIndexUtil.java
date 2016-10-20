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

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BackwardReferenceIndexUtil {
  static final Symbol[] EMPTY_SYMBOL_ARRAY = new Symbol[0];
  static final Tree.Kind LAMBDA_EXPRESSION;
  static final Tree.Kind MEMBER_REFERENCE;

  static {
    Tree.Kind lambdaExpression = null;
    Tree.Kind memberReference = null;
    try {
      lambdaExpression = Tree.Kind.valueOf("LAMBDA_EXPRESSION");
      memberReference = Tree.Kind.valueOf("MEMBER_REFERENCE");
    }
    catch (IllegalArgumentException ignored) {
    }
    LAMBDA_EXPRESSION = lambdaExpression;
    MEMBER_REFERENCE = memberReference;
  }

  static void registerFile(String filePath,
                           Set<JavacRefSymbol> refs,
                           List<JavacRefSymbol> defs,
                           BackwardReferenceIndexWriter writer) {
    final int fileId = writer.enumeratePath(filePath);
    int funExprId = 0;

    final List<LightRef> definitions = new ArrayList<LightRef>(defs.size());
    for (JavacRefSymbol def : defs) {
      Tree.Kind kind = def.getPlaceKind();
      if (kind == Tree.Kind.CLASS) {
        Symbol.ClassSymbol sym = (Symbol.ClassSymbol)def.getSymbol();
        Type superclass = sym.getSuperclass();
        List<Type> interfaces = sym.getInterfaces();

        final Symbol[] supers;
        if (superclass != Type.noType) {
          supers = new Symbol[interfaces.size() + 1];
          supers[interfaces.size()] = superclass.asElement();
        } else {
          supers = interfaces.isEmpty() ? EMPTY_SYMBOL_ARRAY : new Symbol[interfaces.size()];
        }

        int i = 0;
        for (Type anInterface : interfaces) {
          supers[i++] = anInterface.asElement();
        }

        final LightRef.JavaLightClassRef aClass = writer.asClassUsage(sym);
        definitions.add(aClass);

        if (supers.length != 0) {

          final LightRef.JavaLightClassRef[] superIds = new LightRef.JavaLightClassRef[supers.length];
          for (int j = 0; j < supers.length; j++) {
            superIds[j] = writer.asClassUsage(supers[j]);
          }

          writer.writeHierarchy(fileId, aClass, superIds);
        }
      }
      else if (kind == LAMBDA_EXPRESSION || kind == MEMBER_REFERENCE) {
        final LightRef.JavaLightClassRef functionalType = writer.asClassUsage(def.getSymbol());
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

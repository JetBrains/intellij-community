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
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import javax.tools.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class BackwardReferenceRegistrar implements JavacFileReferencesRegistrar {
  private static final Symbol[] EMPTY_SYMBOL_ARRAY = new Symbol[0];

  private BackwardReferenceIndexWriter myWriter;

  @Override
  public boolean initialize() {
    myWriter = BackwardReferenceIndexWriter.getInstance();
    return myWriter != null;
  }

  @Override
  public boolean onlyImports() {
    return false;
  }

  @Override
  public void registerFile(JavaFileObject file, Set<JavacRefSymbol> refs, Collection<JavacRefSymbol> defs) {
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
        myWriter.writeHierarchy(sym, supers);
      }
    }

    myWriter.writeReferences(file, refs);
  }
}

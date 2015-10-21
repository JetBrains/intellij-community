/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.stubs.Import;
import com.intellij.psi.stubsHierarchy.stubs.UnitInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StubResolver {
  private final Symbols mySymbols;
  private final NameEnvironment myNameEnvironment;

  public StubResolver(Symbols symbols) {
    this.mySymbols = symbols;
    this.myNameEnvironment = symbols.myNameEnvironment;
  }

  // resolve class `sym` extends/implements `baseId`
  public Set<Symbol> resolveBase(Symbol.ClassSymbol sym, int[] baseId) {
    Set<Symbol> prev = null;
    Set<Symbol> result = null;
    for (int i = 0; i < baseId.length; i++) {
      int name = baseId[i];
      int k = (i == baseId.length - 1) ? IndexTree.CLASS : IndexTree.CLASS | IndexTree.PACKAGE;
      if (i == 0) {
        result = findIdent(sym.myOwner, sym.myUnitInfo, name, k);
      } else {
        Set<Symbol> acc = new HashSet<Symbol>();
        for (Symbol symbol : prev) {
          selectSym(symbol, name, k, acc);;
        }
        result = acc;
      }
      prev = result;

    }
    return result;
  }

  @NotNull
  private Set<Symbol> findIdent(Symbol startScope, UnitInfo info, int name, int kind) {
    Set<Symbol> result = new HashSet<Symbol>();
    if ((kind & IndexTree.CLASS) != 0) {
      findType(startScope, name, result);
      findGlobalType(info, name, result);
    }

    if ((kind & IndexTree.PACKAGE) != 0) {
      Symbol.PackageSymbol pkg = mySymbols.getPackage(myNameEnvironment.qualifiedName(null, name, false));
      if (pkg != null)
        result.add(pkg);
    }
    return result;
  }

  private void findType(Symbol startScope, int name, Set<Symbol> symbols) {
    // looking up
    for (Symbol s = startScope; s != null; s = s.myOwner)
      findMemberType(s, name, symbols, new HashSet<Symbol>());
    // type from current package
    findIdentInPackage(startScope.pkg(), name, IndexTree.CLASS, symbols);
  }

  // resolving `receiver.name`
  private void selectSym(Symbol receiver, int name, int kind, Set<Symbol> symbols) {
    if (receiver.isPackage())
      findIdentInPackage((Symbol.PackageSymbol)receiver, name, kind, symbols);
    else
      findMemberType(receiver, name, symbols, new HashSet<Symbol>());
  }

  private void findIdentInPackage(Symbol.PackageSymbol pck, int name, int kind, Set<Symbol> symbols) {
    QualifiedName fullname = mySymbols.myNameEnvironment.qualifiedName(pck.myQualifiedName, name, false);
    if (fullname == null) {
      return;
    }
    if ((kind & IndexTree.PACKAGE) != 0) {
      Symbol.PackageSymbol pkg = mySymbols.getPackage(fullname);
      if (pkg != null)
        symbols.add(pkg);
    }
    if ((kind & IndexTree.CLASS) != 0) {
      Collections.addAll(symbols, loadClass(fullname));
    }
  }

  private static void findMemberType(Symbol s, int name, Set<Symbol> symbols, Set<Symbol> processed) {
    if (!processed.add(s)) {
      return;
    }
    findImmediateMemberType(s, name, symbols);
    if (s.isClass())
      findInheritedMemberType((Symbol.ClassSymbol)s, name, symbols, processed);
  }

  private static void findImmediateMemberType(Symbol s, int name, Set<Symbol> symbols) {
    Symbol.ClassSymbol[] members = s.members();
    int index = getIndex(name, members);
    if (index < 0) return;

    // elem
    Symbol.ClassSymbol member = members[index];
    symbols.add(member);
    // on the left
    int i = index - 1;
    while (i >= 0 && members[i].myShortName == name) {
      member = members[i];
      symbols.add(member);
      i--;
    }
    // on the right
    i = index + 1;
    while (i < members.length && members[i].myShortName == name) {
      member = members[i];
      symbols.add(member);
      i++;
    }
  }

  private static void findInheritedMemberType(Symbol.ClassSymbol c, int name, Set<Symbol> symbols, Set<Symbol> processed) {
    for (Symbol.ClassSymbol st : c.getSuperClasses())
      findMemberType(st, name, symbols, processed);
  }

  private Symbol.ClassSymbol[] loadClass(@NotNull QualifiedName fqn) {
    return mySymbols.loadClass(fqn);
  }

  public Symbol.ClassSymbol[] findGlobalType(@NotNull QualifiedName name) {
    return loadClass(name);
  }

  private void findGlobalType(UnitInfo info, int name, Set<Symbol> symbols) {
    for (long anImport : Translator.getDefaultImports(info.getType(), myNameEnvironment))
      handleImport(anImport, name, symbols);
    for (long anImport : info.getImports())
      handleImport(anImport, name, symbols);
  }

  public void handleImport(long tree, int name, Set<Symbol> symbols) {
    QualifiedName fullname = Import.getFullName(tree, myNameEnvironment);
    if (Import.isOnDemand(tree)) {
      if (Import.isStatic(tree)) {
        for (Symbol.ClassSymbol p : findGlobalType(fullname))
          importStaticAll(p, name, symbols);
      }
      else {
        importAll(fullname, name, symbols);
      }
    }
    else {
      QualifiedName prefix = myNameEnvironment.prefix(fullname);
      if (prefix.isEmpty()) {
        return;
      }
      int shortName = myNameEnvironment.shortName(fullname);
      int alias = Import.getAlias(tree);
      boolean shouldImport = ((alias & name) == name) || shortName == name;
      if (!shouldImport)
        return;
      if (Import.isStatic(tree)) {
          for (Symbol.ClassSymbol s : findGlobalType(prefix))
            importNamedStatic(s, shortName, symbols);
      }
      else {
        Collections.addAll(symbols, findGlobalType(fullname));
      }
    }
  }

  // handling of `import prefix.*`
  private void importAll(@NotNull final QualifiedName prefix, int suffix, final Set<Symbol> symbols) {

      QualifiedName fullname = myNameEnvironment.qualifiedName(prefix, suffix, false);
      // existing only
      if (fullname != null) {
        Symbol.ClassSymbol[] ss = findGlobalType(fullname);
        Collections.addAll(symbols, ss);
      }
  }

  // handling of `import static tsym.*`
  private static void importStaticAll(final Symbol.ClassSymbol tsym, final int name, final Set<Symbol> symbols) {
    new Object() {
      Set<Symbol> processed = new HashSet<Symbol>();
      void importFrom(Symbol.ClassSymbol cs) {
        if (cs == null || !processed.add(cs))
          return;
        for (Symbol.ClassSymbol c : cs.getSuperClasses())
          importFrom(c);
        importMember(cs.members(), name, symbols, true);
      }
    }.importFrom(tsym);
  }

  // handling of import static `tsym.name` as
  private static void importNamedStatic(final Symbol.ClassSymbol tsym, final int name, final Set<Symbol> symbols) {
    new Object() {
      Set<Symbol> processed = new HashSet<Symbol>();
      void importFrom(Symbol.ClassSymbol cs) {
        if (cs == null || !processed.add(cs))
          return;
        for (Symbol.ClassSymbol c : cs.getSuperClasses())
          importFrom(c);
        importMember(cs.members(), name, symbols, true);
      }
    }.importFrom(tsym);
  }

  private static void importMember(Symbol.ClassSymbol[] members, int name, Set<Symbol> symbols, boolean isStatic) {
    int index = getIndex(name, members);
    if (index < 0) return;

    // elem
    Symbol.ClassSymbol member = members[index];
    if (!isStatic || member.isStatic()) {
      symbols.add(member);
    }
    // on the left
    int i = index - 1;
    while (i >= 0 && members[i].myShortName == name) {
      member = members[i];
      if (!isStatic || member.isStatic()) {
        symbols.add(member);
      }
      i--;
    }
    // on the right
    i = index + 1;
    while (i < members.length && members[i].myShortName == name) {
      member = members[i];
      if (!isStatic || member.isStatic()) {
        symbols.add(member);
      }
      i++;
    }
  }

  private static int getIndex(int key, Symbol.ClassSymbol[] a) {
    int lo = 0;
    int hi = a.length - 1;
    while (lo <= hi) {
      int mid = lo + (hi - lo) / 2;
      if      (key < a[mid].myShortName) hi = mid - 1;
      else if (key > a[mid].myShortName) lo = mid + 1;
      else return mid;
    }
    return -1;
  }
}

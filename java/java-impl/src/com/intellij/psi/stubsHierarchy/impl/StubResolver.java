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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StubResolver {
  private final Symbols mySymbols;
  private final NameEnvironment myNameEnvironment;
  private final StubHierarchyConnector myConnector;

  public StubResolver(Symbols symbols, StubHierarchyConnector connector) {
    this.mySymbols = symbols;
    this.myNameEnvironment = symbols.myNameEnvironment;
    myConnector = connector;
  }

  // resolve class `sym` extends/implements `baseId`
  Set<Symbol> resolveBase(Symbol.ClassSymbol sym, int[] baseId) throws IncompleteHierarchyException {
    Set<Symbol> result = findIdent(sym.myOwner, sym.myUnitInfo, baseId[0], baseId.length > 1);
    for (int i = 1; i < baseId.length; i++) {
      Set<Symbol> prev = result;
      int k = (i == baseId.length - 1) ? IndexTree.CLASS : IndexTree.CLASS | IndexTree.PACKAGE;

      result = new HashSet<>();
      for (Symbol symbol : prev) {
        selectSym(symbol, baseId[i], k, result);
      }
    }
    if (result.isEmpty()) {
      throw IncompleteHierarchyException.INSTANCE;
    }
    return result;
  }

  @NotNull
  private Set<Symbol> findIdent(Symbol startScope, UnitInfo info, int name, boolean processPackages) throws IncompleteHierarchyException {
    Set<Symbol> result = new HashSet<Symbol>();
    findType(startScope, name, result);
    findGlobalType(info, name, result);

    if (processPackages) {
      Symbol.PackageSymbol pkg = mySymbols.getPackage(myNameEnvironment.qualifiedName(null, name, false));
      if (pkg != null)
        result.add(pkg);
    }
    return result;
  }

  private void findType(Symbol startScope, int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    // looking up
    for (Symbol s = startScope; s != null; s = s.myOwner)
      findMemberType(s, name, symbols);
    // type from current package
    findIdentInPackage(startScope.pkg(), name, IndexTree.CLASS, symbols);
  }

  // resolving `receiver.name`
  private void selectSym(Symbol receiver, int name, int kind, Set<Symbol> symbols) throws IncompleteHierarchyException {
    if (receiver.isPackage())
      findIdentInPackage((Symbol.PackageSymbol)receiver, name, kind, symbols);
    else
      findMemberType(receiver, name, symbols);
  }

  private void findIdentInPackage(Symbol.PackageSymbol pck, int name, int kind, Set<Symbol> symbols) {
    QualifiedName fullname = mySymbols.myNameEnvironment.qualifiedName(pck.myQualifiedName, name, false);
    if (fullname == null) {
      return;
    }
    if (BitUtil.isSet(kind, IndexTree.PACKAGE)) {
      Symbol.PackageSymbol pkg = mySymbols.getPackage(fullname);
      if (pkg != null)
        symbols.add(pkg);
    }
    if (BitUtil.isSet(kind, IndexTree.CLASS)) {
      Collections.addAll(symbols, loadClass(fullname));
    }
  }

  private void findMemberType(Symbol s, int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    if (s.isClass()) {
      processInheritedMembers((Symbol.ClassSymbol)s, name, false, symbols, null);
    } else {
      processMembers(s.getMembers(), name, symbols, false);
    }
  }

  private void processInheritedMembers(Symbol.ClassSymbol s,
                                       int name,
                                       boolean requireStatic,
                                       Set<Symbol> symbols,
                                       @Nullable Set<Symbol> processed) throws IncompleteHierarchyException {
    processMembers(s.getMembers(), name, symbols, requireStatic);

    @CompactArray(Symbol.ClassSymbol.class) Object supers = s.getSuperClasses(myConnector);
    if (supers == null) return;

    if (processed == null) processed = new HashSet<>();
    if (!processed.add(s)) return;

    if (supers instanceof Symbol.ClassSymbol) {
      processInheritedMembers((Symbol.ClassSymbol)supers, name, requireStatic, symbols, processed);
    } else if (supers instanceof Symbol.ClassSymbol[]) {
      for (Symbol.ClassSymbol st : (Symbol.ClassSymbol[])supers) {
        processInheritedMembers(st, name, requireStatic, symbols, processed);
      }
    }
  }

  private Symbol.ClassSymbol[] loadClass(@NotNull QualifiedName fqn) {
    return mySymbols.loadClass(fqn);
  }

  public Symbol.ClassSymbol[] findGlobalType(@NotNull QualifiedName name) {
    return loadClass(name);
  }

  private void findGlobalType(UnitInfo info, int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    for (long anImport : Translator.getDefaultImports(info.getType(), myNameEnvironment))
      handleImport(anImport, name, symbols);
    for (long anImport : info.getImports())
      handleImport(anImport, name, symbols);
  }

  public void handleImport(long tree, int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    QualifiedName fullname = Imports.getFullName(tree, myNameEnvironment);
    if (Imports.isOnDemand(tree)) {
      if (Imports.isStatic(tree)) {
        for (Symbol.ClassSymbol p : findGlobalType(fullname))
          importNamedStatic(p, name, symbols);
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
      int alias = Imports.getAlias(tree);
      boolean shouldImport = ((alias & name) == name) || shortName == name;
      if (!shouldImport)
        return;
      if (Imports.isStatic(tree)) {
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

  // handling of import static `tsym.name` as
  private void importNamedStatic(final Symbol.ClassSymbol tsym, final int name, final Set<Symbol> symbols) throws IncompleteHierarchyException {
    processInheritedMembers(tsym, name, true, symbols, null);
  }

  private static void processMembers(Symbol.ClassSymbol[] members, int name, Set<Symbol> symbols, boolean requireStatic) {
    int index = getIndex(name, members);
    if (index < 0) return;

    // elem
    Symbol.ClassSymbol member = members[index];
    if (!requireStatic || member.isStatic()) {
      symbols.add(member);
    }
    // on the left
    int i = index - 1;
    while (i >= 0 && members[i].myShortName == name) {
      member = members[i];
      if (!requireStatic || member.isStatic()) {
        symbols.add(member);
      }
      i--;
    }
    // on the right
    i = index + 1;
    while (i < members.length && members[i].myShortName == name) {
      member = members[i];
      if (!requireStatic || member.isStatic()) {
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

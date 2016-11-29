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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StubResolver {
  private static final Import[] DEFAULT_JAVA_IMPORTS = {importPackage("java.lang")};
  private static final Import[] DEFAULT_GROOVY_IMPORTS = {
    importPackage("java.lang"),
    importPackage("java.util"),
    importPackage("java.io"),
    importPackage("java.net"),
    importPackage("groovy.lang"),
    importPackage("groovy.util"),
    new Import(NameEnvironment.fromString("java.math"), NameEnvironment.hashIdentifier("BigInteger"), false),
    new Import(NameEnvironment.fromString("java.math"), NameEnvironment.hashIdentifier("BigDecimal"), false),
  };
  private final Symbols mySymbols;
  private final StubHierarchyConnector myConnector;

  public StubResolver(Symbols symbols, StubHierarchyConnector connector) {
    this.mySymbols = symbols;
    myConnector = connector;
  }

  private static Import importPackage(String qname) {
    return new Import(NameEnvironment.fromString(qname), 0, false);
  }

  private static Import[] getDefaultImports(byte type) {
    if (type == IndexTree.JAVA) return DEFAULT_JAVA_IMPORTS;
    if (type == IndexTree.GROOVY) return DEFAULT_GROOVY_IMPORTS;
    return Imports.EMPTY_ARRAY;
  }

  // resolve class `sym` extends/implements `baseId`
  Set<Symbol> resolveBase(Symbol.ClassSymbol sym, @ShortName int[] qname) throws IncompleteHierarchyException {
    Set<Symbol> result = resolveUnqualified(sym, qname[0], qname.length > 1);
    for (int i = 1; i < qname.length; i++) {
      result = processQualifier(result, qname[i], i != qname.length - 1);
    }
    if (result.isEmpty()) {
      throw IncompleteHierarchyException.INSTANCE;
    }
    return result;
  }

  Set<Symbol> resolveUnqualified(Symbol.ClassSymbol sym, @ShortName final int shortName, boolean processPackages) throws IncompleteHierarchyException {
    Set<Symbol> symbols = findIdent(sym.myOwner, sym.myUnitInfo, shortName, processPackages);
    if (symbols.isEmpty()) {
      throw IncompleteHierarchyException.INSTANCE;
    }
    return symbols;
  }

  private Set<Symbol> processQualifier(Set<Symbol> contextResults, @ShortName final int shortName, final boolean processPackages) throws IncompleteHierarchyException {
    Set<Symbol> result = new HashSet<>();
    for (Symbol symbol : contextResults) {
      selectSym(symbol, shortName, processPackages, result);
    }
    return result;
  }

  @NotNull
  private Set<Symbol> findIdent(Symbol startScope, UnitInfo info, @ShortName int name, boolean processPackages) throws IncompleteHierarchyException {
    Set<Symbol> result = new HashSet<>();
    findType(startScope, name, result);
    findGlobalType(info, name, result);

    if (processPackages) {
      ContainerUtil.addIfNotNull(result, mySymbols.getPackage(name));
    }
    return result;
  }

  private void findType(Symbol startScope, int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    // looking up
    for (Symbol s = startScope; s != null; s = s.myOwner)
      findMemberType(s, name, symbols);
    // type from current package
    findIdentInPackage(startScope.pkg(), name, false, symbols);
  }

  // resolving `receiver.name`
  private void selectSym(Symbol receiver, @ShortName int name, boolean processPackages, Set<Symbol> symbols) throws IncompleteHierarchyException {
    if (receiver.isPackage())
      findIdentInPackage((Symbol.PackageSymbol)receiver, name, processPackages, symbols);
    else
      findMemberType(receiver, name, symbols);
  }

  private void findIdentInPackage(Symbol.PackageSymbol pck, @ShortName int name, boolean processPackages, Set<Symbol> symbols) {
    @QNameHash int fullname = NameEnvironment.qualifiedName(pck.myQualifiedName, name);
    if (processPackages) {
      ContainerUtil.addIfNotNull(symbols, mySymbols.getPackage(fullname));
    }
    Collections.addAll(symbols, findGlobalType(fullname));
  }

  private void findMemberType(Symbol s, @ShortName int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    if (s.isClass()) {
      processInheritedMembers((Symbol.ClassSymbol)s, name, symbols, null);
    } else {
      processMembers(s.getMembers(), name, symbols);
    }
  }

  private void processInheritedMembers(Symbol.ClassSymbol s,
                                       @ShortName int name,
                                       Set<Symbol> symbols,
                                       @Nullable Set<Symbol> processed) throws IncompleteHierarchyException {
    processMembers(s.getMembers(), name, symbols);

    @CompactArray(Symbol.ClassSymbol.class) Object supers = s.getSuperClasses(myConnector);
    if (supers == null) return;

    if (processed == null) processed = new HashSet<>();
    if (!processed.add(s)) return;

    if (supers instanceof Symbol.ClassSymbol) {
      processInheritedMembers((Symbol.ClassSymbol)supers, name, symbols, processed);
    } else if (supers instanceof Symbol.ClassSymbol[]) {
      for (Symbol.ClassSymbol st : (Symbol.ClassSymbol[])supers) {
        processInheritedMembers(st, name, symbols, processed);
      }
    }
  }

  public Symbol.ClassSymbol[] findGlobalType(@QNameHash int nameId) {
    return mySymbols.getClassSymbols(nameId);
  }

  private void findGlobalType(UnitInfo info, @ShortName int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    for (Import anImport : getDefaultImports(info.type))
      handleImport(anImport, name, symbols);
    for (Import anImport : info.imports)
      handleImport(anImport, name, symbols);
  }

  public void handleImport(Import anImport, @ShortName int name, Set<Symbol> symbols) throws IncompleteHierarchyException {
    if (anImport.isOnDemand()) {
      if (anImport.isStatic) {
        for (Symbol.ClassSymbol p : findGlobalType(anImport.qualifier))
          importNamedStatic(p, name, symbols);
      }
      else {
        importAll(anImport.qualifier, name, symbols);
      }
    }
    else {
      @ShortName int importedName = anImport.getAlias() != 0 ? anImport.getAlias() : anImport.importedName;
      if (name != importedName) return;

      if (anImport.isStatic) {
          for (Symbol.ClassSymbol s : findGlobalType(anImport.qualifier))
            importNamedStatic(s, anImport.importedName, symbols);
      }
      else {
        Collections.addAll(symbols, findGlobalType(NameEnvironment.qualifiedName(anImport.qualifier, anImport.importedName)));
      }
    }
  }

  // handling of `import prefix.*`
  private void importAll(@QNameHash int prefix, @ShortName int suffix, final Set<Symbol> symbols) {
    Collections.addAll(symbols, findGlobalType(NameEnvironment.qualifiedName(prefix, suffix)));
  }

  // handling of import static `tsym.name` as
  private void importNamedStatic(final Symbol.ClassSymbol tsym, @ShortName final int name, final Set<Symbol> symbols) throws IncompleteHierarchyException {
    processInheritedMembers(tsym, name, symbols, null);
  }

  private static void processMembers(Symbol.ClassSymbol[] members, @ShortName int name, Set<Symbol> symbols) {
    int index = getIndex(name, members);
    if (index < 0) return;

    // elem
    symbols.add(members[index]);
    // on the left
    int i = index - 1;
    while (i >= 0 && members[i].myShortName == name) {
      symbols.add(members[i]);
      i--;
    }
    // on the right
    i = index + 1;
    while (i < members.length && members[i].myShortName == name) {
      symbols.add(members[i]);
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

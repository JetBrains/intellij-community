package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;
import com.intellij.psi.stubsHierarchy.stubs.UnitInfo;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Symbols {
  public final PackageSymbol myRootPackage;
  protected final NameEnvironment myNameEnvironment;

  // id -> ClassSymbol
  protected ClassSymbol[] myClassSymbols = new ClassSymbol[0x8000];
  // fullName -> PackageSymbol
  public final TIntObjectHashMap<PackageSymbol> myPackages = new TIntObjectHashMap<PackageSymbol>();
  // nameId -> ClassSymbols (used by global resolve)
  private Object[] myClassSymbolsByNameId = new Object[0x8000];

  protected Symbols(NameEnvironment nameEnvironment) {
    this.myNameEnvironment = nameEnvironment;
    myRootPackage = new PackageSymbol(null, nameEnvironment.empty, NamesEnumerator.NO_NAME);
    myPackages.put(nameEnvironment.empty.myId, myRootPackage);
  }

  // last used id
  protected int id;

  public PackageSymbol enterPackage(QualifiedName qualifiedName) {
    PackageSymbol p = myPackages.get(qualifiedName.myId);
    if (p == null) {
      PackageSymbol owner = enterPackage(myNameEnvironment.prefix(qualifiedName));
      int shortName = myNameEnvironment.shortName(qualifiedName);
      p = new PackageSymbol(owner, qualifiedName, shortName);
      myPackages.put(qualifiedName.myId, p);
    }
    return p;
  }

  @Nullable
  public PackageSymbol getPackage(QualifiedName qualifiedName) {
    if (qualifiedName == null)
      return null;
    return myPackages.get(qualifiedName.myId);
  }

  @NotNull
  public ClassSymbol[] loadClass(@NotNull QualifiedName qualifiedName) {
    int i = qualifiedName.myId;
    if (i >= myClassSymbolsByNameId.length) {
      return ClassSymbol.EMPTY_ARRAY;
    }
    return getClassSymbols(i);
  }

  private ClassSymbol[] getClassSymbols(int id) {
    Object cs = myClassSymbolsByNameId[id];
    if (cs == null) {
      return ClassSymbol.EMPTY_ARRAY;
    }
    if (cs instanceof ClassSymbol) {
      return new ClassSymbol[]{(ClassSymbol)cs};
    }
    return (ClassSymbol[])cs;
  }

  public ClassSymbol enterClass(ClassAnchor classAnchor, int flags, int shortName, Symbol owner, UnitInfo info, QualifiedName[] supers, HierarchyConnector connector) {
    QualifiedName qualifiedName = myNameEnvironment.qualifiedName(owner, shortName);
    SmartClassAnchor smartClassAnchor = SmartClassAnchor.create(id++, classAnchor);
    ClassSymbol c = new ClassSymbol(smartClassAnchor, flags, owner, qualifiedName, shortName, info, supers, connector);
    putClass(c);
    return c;
  }

  private void putClass(ClassSymbol classSymbol) {
    putClassById(classSymbol);
    putClassByName(classSymbol);
  }

  private void putClassById(ClassSymbol classSymbol) {
    ensureByIdCapacity(classSymbol.myClassAnchor.myId);
    myClassSymbols[classSymbol.myClassAnchor.myId] = classSymbol;
  }

  private void putClassByName(ClassSymbol classSymbol) {
    QualifiedName name = classSymbol.myQualifiedName;
    // anonymous class
    if (name == null)
      return;
    int nameId = name.myId;
    ensureByNameCapacity(nameId);
    Object cs = myClassSymbolsByNameId[nameId];
    if (cs == null) {
      myClassSymbolsByNameId[nameId] = classSymbol;
    } else {
      if (cs instanceof ClassSymbol) {
        ClassSymbol c = (ClassSymbol)cs;
        myClassSymbolsByNameId[nameId] = new ClassSymbol[]{c, classSymbol};
      } else {
        ClassSymbol[] css = (ClassSymbol[])cs;
        ClassSymbol[] newCss = new ClassSymbol[css.length + 1];
        System.arraycopy(css, 0, newCss, 0, css.length);
        newCss[css.length] = classSymbol;
        myClassSymbolsByNameId[nameId] = newCss;
      }
    }
  }

  private void ensureByNameCapacity(int maxIndex) {
    if (maxIndex >= myClassSymbolsByNameId.length) {
      int newLength = calculateNewLength(myClassSymbolsByNameId.length, maxIndex);
      Object[] result = new Object[newLength];
      System.arraycopy(myClassSymbolsByNameId, 0, result, 0, myClassSymbolsByNameId.length);
      myClassSymbolsByNameId = result;
    }
  }

  private void ensureByIdCapacity(int maxIndex) {
    if (maxIndex >= myClassSymbols.length) {
      int newLength = calculateNewLength(myClassSymbols.length, maxIndex);
      ClassSymbol[] result = new ClassSymbol[newLength];
      System.arraycopy(myClassSymbols, 0, result, 0, myClassSymbols.length);
      myClassSymbols = result;
    }
  }

  private static int calculateNewLength(int currentLength, int maxIndex) {
    while (currentLength < maxIndex + 1) currentLength *= 2;
    return currentLength;
  }

  public SingleClassHierarchy createHierarchy() {
    SingleClassHierarchy table = new SingleClassHierarchy(myClassSymbols, id);
    table.connectSubTypes(myClassSymbols, id);
    myClassSymbols = null;
    return table;
  }
}

package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;
import com.intellij.psi.stubsHierarchy.stubs.UnitInfo;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Symbols {
  public final PackageSymbol myRootPackage;
  protected final NameEnvironment myNameEnvironment = new NameEnvironment();

  private List<ClassSymbol> myClassSymbols = new ArrayList<>(0x8000);
  // fullName -> PackageSymbol
  private final TIntObjectHashMap<PackageSymbol> myPackages = new TIntObjectHashMap<>();
  // nameId -> ClassSymbols (used by global resolve)
  private Object[] myClassSymbolsByNameId = new Object[0x8000];

  protected Symbols() {
    myRootPackage = new PackageSymbol(null, myNameEnvironment.empty, NamesEnumerator.NO_NAME);
    myPackages.put(myNameEnvironment.empty.myId, myRootPackage);
  }

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

  public ClassSymbol enterClass(ClassAnchor classAnchor, int flags, int shortName, Symbol owner, UnitInfo info, QualifiedName[] supers, StubHierarchyConnector connector) {
    QualifiedName qualifiedName = myNameEnvironment.qualifiedName(owner, shortName);
    StubClassAnchor stubClassAnchor = new StubClassAnchor(myClassSymbols.size(), classAnchor);
    ClassSymbol c = new ClassSymbol(stubClassAnchor, flags, owner, qualifiedName, shortName, info, supers, connector);
    myClassSymbols.add(c);
    putClassByName(c);
    return c;
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

  private static int calculateNewLength(int currentLength, int maxIndex) {
    while (currentLength < maxIndex + 1) currentLength *= 2;
    return currentLength;
  }

  SingleClassHierarchy createHierarchy() {
    ClassSymbol[] array = myClassSymbols.toArray(ClassSymbol.EMPTY_ARRAY);
    myClassSymbols = null;
    return new SingleClassHierarchy(array);
  }
}

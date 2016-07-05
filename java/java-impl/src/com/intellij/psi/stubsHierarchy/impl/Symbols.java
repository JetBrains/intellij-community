package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Symbols {
  public final PackageSymbol myRootPackage;
  protected final NameEnvironment myNameEnvironment = new NameEnvironment();
  private final AnchorRepository myClassAnchors = new AnchorRepository();

  private List<ClassSymbol> myClassSymbols = new ArrayList<>(0x8000);
  // fullName -> PackageSymbol
  private final TIntObjectHashMap<PackageSymbol> myPackages = new TIntObjectHashMap<>();
  // nameId -> ClassSymbols (used by global resolve)
  private Object[] myClassSymbolsByNameId = new Object[0x8000];

  protected Symbols() {
    myRootPackage = new PackageSymbol(null, 0, NameEnvironment.NO_NAME);
    myPackages.put(0, myRootPackage);
  }

  PackageSymbol enterPackage(@QNameId int qualifiedName, @ShortName int shortName, PackageSymbol owner) {
    PackageSymbol p = myPackages.get(qualifiedName);
    if (p == null) {
      p = new PackageSymbol(owner, qualifiedName, shortName);
      myPackages.put(qualifiedName, p);
    }
    return p;
  }

  @Nullable
  PackageSymbol getPackage(@QNameId int qualifiedName) {
    return myPackages.get(qualifiedName);
  }

  @NotNull
  ClassSymbol[] loadClass(@QNameId int name) {
    return name >= myClassSymbolsByNameId.length ? ClassSymbol.EMPTY_ARRAY : getClassSymbols(name);
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

  ClassSymbol enterClass(int fileId,
                         int stubId,
                         int flags,
                         int shortName,
                         Symbol owner,
                         UnitInfo info,
                         @CompactArray(QualifiedName.class) Object supers,
                         @QNameId int qualifiedName) {
    int anchorId = myClassAnchors.registerClass(fileId, stubId);
    ClassSymbol c = new ClassSymbol(anchorId, flags, owner, shortName, info, supers);
    myClassSymbols.add(c);
    if (qualifiedName >= 0) {
      putClassByName(c, qualifiedName);
    }
    return c;
  }

  private void putClassByName(ClassSymbol classSymbol, int nameId) {
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
    return new SingleClassHierarchy(array, myClassAnchors);
  }
}

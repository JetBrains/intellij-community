package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.psi.stubsHierarchy.impl.Symbol.ClassSymbol;
import com.intellij.psi.stubsHierarchy.impl.Symbol.PackageSymbol;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Symbols {
  public final PackageSymbol myRootPackage = new PackageSymbol(null, 0, NameEnvironment.NO_NAME);
  private final AnchorRepository myClassAnchors = new AnchorRepository();

  private List<ClassSymbol> myClassSymbols = new ArrayList<>(0x8000);
  // fullName -> PackageSymbol
  private final TIntObjectHashMap<PackageSymbol> myPackages = new TIntObjectHashMap<>();
  // nameId -> ClassSymbols (used by global resolve)
  private final TIntObjectHashMap<Object> myClassSymbolsByNameId = new TIntObjectHashMap<>();

  protected Symbols() {
    myPackages.put(0, myRootPackage);
  }

  PackageSymbol enterPackage(@QNameHash int qualifiedName, @ShortName int shortName, PackageSymbol owner) {
    PackageSymbol p = myPackages.get(qualifiedName);
    if (p == null) {
      p = new PackageSymbol(owner, qualifiedName, shortName);
      myPackages.put(qualifiedName, p);
    }
    return p;
  }

  @Nullable
  PackageSymbol getPackage(@QNameHash int qualifiedName) {
    return myPackages.get(qualifiedName);
  }

  @NotNull
  ClassSymbol[] getClassSymbols(@QNameHash int name) {
    Object cs = myClassSymbolsByNameId.get(name);
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
                         @QNameHash int qualifiedName) {
    int anchorId = myClassAnchors.registerClass(fileId, stubId);
    ClassSymbol c = new ClassSymbol(anchorId, flags, owner, shortName, info, supers);
    myClassSymbols.add(c);
    if (qualifiedName != 0) {
      putClassByName(c, qualifiedName);
    }
    return c;
  }

  private void putClassByName(ClassSymbol classSymbol, @QNameHash int nameId) {
    Object cs = myClassSymbolsByNameId.get(nameId);
    if (cs == null) {
      myClassSymbolsByNameId.put(nameId, classSymbol);
    } else if (cs instanceof ClassSymbol) {
      myClassSymbolsByNameId.put(nameId, new ClassSymbol[]{(ClassSymbol)cs, classSymbol});
    } else {
      myClassSymbolsByNameId.put(nameId, ArrayUtil.append((ClassSymbol[])cs, classSymbol));
    }
  }

  SingleClassHierarchy createHierarchy() {
    ClassSymbol[] array = myClassSymbols.toArray(ClassSymbol.EMPTY_ARRAY);
    myClassSymbols = null;
    return new SingleClassHierarchy(array, myClassAnchors);
  }
}

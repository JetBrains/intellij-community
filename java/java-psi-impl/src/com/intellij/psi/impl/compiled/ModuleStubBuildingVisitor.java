// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.psi.impl.java.stubs.impl.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.ModuleResolutionAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.impl.java.stubs.JavaStubElementTypes.*;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.containers.ContainerUtil.map2Array;

public class ModuleStubBuildingVisitor extends ClassVisitor {
  private static final Function<String, String> NAME_MAPPER = name1 -> name1.replace('/', '.');
  private static final Attribute[] ATTRIBUTES = new Attribute[]{new ModuleResolutionAttribute()};

  private final ModuleStubBuilder myBuilder;

  public ModuleStubBuildingVisitor(PsiJavaFileStub parent) {
    super(Opcodes.API_VERSION);
    myBuilder = new ModuleStubBuilder(parent);
  }

  public PsiJavaModuleStub getResult() {
    return myBuilder.build();
  }

  @Override
  public ModuleVisitor visitModule(String name, int access, String version) {
    myBuilder.name(name).flags(moduleFlags(access));
    return new ModuleVisitor(Opcodes.API_VERSION) {
      @Override
      public void visitRequire(String module, int access, String version) {
        if (!isGenerated(access)) {
          myBuilder.addRequires(module, requiresFlags(access));
        }
      }

      @Override
      public void visitExport(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          myBuilder.addPackageAccessibility(EXPORTS_STATEMENT, packageName, modules);
        }
      }

      @Override
      public void visitOpen(String packageName, int access, String... modules) {
        if (!isGenerated(access)) {
          myBuilder.addPackageAccessibility(OPENS_STATEMENT, packageName, modules);
        }
      }

      @Override
      public void visitUse(String service) {
        myBuilder.addUses(service);
      }

      @Override
      public void visitProvide(String service, String... providers) {
        myBuilder.addProvide(service, providers);
      }
    };
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    return StubBuildingVisitor.getAnnotationTextCollector(desc, text -> myBuilder.addAnnotation(text));
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    if (attribute instanceof ModuleResolutionAttribute) {
      myBuilder.resolution(((ModuleResolutionAttribute)attribute).resolution);
    }
    super.visitAttribute(attribute);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
  }

  private static boolean isGenerated(int access) {
    return isSet(access, Opcodes.ACC_SYNTHETIC) || isSet(access, Opcodes.ACC_MANDATED);
  }

  private static int moduleFlags(int access) {
    return isSet(access, Opcodes.ACC_OPEN) ? ModifierFlags.OPEN_MASK : 0;
  }

  private static int requiresFlags(int access) {
    int flags = 0;
    if (isSet(access, Opcodes.ACC_TRANSITIVE)) flags |= ModifierFlags.TRANSITIVE_MASK;
    if (isSet(access, Opcodes.ACC_STATIC_PHASE)) flags |= ModifierFlags.STATIC_MASK;
    return flags;
  }

  public Attribute[] attributes() {
    return ATTRIBUTES;
  }

  private static class ModuleStubBuilder {
    private final PsiJavaFileStub myParent;

    private volatile PsiJavaModuleStub myResult;

    private volatile String myName;
    private volatile int myFlags;
    private volatile int myResolution;

    private final List<Requires> myRequires = new ArrayList<>();
    private final List<PackageAccessibility> myPackageAccessibilities = new ArrayList<>();
    private final List<Provide> myProvides = new ArrayList<>();
    private final List<String> myServices = new ArrayList<>();
    private final List<String> myAnnotations = new ArrayList<>();

    ModuleStubBuilder(PsiJavaFileStub parent) { myParent = parent; }

    ModuleStubBuilder name(String name) {
      myName = name;
      return this;
    }

    void flags(int flags) {
      myFlags = flags;
    }

    void addRequires(String module, int flags) {
      myRequires.add(new Requires(module, flags));
    }

    void addPackageAccessibility(IStubElementType type, String packageName, String[] modules) {
      myPackageAccessibilities.add(new PackageAccessibility(type, packageName, modules));
    }

    void resolution(int resolution) {
      myResolution = resolution;
    }

    PsiJavaModuleStub build() {
      if (myResult == null) {
        synchronized (this) {
          if (myResult == null) {
            PsiJavaModuleStub result = new PsiJavaModuleStubImpl(myParent, myName, myResolution);
            PsiModifierListStubImpl modifiers = new PsiModifierListStubImpl(result, myFlags);
            for (Requires require : myRequires) {
              PsiRequiresStatementStubImpl req = new PsiRequiresStatementStubImpl(result, require.name);
              new PsiModifierListStubImpl(req, require.flags);
            }
            for (PackageAccessibility accessibility : myPackageAccessibilities) {
              new PsiPackageAccessibilityStatementStubImpl(result, accessibility.myType, NAME_MAPPER.fun(accessibility.myPackageName),
                                                           accessibility.myModules);
            }
            for (String service : myServices) {
              new PsiUsesStatementStubImpl(result, NAME_MAPPER.fun(service));
            }
            for (Provide provide : myProvides) {
              PsiProvidesStatementStubImpl statementStub = new PsiProvidesStatementStubImpl(result, NAME_MAPPER.fun(provide.myService));
              String[] names = map2Array(provide.myProviders, String.class, NAME_MAPPER);
              new PsiClassReferenceListStubImpl(PROVIDES_WITH_LIST, statementStub,
                                                names.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : names);
            }
            for (String annotation : myAnnotations) {
              new PsiAnnotationStubImpl(modifiers, annotation);
            }

            myResult = result;
          }
        }
      }
      return myResult;
    }

    void addUses(String service) {
      myServices.add(service);
    }

    void addProvide(String service, String[] providers) {
      myProvides.add(new Provide(service, providers));
    }

    void addAnnotation(String annotationName) {
      myAnnotations.add(annotationName);
    }

    private static class Requires {
      final String name;
      final int flags;

      Requires(String name, int flags) {
        this.name = name;
        this.flags = flags;
      }
    }

    private static class Provide {
      private final String myService;
      private final List<String> myProviders;

      Provide(String service, String[] providers) {
        myService = service;
        myProviders = providers == null ? null : Arrays.asList(providers);
      }
    }

    private static class PackageAccessibility {
      final IStubElementType myType;
      final String myPackageName;
      final List<String> myModules;

      PackageAccessibility(IStubElementType type, String packageName, String[] modules) {
        myType = type;
        myPackageName = packageName;
        myModules = modules == null ? null : Arrays.asList(modules);
      }
    }
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class JavaModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements JavaModuleFixtureBuilder<T> {
  private final List<Lib> myLibraries = new ArrayList<>();
  private final List<MavenLib> myMavenLibraries = new ArrayList<>();

  private String myJdk;
  private MockJdkLevel myMockJdkLevel = MockJdkLevel.jdk14;
  private LanguageLevel myLanguageLevel;

  public JavaModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(StdModuleTypes.JAVA, fixtureBuilder);
  }

  public JavaModuleFixtureBuilderImpl(final ModuleType moduleType, final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(moduleType, fixtureBuilder);
  }

  @NotNull
  @Override
  public JavaModuleFixtureBuilder setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    return this;
  }

  @NotNull
  @Override
  public JavaModuleFixtureBuilder addLibrary(String libraryName, String @NotNull ... classPath) {
    for (String path : classPath) {
      if (!new File(path).exists()) {
        System.out.println(path + " does not exist");
      }
    }
    Map<OrderRootType, String[]> map = Collections.singletonMap(OrderRootType.CLASSES, classPath);
    myLibraries.add(new Lib(libraryName, map));
    return this;
  }

  @NotNull
  @Override
  public JavaModuleFixtureBuilder addLibrary(@NonNls final String libraryName, @NotNull final Map<OrderRootType, String[]> roots) {
    myLibraries.add(new Lib(libraryName, roots));
    return this;
  }

  @Override
  public @NotNull JavaModuleFixtureBuilder addMavenLibrary(@NotNull MavenLib lib) {
    myMavenLibraries.add(lib);
    return this;
  }

  @NotNull
  @Override
  public JavaModuleFixtureBuilder addLibraryJars(String libraryName, @NotNull String basePath, String @NotNull ... jars) {
    if (!basePath.endsWith("/")) {
      basePath += "/";
    }
    String[] classPath = ArrayUtil.newStringArray(jars.length);
    for (int i = 0; i < jars.length; i++) {
      classPath[i] = basePath + jars[i];
    }
    return addLibrary(libraryName, classPath);
  }

  @NotNull
  @Override
  public JavaModuleFixtureBuilder addJdk(@NotNull String jdkPath) {
    myJdk = jdkPath;
    return this;
  }

  @Override
  public void setMockJdkLevel(@NotNull final MockJdkLevel level) {
    myMockJdkLevel = level;
  }

  @Override
  protected void initModule(final Module module) {
    super.initModule(module);

    ModuleRootModificationUtil.updateModel(module, model -> {
      LibraryTable libraryTable = model.getModuleLibraryTable();
      for (Lib lib : myLibraries) {
        Library library = libraryTable.createLibrary(lib.getName());
        Library.ModifiableModel libraryModel = library.getModifiableModel();
        boolean success = false;
        try {
          for (OrderRootType rootType : OrderRootType.getAllTypes()) {
            for (String root : lib.getRoots(rootType)) {
              VirtualFile vRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
              if (vRoot != null && OrderRootType.CLASSES.equals(rootType) && !vRoot.isDirectory()) {
                VirtualFile jar = JarFileSystem.getInstance().refreshAndFindFileByPath(root + "!/");
                if (jar != null) {
                  vRoot = jar;
                }
              }
              if (vRoot != null) {
                libraryModel.addRoot(vRoot, rootType);
              }
            }
          }
          success = true;
        }
        finally {
          if (!success) {
            Disposer.dispose(libraryModel);
          }
        }

        libraryModel.commit();
      }

      for (MavenLib mavenLib : myMavenLibraries) {
        MavenDependencyUtil.addFromMaven(model, mavenLib.getCoordinates(), mavenLib.isIncludeTransitiveDependencies(),
                                         mavenLib.getDependencyScope());
      }

      final Sdk jdk;
      if (myJdk != null) {
        VfsRootAccess.allowRootAccess(module, myJdk);
        jdk = JavaSdk.getInstance().createJdk(module.getName() + "_jdk", myJdk, false);
        ((ProjectJdkImpl)jdk).setVersionString(StringUtil.notNullize(IdeaTestUtil.getMockJdkVersion(myJdk), "java 1.5"));
      }
      else {
        jdk = IdeaTestUtil.getMockJdk17();
      }

      registerJdk(jdk, module.getProject());
      model.setSdk(jdk);

      if (myLanguageLevel != null) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLanguageLevel);
      }
      else if (myMockJdkLevel == MockJdkLevel.jdk15) {
        model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_5);
      }
    });

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        libraryCreated(library, module);
      }
    }
  }

  private static void registerJdk(Sdk jdk, Project project) {
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();

    // Remove all JDK named as jdk.getName()
    // There may be several of them as findJdk just searches a list
    while (true) {
      Sdk byName = jdkTable.findJdk(jdk.getName());
      if (byName == null) break;

      jdkTable.removeJdk(byName);
    }

    WriteAction.runAndWait(()-> jdkTable.addJdk(jdk, project));
  }

  @Override
  protected void setupRootModel(ModifiableRootModel rootModel) {
    if (myOutputPath != null) {
      final File pathFile = new File(myOutputPath);
      if (!pathFile.mkdirs()) {
        assert pathFile.exists() : "unable to create: " + myOutputPath;
      }
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myOutputPath);
      assert virtualFile != null : "cannot find output path: " + myOutputPath;
      rootModel.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(virtualFile);
      rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
      rootModel.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
    }
    if (myTestOutputPath != null) {
      assert new File(myTestOutputPath).mkdirs() : myTestOutputPath;
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(myTestOutputPath);
      assert virtualFile != null : "cannot find test output path: " + myTestOutputPath;
      rootModel.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(virtualFile);
      rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
      rootModel.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
    }
  }

  protected void libraryCreated(Library library, Module module) {}

  private static class Lib {
    private final String myName;
    private final Map<OrderRootType, String []> myRoots;

    Lib(final String name, final Map<OrderRootType, String[]> roots) {
      myName = name;
      myRoots = roots;
    }

    public String getName() {
      return myName;
    }

    public String [] getRoots(OrderRootType rootType) {
      final String[] roots = myRoots.get(rootType);
      return roots != null ? roots : ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
  }
}

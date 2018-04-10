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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.MockJdkWrapper;
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
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public abstract class JavaModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements JavaModuleFixtureBuilder<T> {
  private final List<Lib> myLibraries = new ArrayList<>();
  private String myJdk;
  private MockJdkLevel myMockJdkLevel = MockJdkLevel.jdk14;
  private LanguageLevel myLanguageLevel = null;

  public JavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(StdModuleTypes.JAVA, fixtureBuilder);
  }

  public JavaModuleFixtureBuilderImpl(final ModuleType moduleType, final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(moduleType, fixtureBuilder);
  }

  @Override
  public JavaModuleFixtureBuilder setLanguageLevel(final LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibrary(String libraryName, String... classPath) {
    final HashMap<OrderRootType, String[]> map = new HashMap<>();
    for (String path : classPath) {
      if (!new File(path).exists()) {
        System.out.println(path + " does not exist");
      }
    }
    map.put(OrderRootType.CLASSES, classPath);
    myLibraries.add(new Lib(libraryName, map));
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibrary(@NonNls final String libraryName, final Map<OrderRootType, String[]> roots) {
    myLibraries.add(new Lib(libraryName, roots));
    return this;
  }

  @Override
  public JavaModuleFixtureBuilder addLibraryJars(String libraryName, String basePath, String... jars) {
    if (!basePath.endsWith("/")) {
      basePath += "/";
    }
    String[] classPath = ArrayUtil.newStringArray(jars.length);
    for (int i = 0; i < jars.length; i++) {
      classPath[i] = basePath + jars[i];
    }
    return addLibrary(libraryName, classPath);
  }

  @Override
  public JavaModuleFixtureBuilder addJdk(String jdkPath) {
    myJdk = jdkPath;
    return this;
  }

  @Override
  public void setMockJdkLevel(final MockJdkLevel level) {
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

      final Sdk jdk;
      if (myJdk != null) {
        VfsRootAccess.allowRootAccess(module, myJdk);
        jdk = JavaSdk.getInstance().createJdk(module.getName() + "_jdk", myJdk, false);
        ((ProjectJdkImpl)jdk).setVersionString(StringUtil.notNullize(IdeaTestUtil.getMockJdkVersion(myJdk), "java 1.5"));
      }
      else {
        jdk = IdeaTestUtil.getMockJdk17();
      }
      model.setSdk(new MockJdkWrapper(CompilerConfigurationImpl.getTestsExternalCompilerHome(), jdk));

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

    public Lib(final String name, final Map<OrderRootType, String[]> roots) {
      myName = name;
      myRoots = roots;
    }

    public String getName() {
      return myName;
    }

    public String [] getRoots(OrderRootType rootType) {
      final String[] roots = myRoots.get(rootType);
      return roots != null ? roots : ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}

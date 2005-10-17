/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.application.ApplicationManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 9, 2004
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DummySourceGeneratingCompiler implements SourceGeneratingCompiler{
  public static final String MODULE_NAME = "generated";
  private final Project myProject;

  public DummySourceGeneratingCompiler(Project project) {
    myProject = project;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    final Module module = findMyModule();
    return new GenerationItem[] {
      new MyGenerationItem("aaa/p1.properties", module),
      new MyGenerationItem("bbb/p2.properties", module),
      new MyGenerationItem("bbb/ccc/p3.properties", module)
    };
  }

  private Module findMyModule() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      public Module compute() {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        for (int idx = 0; idx < modules.length; idx++) {
          Module module = modules[idx];
          if (MODULE_NAME.equals(module.getName())) {
            return module;
          }
        }
        return null;
      }
    });
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] items, final VirtualFile outputRootDirectory) {
    final String rootPath = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return outputRootDirectory.getPath();
      }
    });
    final List<GenerationItem> success = new ArrayList<GenerationItem>();
    for (int idx = 0; idx < items.length; idx++) {
      try {
        GenerationItem item = items[idx];
        File file = new File(rootPath + File.separator + item.getPath());
        file.getParentFile().mkdirs();
        file.createNewFile();
        success.add(item);
      }
      catch (IOException e) {
      }
    }
    return success.toArray(new GenerationItem[success.size()]);
  }

  public String getDescription() {
    return "Dummy Source Generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return findMyModule() != null;
  }

  public ValidityState createValidityState(DataInputStream is) throws IOException {
    return null;
  }

  private static class MyGenerationItem implements GenerationItem {
    private final String myRelPath;
    private final Module myModule;

    public MyGenerationItem(String relPath, Module module) {
      myRelPath = relPath;
      myModule = module;
    }

    public String getPath() {
      return myRelPath;
    }

    public ValidityState getValidityState() {
      return null;
    }

    public Module getModule() {
      return myModule;
    }
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.SourceGeneratingCompiler;
import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    final Module module = findMyModule();
    return new GenerationItem[] {
      new MyGenerationItem("aaa/p1.properties", module, false),
      new MyGenerationItem("bbb/p2.properties", module, false),
      new MyGenerationItem("bbb/ccc/p3.properties", module, false),
      new MyGenerationItem("aaa/p1.properties", module, true),
      new MyGenerationItem("bbb/p2-t.properties", module, true),
      new MyGenerationItem("bbb/ccc/p3.properties", module, true)
    };
  }

  private Module findMyModule() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      public Module compute() {
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        for (Module module : modules) {
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
    for (GenerationItem item1 : items) {
      GenerationItem item = item1;
      File file = new File(rootPath + File.separator + item.getPath());
      FileUtil.createIfDoesntExist(file);
      success.add(item);
    }
    return success.toArray(new GenerationItem[success.size()]);
  }

  @NotNull
  public String getDescription() {
    return "Dummy Source Generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return findMyModule() != null;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return null;
  }

  private static class MyGenerationItem implements GenerationItem {
    private final String myRelPath;
    private final Module myModule;
    private final boolean myIsTestSource;

    public MyGenerationItem(String relPath, Module module, final boolean isTestSource) {
      myRelPath = relPath;
      myModule = module;
      myIsTestSource = isTestSource;
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

    public boolean isTestSource() {
      return myIsTestSource;
    }
  }
}

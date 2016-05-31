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
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.IndexableSetContributor;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Irina.Chernushina on 4/14/2016.
 */
public class JsonSchemaResourcesRootsProvider extends IndexableSetContributor {
  public static final NotNullLazyValue<Set<VirtualFile>> ourFiles = new AtomicNotNullLazyValue<Set<VirtualFile>>() {
    @NotNull
    @Override
    protected Set<VirtualFile> compute() {
      final Set<VirtualFile> set = new HashSet<>();
      final JsonSchemaProviderFactory[] extensions = Extensions.getExtensions(JsonSchemaProviderFactory.EP_NAME);
      for (JsonSchemaProviderFactory extension : extensions) {
        final List<JsonSchemaFileProvider> providers = extension.getProviders(null);
        for (JsonSchemaFileProvider provider : providers) {
          if (!SchemaType.userSchema.equals(provider.getSchemaType())) {
            set.add(provider.getSchemaFile());
          }
        }
      }
      return set;
    }
  };

  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return ourFiles.getValue();
  }

  @NotNull
  public static GlobalSearchScope enlarge(@NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    // we need to create the scope manually since files are outside project root (embedded)
    return scope.union(new GlobalSearchScope() {
      @Override
      public boolean contains(@NotNull final VirtualFile file) {
        return ourFiles.getValue().contains(file);
      }

      @Override
      public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
        return scope.compare(file1, file2);
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull final Module aModule) {
        return scope.isSearchInModuleContent(aModule);
      }

      @Override
      public boolean isSearchInLibraries() {
        return scope.isSearchInLibraries();
      }

      @Override
      public boolean isSearchOutsideRootModel() {
        return true;
      }
    });
  }
}

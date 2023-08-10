// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isAbsoluteUrl;
import static com.jetbrains.jsonSchema.remote.JsonFileResolver.isHttpPath;

public class JsonSchemaUserDefinedProviderFactory implements JsonSchemaProviderFactory, DumbAware {
  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    final Map<String, UserDefinedJsonSchemaConfiguration> map = configuration.getStateMap();
    final List<JsonSchemaFileProvider> providers = ContainerUtil.map(map.values(), schema -> createProvider(project, schema));

    return providers;
  }

  @NotNull
  public MyProvider createProvider(@NotNull Project project,
                                   UserDefinedJsonSchemaConfiguration schema) {
    String relPath = schema.getRelativePathToSchema();
    return new MyProvider(project, schema.getSchemaVersion(), schema.getName(),
                          isAbsoluteUrl(relPath) || new File(relPath).isAbsolute()
                            ? relPath
                            : new File(project.getBasePath(),
                          relPath).getAbsolutePath(),
                          schema.getCalculatedPatterns());
  }

  static class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    @NotNull private final Project myProject;
    @NotNull private final JsonSchemaVersion myVersion;
    @NotNull private final @Nls String myName;
    @NotNull private final String myFile;
    private VirtualFile myVirtualFile;
    @NotNull private final List<? extends PairProcessor<Project, VirtualFile>> myPatterns;

    MyProvider(@NotNull final Project project,
                      @NotNull final JsonSchemaVersion version,
                      @NotNull final @Nls String name,
                      @NotNull final String file,
                      @NotNull final List<? extends PairProcessor<Project, VirtualFile>> patterns) {
      myProject = project;
      myVersion = version;
      myName = name;
      myFile = file;
      myPatterns = patterns;
    }

    @Override
    public JsonSchemaVersion getSchemaVersion() {
      return myVersion;
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
      if (myVirtualFile != null && myVirtualFile.isValid()) return myVirtualFile;
      String path = myFile;

      if (isAbsoluteUrl(path)) {
        myVirtualFile = JsonFileResolver.urlToFile(path);
      }
      else {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        myVirtualFile = lfs.findFileByPath(myFile);
        if (myVirtualFile == null) {
          myVirtualFile = lfs.refreshAndFindFileByPath(myFile);
        }
      }
      return myVirtualFile;
    }

    @NotNull
    @Override
    public SchemaType getSchemaType() {
      return SchemaType.userSchema;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      //noinspection SimplifiableIfStatement
      if (myPatterns.isEmpty() || file.isDirectory() || !file.isValid()) return false;
      return myPatterns.stream().anyMatch(processor -> processor.process(myProject, file));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyProvider provider = (MyProvider)o;

      if (!myName.equals(provider.myName)) return false;
      return FileUtil.pathsEqual(myFile, provider.myFile);
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + FileUtil.pathHashCode(myFile);
      return result;
    }

    @Nullable
    @Override
    public String getRemoteSource() {
      return isHttpPath(myFile) ? myFile : null;
    }
  }
}

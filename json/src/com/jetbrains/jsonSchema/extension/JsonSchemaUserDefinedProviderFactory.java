// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  @Override
  public @NotNull List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    final Map<String, UserDefinedJsonSchemaConfiguration> map = configuration.getStateMap();
    final List<JsonSchemaFileProvider> providers = ContainerUtil.map(map.values(), schema -> createProvider(project, schema));

    return providers;
  }

  public @NotNull MyProvider createProvider(@NotNull Project project,
                                            UserDefinedJsonSchemaConfiguration schema) {
    String relPath = schema.getRelativePathToSchema();
    return new MyProvider(project, schema.getSchemaVersion(), schema.getName(),
                          isAbsoluteUrl(relPath) || new File(relPath).isAbsolute()
                            ? relPath
                            : new File(project.getBasePath(),
                          relPath).getAbsolutePath(),
                          schema.getCalculatedPatterns());
  }

  static final class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    private final @NotNull Project myProject;
    private final @NotNull JsonSchemaVersion myVersion;
    private final @NotNull @Nls String myName;
    private final @NotNull String myFile;
    private VirtualFile myVirtualFile;
    private final @NotNull List<? extends PairProcessor<Project, VirtualFile>> myPatterns;

    MyProvider(final @NotNull Project project,
               final @NotNull JsonSchemaVersion version,
               final @NotNull @Nls String name,
               final @NotNull String file,
               final @NotNull List<? extends PairProcessor<Project, VirtualFile>> patterns) {
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

    @Override
    public @Nullable VirtualFile getSchemaFile() {
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

    @Override
    public @NotNull SchemaType getSchemaType() {
      return SchemaType.userSchema;
    }

    @Override
    public @NotNull String getName() {
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

    @Override
    public @Nullable String getRemoteSource() {
      return isHttpPath(myFile) ? myFile : null;
    }
  }
}

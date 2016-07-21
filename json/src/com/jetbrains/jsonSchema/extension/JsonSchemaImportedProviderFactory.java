package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurationBase;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Irina.Chernushina on 2/13/2016.
 */
public class JsonSchemaImportedProviderFactory implements JsonSchemaProviderFactory {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderFactory");

  @Override
  public List<JsonSchemaFileProvider> getProviders(@Nullable Project project) {
    final List<JsonSchemaFileProvider> list = new ArrayList<>();

    if (project != null) {
      processConfiguration(project, JsonSchemaMappingsProjectConfiguration.getInstance(project), list);
    }

    return list.isEmpty() ? Collections.emptyList() : list;
  }

  private static void processConfiguration(Project project, @NotNull final JsonSchemaMappingsConfigurationBase configuration,
                                           @NotNull final List<JsonSchemaFileProvider> list) {
    final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> map = configuration.getStateMap();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : map.values()) {
      list.add(new MyProvider(project, info.getName(), configuration.convertToAbsoluteFile(info.getRelativePathToSchema()),
                              info.getCalculatedPatterns(project)));
    }
  }

  private static class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    @Nullable private final Project myProject;
    @NotNull private final String myName;
    @NotNull private final File myFile;
    private VirtualFile myVirtualFile;
    @NotNull private final List<Processor<VirtualFile>> myPatterns;

    public MyProvider(@Nullable final Project project,
                      @NotNull String name,
                      @NotNull File file,
                      @NotNull List<Processor<VirtualFile>> patterns) {
      myProject = project;
      myName = name;
      myFile = file;
      myPatterns = patterns;
    }

    @Override
    public VirtualFile getSchemaFile() {
      if (myVirtualFile != null && myVirtualFile.isValid()) return myVirtualFile;
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      myVirtualFile = lfs.findFileByIoFile(myFile);
      if (myVirtualFile == null) {
        myVirtualFile = lfs.refreshAndFindFileByIoFile(myFile);
      }
      return myVirtualFile;
    }

    @Override
    public SchemaType getSchemaType() {
      return SchemaType.userSchema;
    }

    @Override
    public int getOrder() {
      return Orders.USER;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull VirtualFile file) {
      if (myPatterns.isEmpty() || file.isDirectory() || !file.isValid() ||
          myProject != null && JsonSchemaMappingsProjectConfiguration.getInstance(myProject).isRegisteredSchemaFile(file)) return false;
      for (Processor<VirtualFile> pattern : myPatterns) {
        if (pattern.process(file)) return true;
      }
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyProvider provider = (MyProvider)o;

      if (!myName.equals(provider.myName)) return false;
      if (!myFile.equals(provider.myFile)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + myFile.hashCode();
      return result;
    }
  }
}

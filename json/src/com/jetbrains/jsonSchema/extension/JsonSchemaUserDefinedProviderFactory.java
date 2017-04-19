package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.jetbrains.jsonSchema.JsonSchemaFileType;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 2/13/2016.
 */
public class JsonSchemaUserDefinedProviderFactory implements JsonSchemaProviderFactory {
  @Override
  public List<JsonSchemaFileProvider> getProviders(@Nullable Project project) {
    if (project == null) return Collections.emptyList();

    final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    final Map<String, UserDefinedJsonSchemaConfiguration> map = configuration.getStateMap();
    final List<JsonSchemaFileProvider> providers = map.values().stream()
      .map(schema -> new MyProvider(schema.getName(), new File(project.getBasePath(), schema.getRelativePathToSchema()),
                                    schema.getCalculatedPatterns(project))).collect(Collectors.toList());

    return providers.isEmpty() ? Collections.emptyList() : providers;
  }

  static class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    @NotNull private final String myName;
    @NotNull private final File myFile;
    private VirtualFile myVirtualFile;
    @NotNull private final List<Processor<VirtualFile>> myPatterns;

    public MyProvider(@NotNull String name,
                      @NotNull File file,
                      @NotNull List<Processor<VirtualFile>> patterns) {
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
          JsonSchemaFileType.INSTANCE.equals(file.getFileType())) return false;
      return myPatterns.stream().anyMatch(processor -> processor.process(file));
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

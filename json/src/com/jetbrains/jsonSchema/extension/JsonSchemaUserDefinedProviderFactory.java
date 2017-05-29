package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 2/13/2016.
 */
public class JsonSchemaUserDefinedProviderFactory implements JsonSchemaProviderFactory {
  @NotNull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
    final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    final Map<String, UserDefinedJsonSchemaConfiguration> map = configuration.getStateMap();
    final List<JsonSchemaFileProvider> providers = map.values().stream()
      .map(schema -> new MyProvider(schema.getName(), new File(project.getBasePath(), schema.getRelativePathToSchema()),
                                    schema.getCalculatedPatterns())).collect(Collectors.toList());

    return providers.isEmpty() ? Collections.emptyList() : providers;
  }

  static class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    @NotNull private final String myName;
    @NotNull private final File myFile;
    private VirtualFile myVirtualFile;
    @NotNull private final List<PairProcessor<Project, VirtualFile>> myPatterns;

    public MyProvider(@NotNull String name,
                      @NotNull File file,
                      @NotNull List<PairProcessor<Project, VirtualFile>> patterns) {
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

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull VirtualFile file) {
      if (myPatterns.isEmpty() || file.isDirectory() || !file.isValid() || getSchemaFile() == null ||
          JsonSchemaService.Impl.get(project).isSchemaFile(file)) return false;
      return myPatterns.stream().anyMatch(processor -> processor.process(project, file));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyProvider provider = (MyProvider)o;

      if (!myName.equals(provider.myName)) return false;
      if (!FileUtil.filesEqual(myFile, provider.myFile)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + FileUtil.fileHashCode(myFile);
      return result;
    }
  }
}

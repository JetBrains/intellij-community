package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurationBase;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * @author Irina.Chernushina on 2/13/2016.
 */
public class JsonSchemaImportedProviderFactory implements JsonSchemaProviderFactory {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderFactory");

  @Override
  public JsonSchemaFileProvider[] getProviders(@Nullable Project project) {
    final List<JsonSchemaFileProvider> list = new ArrayList<JsonSchemaFileProvider>();

    //processConfiguration(project, JsonSchemaMappingsApplicationConfiguration.getInstance(), list);
    if (project != null) {
      processConfiguration(project, JsonSchemaMappingsProjectConfiguration.getInstance(project), list);
    }

    return list.toArray(new JsonSchemaFileProvider[list.size()]);
  }

  private static void processConfiguration(@Nullable Project project, @NotNull final JsonSchemaMappingsConfigurationBase configuration,
                                           @NotNull final List<JsonSchemaFileProvider> list) {
    final Map<String, JsonSchemaMappingsConfigurationBase.SchemaInfo> map = configuration.getStateMap();
    for (JsonSchemaMappingsConfigurationBase.SchemaInfo info : map.values()) {
      if (!info.getPatterns().isEmpty()) {
        list.add(new MyProvider(project, info.getName(), configuration.convertToAbsoluteFile(info.getRelativePathToSchema()), info.getPatterns()));
      }
    }
  }

  private static class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
    @NotNull private final String myName;
    @NotNull private final File myFile;
    @NotNull private final List<Processor<VirtualFile>> myPatterns;

    public MyProvider(@Nullable final Project project,
                      @NotNull String name,
                      @NotNull File file,
                      @NotNull List<JsonSchemaMappingsConfigurationBase.Item> patterns) {
      myName = name;
      myFile = file;
      myPatterns = new ArrayList<Processor<VirtualFile>>();
      for (final JsonSchemaMappingsConfigurationBase.Item pattern : patterns) {
        if (pattern.isPattern()) {
          myPatterns.add(new Processor<VirtualFile>() {
            private Matcher matcher = PatternUtil.fromMask(pattern.getPath()).matcher("");

            @Override
            public boolean process(VirtualFile file) {
              matcher.reset(file.getName());
              return matcher.matches();
            }
          });
        } else {
          if (project == null || project.getBasePath() == null) {
            continue;
          }

          String path = pattern.getPath().replace('\\', '/');
          final String[] parts = path.split("/");
          final VirtualFile relativeFile = VfsUtil.findRelativeFile(project.getBaseDir(), parts);
          if (relativeFile == null) continue;

          if (pattern.isDirectory()) {
            myPatterns.add(new Processor<VirtualFile>() {
              @Override
              public boolean process(VirtualFile file) {
                return VfsUtil.isAncestor(relativeFile, file, true);
              }
            });
          } else {
            myPatterns.add(new Processor<VirtualFile>() {
              @Override
              public boolean process(VirtualFile file) {
                return relativeFile.equals(file);
              }
            });
          }
        }
      }
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean isAvailable(@NotNull VirtualFile file) {
      if (file.isDirectory() || !file.isValid()) return false;
      for (Processor<VirtualFile> pattern : myPatterns) {
        if (pattern.process(file)) return true;
      }
      return false;
    }

    @Nullable
    @Override
    public Reader getSchemaReader() {
      try {
        return new FileReader(myFile);
      }
      catch (FileNotFoundException e) {
        LOG.info(e);
        return null;
      }
    }
  }
}

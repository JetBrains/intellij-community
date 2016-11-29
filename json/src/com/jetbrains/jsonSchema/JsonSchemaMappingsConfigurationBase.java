package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaMappingsConfigurationBase implements PersistentStateComponent<JsonSchemaMappingsConfigurationBase> {
  private final static Comparator<Item> COMPARATOR = (o1, o2) -> {
    if (o1.isPattern() != o2.isPattern()) return o1.isPattern() ? -1 : 1;
    if (o1.isDirectory() != o2.isDirectory()) return o1.isDirectory() ? -1 : 1;
    return o1.getPath().compareToIgnoreCase(o2.getPath());
  };
  @Tag("state") @AbstractCollection(surroundWithTag = false)
  protected final Map<String, SchemaInfo> myState = new TreeMap<>();

  @Nullable
  @Override
  public JsonSchemaMappingsConfigurationBase getState() {
    return this;
  }

  @Override
  public void loadState(JsonSchemaMappingsConfigurationBase state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public Map<String, SchemaInfo> getStateMap() {
    return myState;
  }

  public void setState(@NotNull Map<String, SchemaInfo> state) {
    myState.clear();
    myState.putAll(state);
  }

  public File convertToAbsoluteFile(@NotNull final String path) {
    return new File(path);
  }

  public void addSchema(@NotNull final SchemaInfo info) {
    myState.put(info.getName(), info);
  }

  public void removeSchema(@NotNull final SchemaInfo info) {
    myState.remove(info.getName());
  }

  public static class SchemaInfo {
    private String myName;
    private String myRelativePathToSchema;
    private boolean myApplicationLevel;
    private List<Item> myPatterns = new ArrayList<>();
    @Transient
    private List<Processor<VirtualFile>> myCalculatedPatterns;

    public SchemaInfo() {
    }

    public SchemaInfo(@NotNull String name, @NotNull String relativePathToSchema,
                      boolean applicationLevel,
                      List<Item> patterns) {
      myName = name;
      myRelativePathToSchema = relativePathToSchema;
      myApplicationLevel = applicationLevel;
      myPatterns = new ArrayList<>();
      if (patterns != null) {
        myPatterns.addAll(patterns);
      }
      Collections.sort(myPatterns, COMPARATOR);
    }

    public String getName() {
      return myName;
    }

    public void setName(@NotNull String name) {
      myName = name;
    }

    public String getRelativePathToSchema() {
      return myRelativePathToSchema;
    }

    public void setRelativePathToSchema(String relativePathToSchema) {
      myRelativePathToSchema = relativePathToSchema;
    }

    public boolean isApplicationLevel() {
      return myApplicationLevel;
    }

    public void setApplicationLevel(boolean applicationLevel) {
      myApplicationLevel = applicationLevel;
    }

    public List<Item> getPatterns() {
      return myPatterns;
    }

    public void setPatterns(List<Item> patterns) {
      myPatterns = patterns;
      myCalculatedPatterns = null;
    }

    @NotNull
    public List<Processor<VirtualFile>> getCalculatedPatterns(@NotNull final Project project) {
      if (myCalculatedPatterns == null) recalculatePatterns(project);
      return myCalculatedPatterns;
    }

    private void recalculatePatterns(Project project) {
      myCalculatedPatterns = new ArrayList<>();
      for (final JsonSchemaMappingsConfigurationBase.Item pattern : myPatterns) {
        if (pattern.isPattern()) {
          myCalculatedPatterns.add(new Processor<VirtualFile>() {
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

          final String path = FileUtilRt.toSystemIndependentName(pattern.getPath());
          final List<String> parts = ContainerUtil.filter(path.split("/"), s -> !".".equals(s));
          final VirtualFile relativeFile;
          if (parts.isEmpty()) {
            relativeFile = project.getBaseDir();
          } else {
            relativeFile = VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtil.toStringArray(parts));
            if (relativeFile == null) continue;
          }

          if (pattern.isDirectory()) {
            myCalculatedPatterns.add(file12 -> VfsUtilCore.isAncestor(relativeFile, file12, true));
          } else {
            myCalculatedPatterns.add(relativeFile::equals);
          }
        }
      }
    }


    @Nullable
    public VirtualFile getSchemaFile(@NotNull final Project project) {
      final String pathToSchema = FileUtil.toSystemIndependentName(getRelativePathToSchema());
      final List<String> strings = ContainerUtil.filter(pathToSchema.split("/"), s -> !StringUtil.isEmptyOrSpaces(s));
      return VfsUtil.findRelativeFile(project.getBaseDir(), ArrayUtil.toStringArray(strings));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SchemaInfo info = (SchemaInfo)o;

      if (myApplicationLevel != info.myApplicationLevel) return false;
      if (myName != null ? !myName.equals(info.myName) : info.myName != null) return false;
      if (myRelativePathToSchema != null
          ? !myRelativePathToSchema.equals(info.myRelativePathToSchema)
          : info.myRelativePathToSchema != null) {
        return false;
      }
      if (myPatterns != null ? !myPatterns.equals(info.myPatterns) : info.myPatterns != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName != null ? myName.hashCode() : 0;
      result = 31 * result + (myRelativePathToSchema != null ? myRelativePathToSchema.hashCode() : 0);
      result = 31 * result + (myApplicationLevel ? 1 : 0);
      result = 31 * result + (myPatterns != null ? myPatterns.hashCode() : 0);
      return result;
    }
  }

  public static class Item {
    private String myPath;
    private boolean myIsPattern;
    private boolean myIsDirectory;

    public Item() {
    }

    public Item(String path, boolean isPattern, boolean isDirectory) {
      myPath = path;
      myIsPattern = isPattern;
      myIsDirectory = isDirectory;
    }

    public String getPath() {
      return myPath;
    }

    public void setPath(String path) {
      myPath = path;
    }

    public boolean isPattern() {
      return myIsPattern;
    }

    public void setPattern(boolean pattern) {
      myIsPattern = pattern;
    }

    public boolean isDirectory() {
      return myIsDirectory;
    }

    public void setDirectory(boolean directory) {
      myIsDirectory = directory;
    }

    public String getPresentation() {
      final String prefix = myIsPattern ? "Pattern: " : (myIsDirectory ? "Directory: " : "File: ");
      return prefix + myPath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Item item = (Item)o;

      if (myIsPattern != item.myIsPattern) return false;
      if (myIsDirectory != item.myIsDirectory) return false;
      if (myPath != null ? !myPath.equals(item.myPath) : item.myPath != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPath != null ? myPath.hashCode() : 0;
      result = 31 * result + (myIsPattern ? 1 : 0);
      result = 31 * result + (myIsDirectory ? 1 : 0);
      return result;
    }
  }
}

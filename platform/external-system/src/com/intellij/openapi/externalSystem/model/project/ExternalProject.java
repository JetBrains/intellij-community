package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class ExternalProject extends AbstractNamedExternalEntity {

  private static final Logger LOG = Logger.getInstance("#" + ExternalProject.class.getName());

  private static final long serialVersionUID = 1L;

  private static final LanguageLevel  DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_6;
  private static final JavaSdkVersion DEFAULT_JDK_VERSION    = JavaSdkVersion.JDK_1_6;
  private static final Pattern        JDK_VERSION_PATTERN    = Pattern.compile(".*1\\.(\\d+).*");

  private final Set<ExternalModule>  myModules   = new HashSet<ExternalModule>();
  private final Set<ExternalLibrary> myLibraries = new HashSet<ExternalLibrary>();
  
  @NotNull private final ProjectSystemId mySystemId;
  
  private String myProjectFileDirectoryPath;
  private JavaSdkVersion myJdkVersion    = DEFAULT_JDK_VERSION;
  private LanguageLevel  myLanguageLevel = DEFAULT_LANGUAGE_LEVEL;

  private Sdk    mySdk;
  private String myCompileOutputPath;

  public ExternalProject(@NotNull ProjectSystemId owner,
                         @NotNull String projectFileDirectoryPath,
                         @NotNull String compileOutputPath,
                         @NotNull ProjectSystemId id)
  {
    super(owner, "unnamed");
    mySystemId = id;
    myProjectFileDirectoryPath = ExternalSystemUtil.toCanonicalPath(projectFileDirectoryPath);
    myCompileOutputPath = ExternalSystemUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public ProjectSystemId getSystemId() {
    return mySystemId;
  }

  @NotNull
  public String getProjectFileDirectoryPath() {
    return myProjectFileDirectoryPath;
  }

  public void setProjectFileDirectoryPath(@NotNull String projectFileDirectoryPath) {
    myProjectFileDirectoryPath = ExternalSystemUtil.toCanonicalPath(projectFileDirectoryPath);
  }

  public void addModule(@NotNull ExternalModule module) {
    myModules.add(module);
  }
  
  @NotNull
  public Set<? extends ExternalModule> getModules() {
    return myModules;
  }

  @NotNull
  public Set<? extends ExternalLibrary> getLibraries() {
    return myLibraries;
  }

  public boolean addLibrary(@NotNull ExternalLibrary library) {
    return myLibraries.add(library);
  }

  @NotNull
  public String getCompileOutputPath() {
    return myCompileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    myCompileOutputPath = ExternalSystemUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public JavaSdkVersion getJdkVersion() {
    return myJdkVersion;
  }

  public void setJdkVersion(@NotNull JavaSdkVersion jdkVersion) {
    myJdkVersion = jdkVersion;
  }

  public void setJdkVersion(@Nullable String jdk) {
    if (jdk == null) {
      return;
    }
    try {
      int version = Integer.parseInt(jdk.trim());
      if (applyJdkVersion(version)) {
        return;
      }
    }
    catch (NumberFormatException e) {
      // Ignore.
    }

    Matcher matcher = JDK_VERSION_PATTERN.matcher(jdk);
    if (!matcher.matches()) {
      return;
    }
    String versionAsString = matcher.group(1);
    try {
      applyJdkVersion(Integer.parseInt(versionAsString));
    }
    catch (NumberFormatException e) {
      // Ignore.
    }
  }

  public boolean applyJdkVersion(int version) {
    if (version < 0 || version >= JavaSdkVersion.values().length) {
      LOG.warn(String.format(
        "Unsupported jdk version detected (%d). Expected to get number from range [0; %d]", version, JavaSdkVersion.values().length
      ));
      return false;
    }
    for (JavaSdkVersion sdkVersion : JavaSdkVersion.values()) {
      if (sdkVersion.ordinal() == version) {
        myJdkVersion = sdkVersion;
        return true;
      }
    }
    assert false : version + ", max value: " + JavaSdkVersion.values().length;
    return false;
  }

  @Nullable
  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(@NotNull LanguageLevel level) {
    myLanguageLevel = level;
  }

  public void setLanguageLevel(@Nullable String languageLevel) {
    LanguageLevel level = LanguageLevel.parse(languageLevel);
    if (level != null) {
      myLanguageLevel = level;
    }
  }

  @Override
  public void invite(@NotNull ExternalEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModules.hashCode();
    result = 31 * result + myCompileOutputPath.hashCode();
    result = 31 * result + myJdkVersion.hashCode();
    result = 31 * result + myLanguageLevel.hashCode();
    result = 31 * result + mySystemId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    ExternalProject that = (ExternalProject)o;

    if (!myCompileOutputPath.equals(that.myCompileOutputPath)) return false;
    if (!myJdkVersion.equals(that.myJdkVersion)) return false;
    if (myLanguageLevel != that.myLanguageLevel) return false;
    if (!mySystemId.equals(that.mySystemId)) return false;
    if (!myModules.equals(that.myModules)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s project '%s':jdk='%s'|language level='%s'|modules=%s",
                         mySystemId.toString().toLowerCase(), getName(), getJdkVersion(), getLanguageLevel(), getModules());
  }

  @NotNull
  @Override
  public ExternalProject clone(@NotNull ExternalEntityCloneContext context) {
    ExternalProject result = new ExternalProject(getOwner(), getProjectFileDirectoryPath(), getCompileOutputPath(), mySystemId);
    result.setName(getName());
    result.setJdkVersion(getJdkVersion());
    result.setLanguageLevel(getLanguageLevel());
    for (ExternalModule module : getModules()) {
      result.addModule(module.clone(context));
    }
    for (ExternalLibrary library : getLibraries()) {
      result.addLibrary(library.clone(context));
    }
    return result;
  }
}

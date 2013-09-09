/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.impl.JavaSdkUtil;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 * @since Sep 17, 2004
 */
public class JavaSdkImpl extends JavaSdk {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.JavaSdkImpl");
  // do not use javaw.exe for Windows because of issues with encoding
  @NonNls private static final String VM_EXE_NAME = "java";
  @NonNls private final Pattern myVersionStringPattern = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
  @NonNls private static final String JAVA_VERSION_PREFIX = "java version ";
  @NonNls private static final String OPENJDK_VERSION_PREFIX = "openjdk version ";
  public static final DataKey<Boolean> KEY = DataKey.create("JavaSdk");

  public JavaSdkImpl() {
    super("JavaSDK");
  }

  @Override
  public String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.PpJdk;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.java";
  }

  @Override
  public Icon getIconForAddAction() {
    return AllIcons.General.AddJdk;
  }

  @NonNls
  @Override
  @Nullable
  public String getDefaultDocumentationUrl(@NotNull final Sdk sdk) {
    final JavaSdkVersion version = getVersion(sdk);
    if (version == JavaSdkVersion.JDK_1_5) {
      return "http://docs.oracle.com/javase/1.5.0/docs/api/";
    }
    if (version == JavaSdkVersion.JDK_1_6) {
      return "http://docs.oracle.com/javase/6/docs/api/";
    }
    if (version == JavaSdkVersion.JDK_1_7) {
      return "http://docs.oracle.com/javase/7/docs/api/";
    }
    if (version == JavaSdkVersion.JDK_1_8) {
      return "http://download.java.net/jdk8/docs/api/";
    }
    return null;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getBinPath(@NotNull Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  @NonNls
  public String getToolsPath(@NotNull Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  @Override
  public String getVMExecutablePath(@NotNull Sdk sdk) {
    /*
    if ("64".equals(System.getProperty("sun.arch.data.model"))) {
      return getBinPath(sdk) + File.separator + System.getProperty("os.arch") + File.separator + VM_EXE_NAME;
    }
    */
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String homePath = sdk.getHomePath();
    assert homePath != null : sdk;
    String path = FileUtil.toSystemDependentName(homePath);
    if (!path.endsWith(File.separator)) {
      path += File.separator;
    }
    return path;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String suggestHomePath() {
    if (SystemInfo.isMac) {
      if (new File("/usr/libexec/java_home").exists()) {
        final String path = ExecUtil.execAndReadLine("/usr/libexec/java_home");
        if (path != null && new File(path).exists()) {
          return path;
        }
      }
      return "/System/Library/Frameworks/JavaVM.framework/Versions";
    }

    if (SystemInfo.isLinux) {
      final String[] homes = {"/usr/java", "/opt/java", "/usr/lib/jvm"};
      for (String home : homes) {
        if (new File(home).isDirectory()) {
          return home;
        }
      }
    }

    if (SystemInfo.isSolaris) {
      return "/usr/jdk";
    }

    if (SystemInfo.isWindows) {
      String property = System.getProperty("java.home");
      if (property == null) return null;
      File javaHome = new File(property).getParentFile();//actually java.home points to to jre home
      if (javaHome != null && JdkUtil.checkForJdk(javaHome)) {
        return javaHome.getAbsolutePath();
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths() {
    if (!SystemInfo.isWindows)
      return Collections.singletonList(suggestHomePath());

    String property = System.getProperty("java.home");
    if (property == null)
      return Collections.emptyList();

    File javaHome = new File(property).getParentFile();//actually java.home points to to jre home
    if (javaHome == null || !javaHome.isDirectory() || javaHome.getParentFile() == null) {
      return Collections.emptyList();
    }
    ArrayList<String> result = new ArrayList<String>();
    File javasFolder = javaHome.getParentFile();
    scanFolder(javasFolder, result);
    File parentFile = javasFolder.getParentFile();
    File root = parentFile != null ? parentFile.getParentFile() : null;
    String name = parentFile != null ? parentFile.getName() : "";
    if (name.contains("Program Files") && root != null) {
      String x86Suffix = " (x86)";
      boolean x86 = name.endsWith(x86Suffix) && name.length() > x86Suffix.length();
      File anotherJavasFolder;
      if (x86) {
        anotherJavasFolder = new File(root, name.substring(0, name.length() - x86Suffix.length()));
      }
      else {
        anotherJavasFolder = new File(root, name + x86Suffix);
      }
      if (anotherJavasFolder.isDirectory()) {
        scanFolder(new File(anotherJavasFolder, javasFolder.getName()), result);
      }
    }
    return result;
  }

  private static void scanFolder(File javasFolder, ArrayList<String> result) {
    File[] candidates = javasFolder.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return JdkUtil.checkForJdk(pathname);
      }
    });
    if (candidates != null) {
      result.addAll(ContainerUtil.map2List(candidates, new Function<File, String>() {
        @Override
        public String fun(File file) {
          return file.getAbsolutePath();
        }
      }));
    }
  }

  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = super.getHomeChooserDescriptor();
    descriptor.putUserData(KEY, Boolean.TRUE);
    return descriptor;
  }

  @NonNls public static final String MAC_HOME_PATH = "/Home";

  @Override
  public String adjustSelectedSdkHome(String homePath) {
    if (SystemInfo.isMac) {
      File home = new File(homePath, MAC_HOME_PATH);
      if (home.exists()) return home.getPath();

      home = new File(new File(homePath, "Contents"), "Home");
      if (home.exists()) return home.getPath();
    }

    return homePath;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return checkForJdk(new File(path));
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final String suggestedName;
    if (currentSdkName != null && !currentSdkName.isEmpty()) {
      final Matcher matcher = myVersionStringPattern.matcher(currentSdkName);
      final boolean replaceNameWithVersion = matcher.matches();
      if (replaceNameWithVersion){
        // user did not change name -> set it automatically
        final String versionString = getVersionString(sdkHome);
        suggestedName = versionString == null ? currentSdkName : matcher.replaceFirst("$1" + versionString + "$3");
      }
      else {
        suggestedName = currentSdkName;
      }
    }
    else {
      String versionString = getVersionString(sdkHome);
      suggestedName = versionString == null ? ProjectBundle.message("sdk.java.unknown.name") : getVersionNumber(versionString);
    }
    return suggestedName;
  }

  @NotNull
  private static String getVersionNumber(@NotNull String versionString) {
    if (versionString.startsWith(JAVA_VERSION_PREFIX) || versionString.startsWith(OPENJDK_VERSION_PREFIX)) {
      boolean openJdk = versionString.startsWith(OPENJDK_VERSION_PREFIX);
      versionString = versionString.substring(openJdk ? OPENJDK_VERSION_PREFIX.length() : JAVA_VERSION_PREFIX.length());
      if (versionString.startsWith("\"") && versionString.endsWith("\"")) {
        versionString = versionString.substring(1, versionString.length() - 1);
      }
      int dotIdx = versionString.indexOf('.');
      if (dotIdx > 0) {
        try {
          int major = Integer.parseInt(versionString.substring(0, dotIdx));
          int minorDot = versionString.indexOf('.', dotIdx + 1);
          if (minorDot > 0) {
            int minor = Integer.parseInt(versionString.substring(dotIdx + 1, minorDot));
            versionString = major + "." + minor;
          }
        }
        catch (NumberFormatException e) {
          // Do nothing. Use original version string if failed to parse according to major.minor pattern.
        }
      }
    }
    return versionString;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void setupSdkPaths(@NotNull Sdk sdk) {
    final File jdkHome = new File(sdk.getHomePath());
    List<VirtualFile> classes = findClasses(jdkHome, false);
    VirtualFile sources = findSources(jdkHome);
    VirtualFile docs = findDocs(jdkHome, "docs/api");

    final SdkModificator sdkModificator = sdk.getSdkModificator();
    final Set<VirtualFile> previousRoots = new LinkedHashSet<VirtualFile>(Arrays.asList(sdkModificator.getRoots(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    previousRoots.removeAll(new HashSet<VirtualFile>(classes));
    for (VirtualFile aClass : classes) {
      sdkModificator.addRoot(aClass, OrderRootType.CLASSES);
    }
    for (VirtualFile root : previousRoots) {
      sdkModificator.addRoot(root, OrderRootType.CLASSES);
    }
    if(sources != null){
      sdkModificator.addRoot(sources, OrderRootType.SOURCES);
    }
    if(docs != null){
      sdkModificator.addRoot(docs, JavadocOrderRootType.getInstance());
    }
    else if (SystemInfo.isMac) {
      VirtualFile commonDocs = findDocs(jdkHome, "docs");
      if (commonDocs == null) {
        commonDocs = findInJar(new File(jdkHome, "docs.jar"), "doc/api");
        if (commonDocs == null) {
          commonDocs = findInJar(new File(jdkHome, "docs.jar"), "docs/api");
        }
      }
      if (commonDocs != null) {
        sdkModificator.addRoot(commonDocs, JavadocOrderRootType.getInstance());
      }

      VirtualFile appleDocs = findDocs(jdkHome, "appledocs");
      if (appleDocs == null) {
        appleDocs = findInJar(new File(jdkHome, "appledocs.jar"), "appledoc/api");
      }
      if (appleDocs != null) {
        sdkModificator.addRoot(appleDocs, JavadocOrderRootType.getInstance());
      }

      if (commonDocs == null && appleDocs == null && sources == null) {
        String url = getDefaultDocumentationUrl(sdk);
        if (url != null) {
          sdkModificator.addRoot(VirtualFileManager.getInstance().findFileByUrl(url), JavadocOrderRootType.getInstance());
        }
      }
    }
    attachJdkAnnotations(sdkModificator);
    sdkModificator.commitChanges();
  }

  public static void attachJdkAnnotations(@NotNull SdkModificator modificator) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    // community idea under idea
    VirtualFile root = lfs.findFileByPath(FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/java/jdkAnnotations");

    if (root == null) {  // idea under idea
      root = lfs.findFileByPath(FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/community/java/jdkAnnotations");
    }
    if (root == null) { // build
      root = VirtualFileManager.getInstance().findFileByUrl("jar://"+ FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/jdkAnnotations.jar!/");
    }
    if (root == null) {
      LOG.error("jdk annotations not found in: "+ FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/jdkAnnotations.jar!/");
      return;
    }

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    modificator.removeRoot(root, annoType);
    modificator.addRoot(root, annoType);
  }

  private final Map<String, String> myCachedVersionStrings = new HashMap<String, String>();

  @Override
  public final String getVersionString(final String sdkHome) {
    if (myCachedVersionStrings.containsKey(sdkHome)) {
      return myCachedVersionStrings.get(sdkHome);
    }
    String versionString = getJdkVersion(sdkHome);
    if (versionString != null && versionString.isEmpty()) {
      versionString = null;
    }

    if (versionString != null){
      myCachedVersionStrings.put(sdkHome, versionString);
    }

    return versionString;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return getName();
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() {
  }

  @Override
  public int compareTo(@NotNull String versionString, @NotNull String versionNumber) {
    return getVersionNumber(versionString).compareTo(versionNumber);
  }

  @Override
  public JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    String version = sdk.getVersionString();
    if (version == null) return null;
    return JdkVersionUtil.getVersion(version);
  }

  @Override
  @Nullable
  public JavaSdkVersion getVersion(@NotNull String versionString) {
    return JdkVersionUtil.getVersion(versionString);
  }

  @Override
  public boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version) {
    JavaSdkVersion sdkVersion = getVersion(sdk);
    return sdkVersion != null && sdkVersion.isAtLeast(version);
  }

  @Override
  public Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre) {
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string

    File jdkHomeFile = new File(home);
    addClasses(jdkHomeFile, sdkModificator, isJre);
    addSources(jdkHomeFile, sdkModificator);
    addDocs(jdkHomeFile, sdkModificator);
    sdkModificator.commitChanges();

    return jdk;
  }

  private static void addClasses(File file, SdkModificator sdkModificator, boolean isJre) {
    for (VirtualFile virtualFile : findClasses(file, isJre)) {
      sdkModificator.addRoot(virtualFile, OrderRootType.CLASSES);
    }
  }

  private static List<VirtualFile> findClasses(File file, boolean isJre) {
    List<VirtualFile> result = ContainerUtil.newArrayList();

    List<File> rootFiles = JavaSdkUtil.getJdkClassesRoots(file, isJre);
    for (File child : rootFiles) {
      String url = VfsUtil.getUrlForLibraryRoot(child);
      VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (vFile != null) {
        result.add(vFile);
      }
    }

    return result;
  }

  private static void addSources(File file, SdkModificator sdkModificator) {
    VirtualFile vFile = findSources(file);
    if (vFile != null) {
      sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
    }
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static VirtualFile findSources(File file) {
    File srcDir = new File(file, "src");
    File jarFile = new File(file, "src.jar");
    if (!jarFile.exists()) {
      jarFile = new File(file, "src.zip");
    }

    if (jarFile.exists()) {
      VirtualFile vFile = findInJar(jarFile, "src");
      if (vFile != null) return vFile;
      // try 1.4 format
      vFile = findInJar(jarFile, "");
      return vFile;
    }
    else {
      if (!srcDir.exists() || !srcDir.isDirectory()) return null;
      String path = srcDir.getAbsolutePath().replace(File.separatorChar, '/');
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void addDocs(File file, SdkModificator rootContainer) {
    VirtualFile vFile = findDocs(file, "docs/api");
    if (vFile != null) {
      rootContainer.addRoot(vFile, JavadocOrderRootType.getInstance());
    }
  }

  @Nullable
  private static VirtualFile findInJar(File jarFile, String relativePath) {
    if (!jarFile.exists()) return null;
    String url = JarFileSystem.PROTOCOL_PREFIX +
                 jarFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR + relativePath;
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Nullable
  public static VirtualFile findDocs(File file, final String relativePath) {
    file = new File(file.getAbsolutePath() + File.separator + relativePath.replace('/', File.separatorChar));
    if (!file.exists() || !file.isDirectory()) return null;
    String path = file.getAbsolutePath().replace(File.separatorChar, '/');
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }
}

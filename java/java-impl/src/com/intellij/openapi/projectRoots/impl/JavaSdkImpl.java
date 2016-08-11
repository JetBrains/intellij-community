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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.jrt.JrtFileSystem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
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

  public static final DataKey<Boolean> KEY = DataKey.create("JavaSdk");

  private static final String VM_EXE_NAME = "java";   // do not use JavaW.exe for Windows because of issues with encoding
  private static final Pattern VERSION_STRING_PATTERN = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
  private static final String JAVA_VERSION_PREFIX = "java version ";
  private static final String OPENJDK_VERSION_PREFIX = "openjdk version ";

  public JavaSdkImpl(final VirtualFileManager fileManager, final FileTypeManager fileTypeManager) {
    super("JavaSDK");

    fileManager.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        updateCache(event);
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        updateCache(event);
      }

      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        updateCache(event);
      }

      private void updateCache(VirtualFileEvent event) {
        final VirtualFile file = event.getFile();
        if (FileTypes.ARCHIVE.equals(fileTypeManager.getFileTypeByFileName(event.getFileName()))) {
          final String filePath = file.getPath();
          synchronized (myCachedVersionStrings) {
            for (String sdkHome : myCachedVersionStrings.keySet()) {
              if (FileUtil.isAncestor(sdkHome, filePath, false)) {
                myCachedVersionStrings.remove(sdkHome);
                break;
              }
            }
          }
        }
      }
    });
  }

  @NotNull
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

  @NotNull
  @Override
  public Icon getIconForAddAction() {
    return AllIcons.General.AddJdk;
  }

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
      return "http://docs.oracle.com/javase/8/docs/api";
    }
    return null;
  }

  @Nullable
  @Override
  public String getDownloadSdkUrl() {
    return "http://www.oracle.com/technetwork/java/javase/downloads/index.html";
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) {
  }

  @Override
  @SuppressWarnings("HardCodedStringLiteral")
  public String getBinPath(@NotNull Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  public String getToolsPath(@NotNull Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  @Override
  public String getVMExecutablePath(@NotNull Sdk sdk) {
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
  public String suggestHomePath() {
    if (SystemInfo.isMac) {
      if (new File("/usr/libexec/java_home").canExecute()) {
        String path = ExecUtil.execAndReadLine(new GeneralCommandLine("/usr/libexec/java_home"));
        if (path != null && new File(path).isDirectory()) {
          return path;
        }
      }

      String home = checkKnownLocations("/Library/Java/JavaVirtualMachines", "/System/Library/Java/JavaVirtualMachines");
      if (home != null) return home;
    }

    if (SystemInfo.isLinux) {
      String home = checkKnownLocations("/usr/java", "/opt/java", "/usr/lib/jvm");
      if (home != null) return home;
    }

    if (SystemInfo.isSolaris) {
      String home = checkKnownLocations("/usr/jdk");
      if (home != null) return home;
    }

    String property = System.getProperty("java.home");
    if (property != null) {
      File javaHome = new File(property);
      if (javaHome.getName().equals("jre")) {
        javaHome = javaHome.getParentFile();
      }
      if (javaHome != null && javaHome.isDirectory()) {
        return javaHome.getAbsolutePath();
      }
    }

    return null;
  }

  @Nullable
  private static String checkKnownLocations(String... locations) {
    for (String home : locations) {
      if (new File(home).isDirectory()) {
        return home;
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
    ArrayList<String> result = new ArrayList<>();
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

  private static void scanFolder(File javasFolder, List<String> result) {
    @SuppressWarnings("RedundantCast") File[] candidates = javasFolder.listFiles((FileFilter)JdkUtil::checkForJdk);
    if (candidates != null) {
      for (File file : candidates) {
        result.add(file.getAbsolutePath());
      }
    }
  }

  @NotNull
  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    final FileChooserDescriptor baseDescriptor = super.getHomeChooserDescriptor();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(baseDescriptor) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (files.length > 0 && !JrtFileSystem.isSupported()) {
          String path = files[0].getPath();
          if (JrtFileSystem.isModularJdk(path) || JrtFileSystem.isModularJdk(adjustSelectedSdkHome(path))) {
            throw new Exception(LangBundle.message("jrt.not.available.message"));
          }
        }
        baseDescriptor.validateSelectedFiles(files);
      }
    };
    descriptor.putUserData(KEY, Boolean.TRUE);
    return descriptor;
  }

  @NotNull
  @Override
  public String adjustSelectedSdkHome(@NotNull String homePath) {
    if (SystemInfo.isMac) {
      File home = new File(homePath, "/Home");
      if (home.exists()) return home.getPath();

      home = new File(homePath, "Contents/Home");
      if (home.exists()) return home.getPath();
    }

    return homePath;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return checkForJdk(new File(path)) &&
           (!JrtFileSystem.isModularJdk(path) || JrtFileSystem.isSupported());
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final String suggestedName;
    if (currentSdkName != null && !currentSdkName.isEmpty()) {
      final Matcher matcher = VERSION_STRING_PATTERN.matcher(currentSdkName);
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
  @SuppressWarnings("HardCodedStringLiteral")
  public void setupSdkPaths(@NotNull Sdk sdk) {
    String homePath = sdk.getHomePath();
    assert homePath != null : sdk;

    File jdkHome = new File(homePath);
    List<VirtualFile> classes = findClasses(jdkHome, false);
    VirtualFile sources = findSources(jdkHome);
    VirtualFile docs = findDocs(jdkHome, "docs/api");
    SdkModificator sdkModificator = sdk.getSdkModificator();

    Set<VirtualFile> previousRoots = new LinkedHashSet<>(Arrays.asList(sdkModificator.getRoots(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    previousRoots.removeAll(new HashSet<>(classes));
    for (VirtualFile aClass : classes) {
      sdkModificator.addRoot(aClass, OrderRootType.CLASSES);
    }
    for (VirtualFile root : previousRoots) {
      sdkModificator.addRoot(root, OrderRootType.CLASSES);
    }

    if (sources != null) {
      sdkModificator.addRoot(sources, OrderRootType.SOURCES);
    }
    VirtualFile javaFxSources = findSources(jdkHome, "javafx-src");
    if (javaFxSources != null) {
      sdkModificator.addRoot(javaFxSources, OrderRootType.SOURCES);
    }

    if (docs != null) {
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
    else if (getVersion(sdk) == JavaSdkVersion.JDK_1_7) {
      VirtualFile url = VirtualFileManager.getInstance().findFileByUrl("http://docs.oracle.com/javafx/2/api/");
      sdkModificator.addRoot(url, JavadocOrderRootType.getInstance());
    }

    attachJdkAnnotations(sdkModificator);

    sdkModificator.commitChanges();
  }

  public static void attachJdkAnnotations(@NotNull SdkModificator modificator) {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    List<String> pathsChecked = new ArrayList<>();
    // community idea under idea
    String path = FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/java/jdkAnnotations";
    VirtualFile root = lfs.findFileByPath(path);
    pathsChecked.add(path);

    if (root == null) {  // idea under idea
      path = FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/community/java/jdkAnnotations";
      root = lfs.findFileByPath(path);
      pathsChecked.add(path);
    }
    if (root == null) { // build
      String url = "jar://" + FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/jdkAnnotations.jar!/";
      root = VirtualFileManager.getInstance().findFileByUrl(url);
      pathsChecked.add(FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/jdkAnnotations.jar");
    }
    if (root == null) {
      String msg = "Paths checked:\n";
      for (String p : pathsChecked) {
        File file = new File(p);
        msg += "Path: '"+p+"' "+(file.exists() ? "Found" : "Not found")+"; directory children: "+Arrays.toString(file.getParentFile().listFiles())+"\n";
      }
      LOG.error("JDK annotations not found", msg);
      return;
    }

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    modificator.removeRoot(root, annoType);
    modificator.addRoot(root, annoType);
  }

  private final Map<String, String> myCachedVersionStrings = Collections.synchronizedMap(new HashMap<String, String>());

  @Override
  public final String getVersionString(String sdkHome) {
    String versionString = myCachedVersionStrings.get(sdkHome);
    if (versionString == null) {
      versionString = getJdkVersion(sdkHome);
      if (!StringUtil.isEmpty(versionString)) {
        myCachedVersionStrings.put(sdkHome, versionString);
      }
    }
    return versionString;
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

  @NotNull
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
    VirtualFileManager fileManager = VirtualFileManager.getInstance();

    String path = file.getPath();
    if (JrtFileSystem.isModularJdk(path)) {
      String url = VirtualFileManager.constructUrl(JrtFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(path) + JrtFileSystem.SEPARATOR);
      for (String module : JrtFileSystem.listModules(path)) {
        ContainerUtil.addIfNotNull(result, fileManager.findFileByUrl(url + module));
      }
    }

    for (File root : JavaSdkUtil.getJdkClassesRoots(file, isJre)) {
      String url = VfsUtil.getUrlForLibraryRoot(root);
      ContainerUtil.addIfNotNull(result, fileManager.findFileByUrl(url));
    }

    Collections.sort(result, (o1, o2) -> o1.getPath().compareTo(o2.getPath()));

    return result;
  }

  private static void addSources(File file, SdkModificator sdkModificator) {
    VirtualFile vFile = findSources(file);
    if (vFile != null) {
      sdkModificator.addRoot(vFile, OrderRootType.SOURCES);
    }
  }

  @Nullable
  @SuppressWarnings("HardCodedStringLiteral")
  private static VirtualFile findSources(File file) {
    return findSources(file, "src");
  }

  @Nullable
  @SuppressWarnings("HardCodedStringLiteral")
  private static VirtualFile findSources(File file, final String srcName) {
    File srcDir = new File(file, "src");
    File jarFile = new File(file, srcName + ".jar");
    if (!jarFile.exists()) {
      jarFile = new File(file, srcName + ".zip");
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

  @SuppressWarnings("HardCodedStringLiteral")
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
  private static VirtualFile findDocs(File file, final String relativePath) {
    file = new File(file.getAbsolutePath() + File.separator + relativePath.replace('/', File.separatorChar));
    if (!file.exists() || !file.isDirectory()) return null;
    String path = file.getAbsolutePath().replace(File.separatorChar, '/');
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }
}
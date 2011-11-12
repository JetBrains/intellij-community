/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 17, 2004
 */
public class JavaSdkImpl extends JavaSdk {
  // do not use javaw.exe for Windows because of issues with encoding
  @NonNls private static final String VM_EXE_NAME = "java";
  @NonNls private final Pattern myVersionStringPattern = Pattern.compile("^(.*)java version \"([1234567890_.]*)\"(.*)$");
  public static final Icon ICON = IconLoader.getIcon("/nodes/ppJdkClosed.png");
  private static final Icon JDK_ICON_EXPANDED = IconLoader.getIcon("/nodes/ppJdkOpen.png");
  private static final Icon ADD_ICON = IconLoader.getIcon("/general/addJdk.png");
  @NonNls private static final String JAVA_VERSION_PREFIX = "java version ";
  @NonNls private static final String OPENJDK_VERSION_PREFIX = "openjdk version ";
  private static final Map<JavaSdkVersion, String[]> VERSION_STRINGS = new EnumMap<JavaSdkVersion, String[]>(JavaSdkVersion.class);

  static {
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_0, new String[]{"1.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_1, new String[]{"1.1"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_2, new String[]{"1.2"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_3, new String[]{"1.3"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_4, new String[]{"1.4"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_5, new String[]{"1.5", "5.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_6, new String[]{"1.6", "6.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_7, new String[]{"1.7", "7.0"});
    VERSION_STRINGS.put(JavaSdkVersion.JDK_1_8, new String[]{"1.8", "8.0"});
  }

  public JavaSdkImpl() {
    super("JavaSDK");
  }

  @Override
  public String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.java";
  }

  @Override
  public Icon getIconForExpandedTreeNode() {
    return JDK_ICON_EXPANDED;
  }

  @Override
  public Icon getIconForAddAction() {
    return ADD_ICON;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getBinPath(Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  @NonNls
  public String getToolsPath(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    final boolean isJdk1_x = versionString != null && (versionString.contains("1.0") || versionString.contains("1.1"));
    return getConvertedHomePath(sdk) + "lib" + File.separator + (isJdk1_x? "classes.zip" : "tools.jar");
  }

  @Override
  public String getVMExecutablePath(Sdk sdk) {
    /*
    if ("64".equals(System.getProperty("sun.arch.data.model"))) {
      return getBinPath(sdk) + File.separator + System.getProperty("os.arch") + File.separator + VM_EXE_NAME;
    }
    */
    return getBinPath(sdk) + File.separator + VM_EXE_NAME;
  }

  private static String getConvertedHomePath(Sdk sdk) {
    String path = sdk.getHomePath().replace('/', File.separatorChar);
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
        BufferedReader input = null;
        try {
          final Process exec = Runtime.getRuntime().exec("/usr/libexec/java_home");
          input = new BufferedReader(new InputStreamReader(exec.getInputStream()));
          final String path = input.readLine();
          if (new File(path).exists()) return path;
        }
        catch (IOException e) {
          // nothing
        } finally {
          try {
            if (input != null) input.close();
          }
          catch (IOException e) {
            // ignore
          }
        }
      }

      return "/System/Library/Frameworks/JavaVM.framework/Versions/";
    }
    if (SystemInfo.isLinux) {
      return "/usr/lib/jvm/";
    }
    if (SystemInfo.isSolaris) {
      return "/usr/jdk/";
    }
    return null;
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
    if (currentSdkName != null && currentSdkName.length() > 0) {
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
      if (versionString != null) {
        suggestedName = getVersionNumber(versionString);
      } else {
        suggestedName = ProjectBundle.message("sdk.java.unknown.name");
      }
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
  public void setupSdkPaths(Sdk sdk) {
    final File jdkHome = new File(sdk.getHomePath());
    VirtualFile[] classes = findClasses(jdkHome, false);
    VirtualFile sources = findSources(jdkHome);
    VirtualFile docs = findDocs(jdkHome, "docs/api");

    final SdkModificator sdkModificator = sdk.getSdkModificator();
    final Set<VirtualFile> previousRoots = new LinkedHashSet<VirtualFile>(Arrays.asList(sdkModificator.getRoots(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    previousRoots.removeAll(new HashSet<VirtualFile>(Arrays.asList(classes)));
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
    }
    sdkModificator.commitChanges();
  }

  private final Map<String, String> myCachedVersionStrings = new HashMap<String, String>();

  @Override
  public final String getVersionString(final String sdkHome) {
    if (myCachedVersionStrings.containsKey(sdkHome)) {
      return myCachedVersionStrings.get(sdkHome);
    }
    String versionString = getJdkVersion(sdkHome);
    if (versionString != null && versionString.length() == 0) {
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
  public Sdk createJdk(final String jdkName, final String home, final boolean isJre) {
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    jdk.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string

    File jdkHomeFile = new File(home);
    addClasses(jdkHomeFile, sdkModificator, isJre);
    addSources(jdkHomeFile, sdkModificator);
    addDocs(jdkHomeFile, sdkModificator);
    sdkModificator.commitChanges();
    return jdk;
  }

  @Override
  public JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    String version = sdk.getVersionString();
    if (version == null) return null;
    return getVersion(version);
  }

  @Override
  @Nullable
  public JavaSdkVersion getVersion(@NotNull String versionString) {
    for (Map.Entry<JavaSdkVersion, String[]> entry : VERSION_STRINGS.entrySet()) {
      for (String s : entry.getValue()) {
        if (versionString.contains(s)) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  @Override
  public boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version) {
    JavaSdkVersion sdkVersion = getVersion(sdk);
    return sdkVersion != null && sdkVersion.isAtLeast(version);
  }

  private static File getPathForJdkNamed(String name) {
    File mockJdkCEPath = new File(PathManager.getHomePath(), "java/" + name);
    if (mockJdkCEPath.exists()) {
      return mockJdkCEPath;
    }
    return new File(PathManager.getHomePath(), "community/java/" + name);
  }
  public static Sdk getMockJdk17() {
    return getMockJdk17("java 1.7");
  }
  public static Sdk getMockJdk17(String name) {
    return createMockJdk(getMockJdk17Path().getPath(), name, getInstance());
  }
  public static Sdk getMockJdk14() {
    File mockJdkCEPath = getMockJdk14Path();
    return createMockJdk(mockJdkCEPath.getPath(), "java 1.4", getInstance());
  }

  public static File getMockJdk14Path() {
    return getPathForJdkNamed("mockJDK-1.4");
  }
  public static File getMockJdk17Path() {
    return getPathForJdkNamed("mockJDK-1.7");
  }

  public static Sdk getWebMockJdk17() {
    Sdk jdk = getMockJdk17();
    addWebJarsTo(jdk);
    return jdk;
  }

  public static void addWebJarsTo(Sdk jdk) {
    SdkModificator sdkModificator = jdk.getSdkModificator();
    File jar = new File(PathManager.getHomePath(), "lib/jsp-api.jar");
    VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(LocalFileSystem.getInstance().findFileByIoFile(jar));
    sdkModificator.addRoot(root, OrderRootType.CLASSES);
    File jar2 = new File(PathManager.getHomePath(), "lib/servlet-api.jar");
    VirtualFile root2 = JarFileSystem.getInstance().getJarRootForLocalFile(LocalFileSystem.getInstance().findFileByIoFile(jar2));
    sdkModificator.addRoot(root2, OrderRootType.CLASSES);
    sdkModificator.commitChanges();
  }

  private static Sdk createMockJdk(String jdkHome, final String versionName, JavaSdk javaSdk) {
    File jdkHomeFile = new File(jdkHome);
    if (!jdkHomeFile.exists()) return null;

    final Sdk jdk = new ProjectJdkImpl(versionName, javaSdk);
    final SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = jdkHome.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.setVersionString(versionName); // must be set after home path, otherwise setting home path clears the version string

    addSources(jdkHomeFile, sdkModificator);
    addClasses(jdkHomeFile, sdkModificator, false);
    addClasses(jdkHomeFile, sdkModificator, true);
    sdkModificator.commitChanges();

    return jdk;
  }

  private static void addClasses(File file, SdkModificator sdkModificator, final boolean isJre) {
    VirtualFile[] classes = findClasses(file, isJre);
    for (VirtualFile virtualFile : classes) {
      sdkModificator.addRoot(virtualFile, OrderRootType.CLASSES);
    }
  }

  private static VirtualFile[] findClasses(File file, boolean isJre) {
    FileFilter jarFileFilter = new FileFilter(){
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f){
        return !f.isDirectory() && f.getName().endsWith(".jar");
      }
    };

    File[] jarDirs;
    if(SystemInfo.isMac && /*!ApplicationManager.getApplication().isUnitTestMode()) &&*/ !file.getName().startsWith("mockJDK")){
      final File openJdkRtJar = new File(new File(new File(file, "jre"), "lib"), "rt.jar");
      if (openJdkRtJar.exists() && !openJdkRtJar.isDirectory()) {
        // openjdk
        File libFile = new File(file, "lib");
        @NonNls File classesFile = openJdkRtJar.getParentFile();
        @NonNls File libExtFile = new File(openJdkRtJar.getParentFile(), "ext");
        @NonNls File libEndorsedFile = new File(libFile, "endorsed");
        jarDirs = new File[]{libEndorsedFile, libFile, classesFile, libExtFile};
      } else {
        File libFile = new File(file, "lib");
        @NonNls File classesFile = new File(file, "../Classes");
        @NonNls File libExtFile = new File(libFile, "ext");
        @NonNls File libEndorsedFile = new File(libFile, "endorsed");
        jarDirs = new File[]{libEndorsedFile, libFile, classesFile, libExtFile};
      }
    }
    else{
      @NonNls final String jre = "jre";
      File jreLibFile = isJre ? new File(file, "lib") : new File(new File(file, jre), "lib");
      @NonNls File jreLibExtFile = new File(jreLibFile, "ext");
      @NonNls File jreLibEndorsedFile = new File(jreLibFile, "endorsed");
      jarDirs = new File[]{jreLibEndorsedFile, jreLibFile, jreLibExtFile};
    }

    Set<File> filter = new LinkedHashSet<File>();
    List<File> children = new ArrayList<File>();
    for (File jarDir : jarDirs) {
      if (jarDir != null && jarDir.isDirectory()) {
        File[] jarFiles = jarDir.listFiles(jarFileFilter);
        for (File jarFile : jarFiles) {
          try {
            // File.getCanonicalFile() allows us to filter out duplicate (symbolically linked) jar files,
            // commonly found in osx JDK distributions
            if (filter.add(jarFile.getCanonicalFile())) children.add(jarFile);
          }
          catch (IOException e) {
            // Symbolic links may fail to resolve. Just skip those jars as we won't be able to find virtual file in this case anyway. 
          }
        }
      }
    }

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (File child : children) {
      String url = JarFileSystem.PROTOCOL_PREFIX + child.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (vFile != null) {
        result.add(vFile);
      }
    }
    
    @NonNls File classesZipFile = new File(new File(file, "lib"), "classes.zip");
    if(!classesZipFile.isDirectory() && classesZipFile.exists()){
      String url =
        JarFileSystem.PROTOCOL_PREFIX + classesZipFile.getAbsolutePath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
      VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(url);
      if (vFile != null){
        result.add(vFile);
      }
    }

    return VfsUtil.toVirtualFileArray(result);
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
    File srcfile = new File(file, "src");
    File jarfile = new File(file, "src.jar");
    if (!jarfile.exists()) {
      jarfile = new File(file, "src.zip");
    }

    if (jarfile.exists()) {
      VirtualFile vFile = findInJar(jarfile, "src");
      if (vFile != null) return vFile;
      // try 1.4 format
      vFile = findInJar(jarfile, "");
      return vFile;
    }
    else {
      if (!srcfile.exists() || !srcfile.isDirectory()) return null;
      String path = srcfile.getAbsolutePath().replace(File.separatorChar, '/');
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

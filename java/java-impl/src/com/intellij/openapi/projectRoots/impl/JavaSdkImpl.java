// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.lang.JavaVersion;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.model.java.impl.JavaSdkUtil;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaSdkImpl extends JavaSdk {
  private static final Logger LOG = Logger.getInstance(JavaSdkImpl.class);

  public static final DataKey<Boolean> KEY = DataKey.create("JavaSdk");

  private static final String VM_EXE_NAME = SystemInfo.isWindows ? "java.exe" : "java";  // do not use JavaW.exe because of issues with encoding

  private final Map<String, String> myCachedSdkHomeToVersionString = new ConcurrentHashMap<>();
  private final Map<String, JavaVersion> myCachedVersionStringToJdkVersion = new ConcurrentHashMap<>();

  public JavaSdkImpl() {
    super("JavaSDK");

    Disposable parentDisposable = ExtensionPointUtil.createExtensionDisposable(this, EP_NAME);
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFileContentChangeEvent || event instanceof VFileDeleteEvent) {
            updateCache(event, PathUtil.getFileName(event.getPath()));
            break;
          }
          else if (event instanceof VFileCreateEvent) {
            updateCache(event, ((VFileCreateEvent)event).getChildName());
            break;
          }
        }

      }
    });
  }

  private void updateCache(@NotNull VFileEvent event, @NotNull String fileName) {
    if (ArchiveFileType.INSTANCE.equals(FileTypeManager.getInstance().getFileTypeByFileName(fileName))) {
      String filePath = event.getPath();
      if (myCachedSdkHomeToVersionString.keySet().removeIf(sdkHome -> FileUtil.isAncestor(sdkHome, filePath, false))) {
        myCachedVersionStringToJdkVersion.clear();
      }
    }
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
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    JavaSdkVersion version = getVersion(sdk);
    int release = version != null ? version.ordinal() : 0;
    if (release > LanguageLevel.HIGHEST.toJavaVersion().feature) return "https://download.java.net/java/early_access/jdk" + release + "/docs/api/";
    if (release >= 11) return "https://docs.oracle.com/en/java/javase/" + release + "/docs/api/";
    if (release >= 6) return "https://docs.oracle.com/javase/" + release + "/docs/api/";
    if (release == 5) return "https://docs.oracle.com/javase/1.5.0/docs/api/";
    return null;
  }

  @NotNull
  @Override
  public String getDownloadSdkUrl() {
    return "https://www.oracle.com/technetwork/java/javase/downloads/index.html";
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) { }

  @NotNull
  @Override
  public Comparator<Sdk> versionComparator() {
    return (sdk1, sdk2) -> {
      assert sdk1.getSdkType() == this : sdk1;
      assert sdk2.getSdkType() == this : sdk2;
      return Comparing.compare(getJavaVersion(sdk1), getJavaVersion(sdk2));
    };
  }

  @NotNull
  @Override
  public Comparator<String> versionStringComparator() {
    return (sdk1, sdk2) -> {
      return Comparing.compare(getJavaVersion(sdk1), getJavaVersion(sdk2));
    };
  }

  @Override
  public String getBinPath(@NotNull Sdk sdk) {
    return getConvertedHomePath(sdk) + "bin";
  }

  @Override
  public String getToolsPath(@NotNull Sdk sdk) {
    JavaVersion version = getJavaVersion(sdk);
    return version == null || version.feature > 9 ? null :
           getConvertedHomePath(sdk) + "lib" + File.separator + (version.feature < 2 ? "classes.zip" : "tools.jar");
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
    return JavaHomeFinder.defaultJavaLocation();
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths() {
    return JavaHomeFinder.suggestHomePaths();
  }

  @NotNull
  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = super.getHomeChooserDescriptor();
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
    return JdkUtil.checkForJdk(path);
  }

  @Override
  public String getInvalidHomeMessage(String path) {
    if (JdkUtil.checkForJre(path)) {
      return "The selected directory points to a JRE, not a JDK.\nYou can download a JDK from " + getDownloadSdkUrl();
    }
    return super.getInvalidHomeMessage(path);
  }

  @NotNull
  @Override
  public String suggestSdkName(@Nullable String currentSdkName, String sdkHome) {
    String suggestedName = JdkUtil.suggestJdkName(getVersionString(sdkHome));
    return suggestedName != null ? suggestedName : currentSdkName != null ? currentSdkName : "";
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    setupSdkPaths(sdk);

    if (sdk.getSdkModificator().getRoots(OrderRootType.CLASSES).length == 0) {
      String title = JavaBundle.message("sdk.cannot.create");
      String message = JavaBundle.message("sdk.java.no.classes", sdk.getHomePath());
      Messages.showMessageDialog(message, title, Messages.getErrorIcon());
      return false;
    }

    return true;
  }

  @Override
  public void setupSdkPaths(@NotNull Sdk sdk) {
    String homePath = sdk.getHomePath();
    assert homePath != null : sdk;
    File jdkHome = new File(homePath);
    SdkModificator sdkModificator = sdk.getSdkModificator();

    List<String> classes = findClasses(jdkHome, false);
    Set<String> previousRoots = new LinkedHashSet<>(Arrays.asList(sdkModificator.getUrls(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    previousRoots.removeAll(new HashSet<>(classes));
    for (String url : classes) {
      sdkModificator.addRoot(url, OrderRootType.CLASSES);
    }
    for (String url : previousRoots) {
      sdkModificator.addRoot(url, OrderRootType.CLASSES);
    }

    addSources(jdkHome, sdkModificator);
    addDocs(jdkHome, sdkModificator, sdk);
    attachJdkAnnotations(sdkModificator);

    sdkModificator.commitChanges();
  }

  public static void attachJdkAnnotations(@NotNull SdkModificator modificator) {
    attachIDEAAnnotationsToJdk(modificator);
  }

  // return true on success
  public static boolean attachIDEAAnnotationsToJdk(@NotNull SdkModificator modificator) {
    List<String> pathsChecked = new ArrayList<>();
    VirtualFile root = internalJdkAnnotationsPath(pathsChecked);
    if (root != null && !isInternalJdkAnnotationRootCorrect(root)) {
      root = null;
    }
    if (root == null) {
      String msg = "Paths checked:\n";
      for (String p : pathsChecked) {
        File f = new File(p);
        //noinspection StringConcatenationInLoop yeah I know, it's more readable this way
        msg += p + "; exists: " + f.exists() + "; siblings: " + Arrays.toString(f.getParentFile().list()) + "\n";
      }
      LOG.error("JDK annotations not found", msg);
      return false;
    }

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    if (modificator.getRoots(annoType).length != 0) {
      modificator.removeRoot(root, annoType);
    }
    modificator.addRoot(root, annoType);
    return true;
  }

  // does this file look like the genuine root for all correct annotations.xml
  private static boolean isInternalJdkAnnotationRootCorrect(@NotNull VirtualFile root) {
    String relPath = "java/awt/event/annotations.xml";
    VirtualFile xml = root.findFileByRelativePath(relPath);
    if (xml == null) {
      reportCorruptedJdkAnnotations(root, "there's no file " + root.getPath() + "/" + relPath);
      return false;
    }
    MostlySingularMultiMap<String, BaseExternalAnnotationsManager.AnnotationData> loaded = BaseExternalAnnotationsManager.loadData(xml, LoadTextUtil.loadText(xml), null);
    Iterable<BaseExternalAnnotationsManager.AnnotationData> data = loaded.get("java.awt.event.InputEvent int getModifiers()");

    BaseExternalAnnotationsManager.AnnotationData magicAnno = ContainerUtil.find(data, ann -> ann.toString().equals(MagicConstant.class.getName() + "(flagsFromClass=java.awt.event.InputEvent.class)"));
    if (magicAnno != null) {
      return true;
    }
    reportCorruptedJdkAnnotations(root, "java.awt.event.InputEvent.getModifiers() not annotated with MagicConstant: "+data);
    return false;
  }

  private static void reportCorruptedJdkAnnotations(@NotNull VirtualFile root, @NotNull String reason) {
    LOG.warn("Internal jdk annotation root " + root + " seems corrupted: " + reason);
  }

  static VirtualFile internalJdkAnnotationsPath(@NotNull List<? super String> pathsChecked) {
    String javaPluginClassesRootPath = PathManager.getJarPathForClass(JavaSdkImpl.class);
    LOG.assertTrue(javaPluginClassesRootPath != null);
    File javaPluginClassesRoot = new File(javaPluginClassesRootPath);
    VirtualFile root;
    if (javaPluginClassesRoot.isFile()) {
      String annotationsJarPath = FileUtil.toSystemIndependentName(new File(javaPluginClassesRoot.getParentFile(), "jdkAnnotations.jar").getAbsolutePath());
      root = VirtualFileManager.getInstance().findFileByUrl("jar://" + annotationsJarPath + "!/");
      pathsChecked.add(annotationsJarPath);
    }
    else {
      // when run against IDEA plugin JDK, something like this comes up: "$IDEA_HOME$/out/classes/production/intellij.java.impl"
      File projectRoot = JBIterable.generate(javaPluginClassesRoot, File::getParentFile).get(4);
      File root1 = new File(projectRoot, "community/java/jdkAnnotations");
      File root2 = new File(projectRoot, "java/jdkAnnotations");
      root = root1.exists() && root1.isDirectory() ? LocalFileSystem.getInstance().findFileByIoFile(root1) :
      root2.exists() && root2.isDirectory() ? LocalFileSystem.getInstance().findFileByIoFile(root2) : null;
    }
    if (root == null) {
      String url = "jar://" + FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/jdkAnnotations.jar!/";
      root = VirtualFileManager.getInstance().findFileByUrl(url);
      pathsChecked.add(url);
    }
    if (root == null) {
      // community idea under idea
      String path = FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath()) + "/java/jdkAnnotations";
      root = LocalFileSystem.getInstance().findFileByPath(path);
      pathsChecked.add(path);
    }
    return root;
  }

  @Override
  public final String getVersionString(String sdkHome) {
    return myCachedSdkHomeToVersionString.computeIfAbsent(sdkHome, homePath -> {
      JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(homePath);
      return jdkInfo != null ? JdkVersionDetector.formatVersionString(jdkInfo.version) : null;
    });
  }

  @Override
  public JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    JavaVersion version = getJavaVersion(sdk);
    return version != null ? JavaSdkVersion.fromJavaVersion(version) : null;
  }

  @Nullable
  public JavaVersion getJavaVersion(@NotNull Sdk sdk) {
    String versionString = sdk.getVersionString();
    return getJavaVersion(versionString);
  }

  @Nullable
  private JavaVersion getJavaVersion(@Nullable String versionString) {
    return versionString != null
           ? myCachedVersionStringToJdkVersion.computeIfAbsent(versionString, JavaVersion::tryParse)
           : null;
  }

  @Override
  @Nullable
  public JavaSdkVersion getVersion(@NotNull String versionString) {
    return JavaSdkVersion.fromVersionString(versionString);
  }

  @Override
  public boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version) {
    JavaSdkVersion sdkVersion = getVersion(sdk);
    return sdkVersion != null && sdkVersion.isAtLeast(version);
  }

  @NotNull
  @Override
  public Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre) {
    File jdkHomeFile = new File(home);
    if (!jdkHomeFile.exists()) {
      throw new IllegalArgumentException(jdkHomeFile.getAbsolutePath() + " doesn't exist");
    }
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    sdkModificator.setHomePath(FileUtil.toSystemIndependentName(home));
    if (JdkVersionDetector.isVersionString(jdkName)) {
      sdkModificator.setVersionString(jdkName);  // must be set after home path, otherwise setting home path clears the version string
    }

    addClasses(jdkHomeFile, sdkModificator, isJre);
    addSources(jdkHomeFile, sdkModificator);
    addDocs(jdkHomeFile, sdkModificator, null);
    attachJdkAnnotations(sdkModificator);

    sdkModificator.commitChanges();

    return jdk;
  }

  @ApiStatus.Internal
  public static void addClasses(@NotNull File file, @NotNull SdkModificator sdkModificator, boolean isJre) {
    for (String url : findClasses(file, isJre)) {
      sdkModificator.addRoot(url, OrderRootType.CLASSES);
    }
  }

  /**
   * Tries to load the list of modules in the JDK from the 'release' file. Returns null if the 'release' file is not there
   * or doesn't contain the expected information.
   */
  @Nullable
  private static List<String> readModulesFromReleaseFile(File jrtBaseDir) {
    File releaseFile = new File(jrtBaseDir, "release");
    if (releaseFile.isFile()) {
      try (FileInputStream stream = new FileInputStream(releaseFile)) {
        Properties p = new Properties();
        p.load(stream);
        String modules = p.getProperty("MODULES");
        if (modules != null) {
          return StringUtil.split(StringUtil.unquoteString(modules), " ");
        }
      }
      catch (IOException | IllegalArgumentException e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @NotNull
  private static List<String> findClasses(@NotNull File jdkHome, boolean isJre) {
    List<String> result = new ArrayList<>();

    if (JdkUtil.isExplodedModularRuntime(jdkHome.getPath())) {
      File[] exploded = new File(jdkHome, "modules").listFiles();
      if (exploded != null) {
        for (File root : exploded) {
          result.add(VfsUtil.getUrlForLibraryRoot(root));
        }
      }
    }
    else if (JdkUtil.isModularRuntime(jdkHome)) {
      String jrtBaseUrl = JrtFileSystem.PROTOCOL_PREFIX + getPath(jdkHome) + JrtFileSystem.SEPARATOR;
      List<String> modules = readModulesFromReleaseFile(jdkHome);
      if (modules != null) {
        for (String module : modules) {
          result.add(jrtBaseUrl + module);
        }
      }
      else {
        VirtualFile jrt = VirtualFileManager.getInstance().findFileByUrl(jrtBaseUrl);
        if (jrt != null) {
          for (VirtualFile virtualFile : jrt.getChildren()) {
            result.add(virtualFile.getUrl());
          }
        }
      }
    }
    else {
      for (File root : JavaSdkUtil.getJdkClassesRoots(jdkHome, isJre)) {
        result.add(VfsUtil.getUrlForLibraryRoot(root));
      }
    }

    Collections.sort(result);
    return result;
  }

  @ApiStatus.Internal
  public static void addSources(@NotNull File jdkHome, @NotNull SdkModificator sdkModificator) {
    VirtualFile jdkSrc = findSources(jdkHome, "src");
    if (jdkSrc != null) {
      if (jdkSrc.findChild("java.base") != null) {
        Stream.of(jdkSrc.getChildren())
          .filter(VirtualFile::isDirectory)
          .forEach(root -> sdkModificator.addRoot(root, OrderRootType.SOURCES));
      }
      else {
        sdkModificator.addRoot(jdkSrc, OrderRootType.SOURCES);
      }
    }

    VirtualFile fxSrc = findSources(jdkHome, "javafx-src");
    if (fxSrc != null) {
      sdkModificator.addRoot(fxSrc, OrderRootType.SOURCES);
    }
  }

  @Nullable
  private static VirtualFile findSources(File jdkHome, String srcName) {
    File srcArc = new File(jdkHome, srcName + ".jar");
    if (!srcArc.exists()) srcArc = new File(jdkHome, srcName + ".zip");
    if (!srcArc.exists()) srcArc = new File(jdkHome, "lib/" + srcName + ".zip");
    if (srcArc.exists()) {
      VirtualFile srcRoot = findInJar(srcArc, "src");
      if (srcRoot == null) srcRoot = findInJar(srcArc, "");
      return srcRoot;
    }

    File srcDir = new File(jdkHome, "src");
    if (srcDir.isDirectory()) {
      return LocalFileSystem.getInstance().findFileByPath(getPath(srcDir));
    }

    return null;
  }

  private void addDocs(File jdkHome, SdkModificator sdkModificator, @Nullable Sdk sdk) {
    OrderRootType docRootType = JavadocOrderRootType.getInstance();

    VirtualFile apiDocs = findDocs(jdkHome, "docs/api");
    if (apiDocs != null) {
      sdkModificator.addRoot(apiDocs, docRootType);
    }
    else if (SystemInfo.isMac) {
      VirtualFile commonDocs = findDocs(jdkHome, "docs");
      if (commonDocs == null) commonDocs = findInJar(new File(jdkHome, "docs.jar"), "doc/api");
      if (commonDocs == null) commonDocs = findInJar(new File(jdkHome, "docs.jar"), "docs/api");
      if (commonDocs != null) {
        sdkModificator.addRoot(commonDocs, docRootType);
      }

      VirtualFile appleDocs = findDocs(jdkHome, "appledocs");
      if (appleDocs == null) appleDocs = findInJar(new File(jdkHome, "appledocs.jar"), "appledoc/api");
      if (appleDocs != null) {
        sdkModificator.addRoot(appleDocs, docRootType);
      }
    }

    if (sdk != null && sdkModificator.getRoots(docRootType).length == 0 && sdkModificator.getRoots(OrderRootType.SOURCES).length == 0) {
      // registers external docs when both sources and local docs are missing
      String docUrl = getDefaultDocumentationUrl(sdk);
      if (docUrl != null) {
        VirtualFile onlineDoc = VirtualFileManager.getInstance().findFileByUrl(docUrl);
        if (onlineDoc != null) {
          sdkModificator.addRoot(onlineDoc, docRootType);
        }
      }

      if (getVersion(sdk) == JavaSdkVersion.JDK_1_7) {
        VirtualFile fxDocUrl = VirtualFileManager.getInstance().findFileByUrl("https://docs.oracle.com/javafx/2/api/");
        if (fxDocUrl != null) {
          sdkModificator.addRoot(fxDocUrl, docRootType);
        }
      }
    }
  }

  @Nullable
  private static VirtualFile findDocs(@NotNull File jdkHome, @NotNull String relativePath) {
    File docDir = new File(jdkHome.getAbsolutePath(), relativePath);
    return docDir.isDirectory() ? LocalFileSystem.getInstance().findFileByPath(getPath(docDir)) : null;
  }

  private static VirtualFile findInJar(File jarFile, String relativePath) {
    if (!jarFile.exists()) return null;
    String url = JarFileSystem.PROTOCOL_PREFIX + getPath(jarFile) + JarFileSystem.JAR_SEPARATOR + relativePath;
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  private static String getPath(File jarFile) {
    return FileUtil.toSystemIndependentName(jarFile.getAbsolutePath());
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.codeInsight.BaseExternalAnnotationsManager;
import com.intellij.execution.wsl.WslDistributionManager;
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final Map<String, JdkVersionDetector.JdkVersionInfo> myCachedSdkHomeToInfo = new ConcurrentHashMap<>();
  private final Map<String, JavaVersion> myCachedVersionStringToJdkVersion = new ConcurrentHashMap<>();

  public JavaSdkImpl() {
    super("JavaSDK");

    Disposable parentDisposable = ExtensionPointUtil.createExtensionDisposable(this, EP_NAME);
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
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
      if (myCachedSdkHomeToInfo.keySet().removeIf(sdkHome -> FileUtil.isAncestor(sdkHome, filePath, false))) {
        myCachedVersionStringToJdkVersion.clear();
      }
    }
  }

  @Override
  public @NotNull String getPresentableName() {
    return ProjectBundle.message("sdk.java.name");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.PpJdk;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "reference.project.structure.sdk.java";
  }

  @Override
  public @Nullable String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    JavaSdkVersion version = getVersion(sdk);
    int release = version != null ? version.ordinal() : 0;
    if (release > LanguageLevel.HIGHEST.toJavaVersion().feature) return "https://download.java.net/java/early_access/jdk" + release + "/docs/api/";
    if (release >= 11) return "https://docs.oracle.com/en/java/javase/" + release + "/docs/api/";
    if (release >= 6) return "https://docs.oracle.com/javase/" + release + "/docs/api/";
    if (release == 5) return "https://docs.oracle.com/javase/1.5.0/docs/api/";
    return null;
  }

  @Override
  public @NotNull String getDownloadSdkUrl() {
    return "https://www.oracle.com/technetwork/java/javase/downloads/index.html";
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) { }

  @Override
  public @NotNull Comparator<Sdk> versionComparator() {
    return (sdk1, sdk2) -> {
      assert sdk1.getSdkType() == this : sdk1;
      assert sdk2.getSdkType() == this : sdk2;
      return Comparing.compare(getJavaVersion(sdk1), getJavaVersion(sdk2));
    };
  }

  @Override
  public @NotNull Comparator<String> versionStringComparator() {
    return (sdk1, sdk2) -> Comparing.compare(getJavaVersion(sdk1), getJavaVersion(sdk2));
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
    String binPath = getBinPath(sdk);
    if (binPath.startsWith(WslConstants.UNC_PREFIX)) {
      return binPath + "/java";
    }
    return binPath + File.separator + VM_EXE_NAME;
  }

  @NotNull
  private static String getConvertedHomePath(@NotNull Sdk sdk) {
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

  @Override
  public @NotNull Collection<String> suggestHomePaths() {
    return JavaHomeFinder.suggestHomePaths();
  }

  @Override
  public @NotNull FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = super.getHomeChooserDescriptor();
    descriptor.putUserData(KEY, Boolean.TRUE);
    return descriptor;
  }

  @Override
  public @NotNull String adjustSelectedSdkHome(@NotNull String homePath) {
    if (SystemInfo.isMac) {
      Path home = Path.of(homePath, "/Home");
      if (Files.exists(home)) return home.toString();

      home = Path.of(homePath, "Contents/Home");
      if (Files.exists(home)) return home.toString();
    }

    return homePath;
  }

  @Override
  public boolean isValidSdkHome(@NotNull String path) {
    return JdkUtil.checkForJdk(path);
  }

  @Override
  public String getInvalidHomeMessage(@NotNull String path) {
    if (JdkUtil.checkForJre(path)) {
      return "The selected directory points to a JRE, not a JDK.\nYou can download a JDK from " + getDownloadSdkUrl();
    }
    return super.getInvalidHomeMessage(path);
  }

  @Override
  public @NotNull String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome) {
    JdkVersionDetector.JdkVersionInfo info = getInfo(sdkHome);
    if (info == null) return currentSdkName != null ? currentSdkName : "";

    String vendorPrefix = Registry.is("use.jdk.vendor.in.suggested.jdk.name", true) ? info.variant.prefix : null;
    String name = JdkUtil.suggestJdkName(info.version, vendorPrefix);
    if (WslDistributionManager.isWslPath(sdkHome)) name += " (WSL)";
    return name;
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
    Path jdkHome = Path.of(homePath);
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
    VirtualFile root = internalJdkAnnotationsPath(pathsChecked, false);
    if (root != null && !isInternalJdkAnnotationRootCorrect(root)) {
      root = null;
    }
    if (root == null) {
      StringBuilder msg = new StringBuilder("Paths checked:\n");
      for (String path : pathsChecked) {
        File file = new File(path);
        msg.append(path).append("; exists: ").append(file.exists());
        File parentFile = file.getParentFile();
        if (parentFile != null) {
          msg.append("; siblings: ").append(Arrays.toString(parentFile.list())).append('\n');
        }
      }
      LOG.error("JDK annotations not found", msg.toString());
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
  private static boolean isInternalJdkAnnotationRootCorrect(VirtualFile root) {
    String relPath = "java/awt/event/annotations.xml";
    VirtualFile xml = root.findFileByRelativePath(relPath);
    if (xml == null) {
      reportCorruptedJdkAnnotations(root, "there's no file " + root.getPath() + "/" + relPath);
      return false;
    }
    MostlySingularMultiMap<String, BaseExternalAnnotationsManager.AnnotationData> loaded =
      BaseExternalAnnotationsManager.loadData(xml, LoadTextUtil.loadText(xml), null);
    Iterable<BaseExternalAnnotationsManager.AnnotationData> data = loaded.get("java.awt.event.InputEvent int getModifiers()");
    BaseExternalAnnotationsManager.AnnotationData magicAnno =
      ContainerUtil.find(data, ann -> ann.toString().equals(MagicConstant.class.getName() + "(flagsFromClass=java.awt.event.InputEvent.class)"));
    if (magicAnno != null) return true;
    reportCorruptedJdkAnnotations(root, "java.awt.event.InputEvent.getModifiers() not annotated with MagicConstant: "+data);
    return false;
  }

  private static void reportCorruptedJdkAnnotations(@NotNull VirtualFile root, @NotNull @NlsSafe String reason) {
    LOG.warn("Internal jdk annotation root " + root + " seems corrupted: " + reason);
  }

  static VirtualFile internalJdkAnnotationsPath(@NotNull List<? super String> pathsChecked, boolean refresh) {
    Path javaPluginClassesRootPath = PathManager.getJarForClass(JavaSdkImpl.class);
    LOG.assertTrue(javaPluginClassesRootPath != null);
    javaPluginClassesRootPath = javaPluginClassesRootPath.toAbsolutePath();
    VirtualFile root;
    VirtualFileManager vfm = VirtualFileManager.getInstance();
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    String pathInResources = "resources/jdkAnnotations.jar";
    if (Files.isRegularFile(javaPluginClassesRootPath)) {
      Path annotationsJarPath = javaPluginClassesRootPath.resolveSibling(pathInResources);
      String annotationsJarPathString = FileUtil.toSystemIndependentName(annotationsJarPath.toString());
      String url = "jar://" + annotationsJarPathString + "!/";
      root = refresh ? vfm.refreshAndFindFileByUrl(url) : vfm.findFileByUrl(url);
      pathsChecked.add(annotationsJarPathString);
    }
    else {
      // when run against IDEA plugin JDK, something like this comes up: "$IDEA_HOME$/out/classes/production/intellij.java.impl"
      Path projectRoot = JBIterable.generate(javaPluginClassesRootPath, Path::getParent).get(4);
      if (projectRoot != null) {
        Path root1 = projectRoot.resolve("community/java/jdkAnnotations");
        Path root2 = projectRoot.resolve("java/jdkAnnotations");
        root = Files.isDirectory(root1) ? (refresh ? lfs.refreshAndFindFileByNioFile(root1) : lfs.findFileByNioFile(root1)) :
               Files.isDirectory(root2) ? (refresh ? lfs.refreshAndFindFileByNioFile(root2) : lfs.findFileByNioFile(root2)) : null;
      }
      else {
        root = null;
      }
    }
    if (root == null) {
      String url = "jar://" + FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/lib/" + pathInResources + "!/";
      root = refresh ? vfm.refreshAndFindFileByUrl(url) : vfm.findFileByUrl(url);
      pathsChecked.add(url);
    }
    if (root == null) {
      // community idea under idea
      String path = FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath()) + "/java/jdkAnnotations";
      root = refresh ? lfs.refreshAndFindFileByPath(path) : lfs.findFileByPath(path);
      pathsChecked.add(path);
    }
    if (root == null && !refresh) {
      pathsChecked.add("<refresh is on now>");
      root = internalJdkAnnotationsPath(pathsChecked, true);
    }
    return root;
  }

  private @Nullable JdkVersionDetector.JdkVersionInfo getInfo(String sdkHome) {
    return myCachedSdkHomeToInfo.computeIfAbsent(sdkHome, homePath -> SdkVersionUtil.getJdkVersionInfo(homePath));
  }

  @Override
  public String getVersionString(String sdkHome) {
    var info = getInfo(sdkHome);
    if (info == null) return null;
    return info.displayVersionString();
  }

  @Override
  public JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    JavaVersion version = getJavaVersion(sdk);
    return version != null ? JavaSdkVersion.fromJavaVersion(version) : null;
  }

  public @Nullable JavaVersion getJavaVersion(@NotNull Sdk sdk) {
    return getJavaVersion(sdk.getVersionString());
  }

  private @Nullable JavaVersion getJavaVersion(@Nullable String versionString) {
    return versionString != null ? myCachedVersionStringToJdkVersion.computeIfAbsent(versionString, JavaVersion::tryParse) : null;
  }

  @Override
  public boolean isOfVersionOrHigher(@NotNull Sdk sdk, @NotNull JavaSdkVersion version) {
    JavaSdkVersion sdkVersion = getVersion(sdk);
    return sdkVersion != null && sdkVersion.isAtLeast(version);
  }

  @Override
  public @NotNull Sdk createJdk(@NotNull String jdkName, @NotNull String home, boolean isJre) {
    Path jdkHomePath = Path.of(home);
    if (!Files.exists(jdkHomePath)) {
      throw new IllegalArgumentException(jdkHomePath.toAbsolutePath() + " doesn't exist");
    }
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    sdkModificator.setHomePath(FileUtil.toSystemIndependentName(home));
    if (JdkVersionDetector.isVersionString(jdkName)) {
      sdkModificator.setVersionString(jdkName);  // must be set after home path, otherwise setting home path clears the version string
    }

    addClasses(jdkHomePath, sdkModificator, isJre);
    addSources(jdkHomePath, sdkModificator);
    addDocs(jdkHomePath, sdkModificator, null);
    attachJdkAnnotations(sdkModificator);

    sdkModificator.commitChanges();

    return jdk;
  }

  @ApiStatus.Internal
  public static void addClasses(@NotNull Path file, @NotNull SdkModificator sdkModificator, boolean isJre) {
    for (String url : findClasses(file, isJre)) {
      sdkModificator.addRoot(url, OrderRootType.CLASSES);
    }
  }

  /**
   * Tries to load the list of modules in the JDK from the 'release' file. Returns null if the 'release' file is not there
   * or doesn't contain the expected information.
   */
  private static @Nullable List<String> readModulesFromReleaseFile(Path jrtBaseDir) {
    try (InputStream stream = Files.newInputStream(jrtBaseDir.resolve("release"))) {
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
    return null;
  }

  private static List<String> findClasses(Path jdkHome, boolean isJre) {
    List<String> result = new ArrayList<>();

    if (JdkUtil.isExplodedModularRuntime(jdkHome)) {
      try {
        try (DirectoryStream<Path> roots = Files.newDirectoryStream(jdkHome.resolve("modules"))) {
          for (Path root : roots) {
            result.add(VfsUtil.getUrlForLibraryRoot(root));
          }
        }
      }
      catch (IOException ignore) { }
    }
    else if (JdkUtil.isModularRuntime(jdkHome)) {
      String jrtBaseUrl = JrtFileSystem.PROTOCOL_PREFIX + vfsPath(jdkHome) + JrtFileSystem.SEPARATOR;
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
      for (Path root : JavaSdkUtil.getJdkClassesRoots(jdkHome, isJre)) {
        result.add(VfsUtil.getUrlForLibraryRoot(root));
      }
    }

    Collections.sort(result);
    return result;
  }

  @ApiStatus.Internal
  public static void addSources(@NotNull Path jdkHome, @NotNull SdkModificator sdkModificator) {
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

  private static @Nullable VirtualFile findSources(Path jdkHome, String srcName) {
    Path srcArc = jdkHome.resolve(srcName + ".jar");
    if (!Files.exists(srcArc)) srcArc = jdkHome.resolve(srcName + ".zip");
    if (!Files.exists(srcArc)) srcArc = jdkHome.resolve("lib").resolve(srcName + ".zip");
    if (Files.exists(srcArc)) {
      VirtualFile srcRoot = findInJar(srcArc, "src");
      if (srcRoot == null) srcRoot = findInJar(srcArc, "");
      return srcRoot;
    }

    Path srcDir = jdkHome.resolve("src");
    return Files.isDirectory(srcDir) ? LocalFileSystem.getInstance().findFileByNioFile(srcDir) : null;
  }

  private void addDocs(Path jdkHome, SdkModificator sdkModificator, @Nullable Sdk sdk) {
    OrderRootType docRootType = JavadocOrderRootType.getInstance();

    VirtualFile apiDocs = findDocs(jdkHome, "docs/api");
    if (apiDocs != null) {
      sdkModificator.addRoot(apiDocs, docRootType);
    }
    else if (SystemInfo.isMac) {
      VirtualFile commonDocs = findDocs(jdkHome, "docs");
      if (commonDocs == null) commonDocs = findInJar(jdkHome.resolve("docs.jar"), "doc/api");
      if (commonDocs == null) commonDocs = findInJar(jdkHome.resolve("docs.jar"), "docs/api");
      if (commonDocs != null) {
        sdkModificator.addRoot(commonDocs, docRootType);
      }

      VirtualFile appleDocs = findDocs(jdkHome, "appledocs");
      if (appleDocs == null) appleDocs = findInJar(jdkHome.resolve("appledocs.jar"), "appledoc/api");
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

  private static @Nullable VirtualFile findDocs(Path jdkHome, String relativePath) {
    Path docDir = jdkHome.resolve(relativePath);
    return Files.isDirectory(docDir) ? LocalFileSystem.getInstance().findFileByNioFile(docDir) : null;
  }

  private static @Nullable VirtualFile findInJar(Path jarFile, String relativePath) {
    if (!Files.exists(jarFile)) return null;
    String url = JarFileSystem.PROTOCOL_PREFIX + vfsPath(jarFile) + JarFileSystem.JAR_SEPARATOR + relativePath;
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  private static String vfsPath(Path path) {
    return FileUtil.toSystemIndependentName(path.toAbsolutePath().toString());
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES ||
           type == OrderRootType.SOURCES ||
           type == JavadocOrderRootType.getInstance() ||
           type == AnnotationOrderRootType.getInstance();
  }
}

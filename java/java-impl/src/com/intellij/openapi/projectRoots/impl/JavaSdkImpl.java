/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.lang.JavaVersion;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.jetbrains.jps.model.java.impl.JavaSdkUtil;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * @author Eugene Zhuravlev
 * @since Sep 17, 2004
 */
public class JavaSdkImpl extends JavaSdk {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.JavaSdkImpl");

  public static final DataKey<Boolean> KEY = DataKey.create("JavaSdk");

  private static final String VM_EXE_NAME = "java";   // do not use JavaW.exe for Windows because of issues with encoding

  private final Map<String, String> myCachedSdkHomeToVersionString = new ConcurrentHashMap<>();
  private final Map<String, JavaSdkVersion> myCachedVersionStringToJdkVersion = new ConcurrentHashMap<>();

  public JavaSdkImpl(final VirtualFileManager fileManager, final FileTypeManager fileTypeManager) {
    super("JavaSDK");

    fileManager.addVirtualFileListener(new VirtualFileListener() {
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
        if (FileTypes.ARCHIVE.equals(fileTypeManager.getFileTypeByFileName(event.getFileName()))) {
          String filePath = event.getFile().getPath();
          if (myCachedSdkHomeToVersionString.keySet().removeIf(sdkHome -> FileUtil.isAncestor(sdkHome, filePath, false))) {
            myCachedVersionStringToJdkVersion.clear();
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
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    JavaSdkVersion version = getVersion(sdk);
    if (version != null) {
      if (version == JavaSdkVersion.JDK_1_5) {
        return "https://docs.oracle.com/javase/1.5.0/docs/api/";
      }
      if (version == JavaSdkVersion.JDK_10) {
        return "https://download.java.net/java/jdk10/docs/api/";
      }
      if (version.isAtLeast(JavaSdkVersion.JDK_1_6)) {
        return "https://docs.oracle.com/javase/" + version.ordinal() + "/docs/api/";
      }
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
  public void saveAdditionalData(@NotNull SdkAdditionalData additionalData, @NotNull Element additional) { }

  @Override
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
    Collection<String> paths = suggestHomePaths();
    return paths.isEmpty() ? null : paths.iterator().next();
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
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    JavaVersion version = JavaVersion.tryParse(getVersionString(sdkHome));
    if (version == null) return currentSdkName;

    StringBuilder suggested = new StringBuilder();
    if (version.feature < 9) suggested.append("1.");
    suggested.append(version.feature);
    if (version.ea) suggested.append("-ea");
    return suggested.toString();
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    setupSdkPaths(sdk);

    if (sdk.getSdkModificator().getRoots(OrderRootType.CLASSES).length == 0) {
      String title = ProjectBundle.message("sdk.cannot.create");
      String message = ProjectBundle.message("sdk.java.no.classes", sdk.getHomePath());
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

    List<VirtualFile> classes = findClasses(jdkHome, false);
    Set<VirtualFile> previousRoots = new LinkedHashSet<>(Arrays.asList(sdkModificator.getRoots(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    previousRoots.removeAll(new HashSet<>(classes));
    for (VirtualFile aClass : classes) {
      sdkModificator.addRoot(aClass, OrderRootType.CLASSES);
    }
    for (VirtualFile root : previousRoots) {
      sdkModificator.addRoot(root, OrderRootType.CLASSES);
    }

    addSources(jdkHome, sdkModificator);
    addDocs(jdkHome, sdkModificator, sdk);
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
      StringBuilder msg = new StringBuilder("Paths checked:\n");
      for (String p : pathsChecked) {
        File f = new File(p);
        msg.append(p).append("; ").append(f.exists()).append("; ").append(Arrays.toString(f.getParentFile().list())).append('\n');
      }
      LOG.error("JDK annotations not found", msg.toString());
      return;
    }

    OrderRootType annoType = AnnotationOrderRootType.getInstance();
    modificator.removeRoot(root, annoType);
    modificator.addRoot(root, annoType);
  }

  @Override
  public final String getVersionString(String sdkHome) {
    return myCachedSdkHomeToVersionString.computeIfAbsent(sdkHome, k -> {
      JdkVersionDetector.JdkVersionInfo jdkInfo = SdkVersionUtil.getJdkVersionInfo(k);
      return jdkInfo != null ? JdkVersionDetector.formatVersionString(jdkInfo.version) : null;
    });
  }

  @Override
  public JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    String versionString = sdk.getVersionString();
    return versionString == null ? null :
           myCachedVersionStringToJdkVersion.computeIfAbsent(versionString, JavaSdkVersion::fromVersionString);
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
    ProjectJdkImpl jdk = new ProjectJdkImpl(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string

    File jdkHomeFile = new File(home);
    addClasses(jdkHomeFile, sdkModificator, isJre);
    addSources(jdkHomeFile, sdkModificator);
    addDocs(jdkHomeFile, sdkModificator, null);
    sdkModificator.commitChanges();

    return jdk;
  }

  @NotNull
  @TestOnly
  public Sdk createMockJdk(@NotNull String jdkName, @NotNull String home, boolean isJre) {
    String homePath = PathUtil.toSystemIndependentName(home);
    File jdkHomeFile = new File(homePath);

    MultiMap<OrderRootType, VirtualFile> roots = MultiMap.create();
    SdkModificator sdkModificator = new SdkModificator() {
      @Override public String getName() { throw new UnsupportedOperationException(); }
      @Override public void setName(String name) { throw new UnsupportedOperationException(); }
      @Override public String getHomePath() { throw new UnsupportedOperationException(); }
      @Override public void setHomePath(String path) { throw new UnsupportedOperationException(); }
      @Override public String getVersionString() { throw new UnsupportedOperationException(); }
      @Override public void setVersionString(String versionString) { throw new UnsupportedOperationException(); }
      @Override public SdkAdditionalData getSdkAdditionalData() { throw new UnsupportedOperationException(); }
      @Override public void setSdkAdditionalData(SdkAdditionalData data) { throw new UnsupportedOperationException(); }
      @NotNull
      @Override public VirtualFile[] getRoots(@NotNull OrderRootType rootType) { throw new UnsupportedOperationException(); }
      @Override public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) { throw new UnsupportedOperationException(); }
      @Override public void removeRoots(@NotNull OrderRootType rootType) { throw new UnsupportedOperationException(); }
      @Override public void removeAllRoots() { throw new UnsupportedOperationException(); }
      @Override public void commitChanges() { throw new UnsupportedOperationException(); }
      @Override public boolean isWritable() { throw new UnsupportedOperationException(); }

      @Override
      public void addRoot(@NotNull VirtualFile root, @NotNull OrderRootType rootType) {
        roots.putValue(rootType, root);
      }
    };

    addClasses(jdkHomeFile, sdkModificator, isJre);
    addSources(jdkHomeFile, sdkModificator);

    return new MockSdk(jdkName, homePath, jdkName, roots, this);
  }

  private static void addClasses(@NotNull File file, @NotNull SdkModificator sdkModificator, boolean isJre) {
    for (VirtualFile virtualFile : findClasses(file, isJre)) {
      sdkModificator.addRoot(virtualFile, OrderRootType.CLASSES);
    }
  }

  @NotNull
  private static List<VirtualFile> findClasses(@NotNull File file, boolean isJre) {
    List<VirtualFile> result = ContainerUtil.newArrayList();
    VirtualFileManager fileManager = VirtualFileManager.getInstance();

    if (JdkUtil.isExplodedModularRuntime(file.getPath())) {
      VirtualFile exploded = fileManager.findFileByUrl(StandardFileSystems.FILE_PROTOCOL_PREFIX + getPath(new File(file, "modules")));
      if (exploded != null) {
        ContainerUtil.addAll(result, exploded.getChildren());
      }
    }
    else if (JdkUtil.isModularRuntime(file)) {
      VirtualFile jrt = fileManager.findFileByUrl(JrtFileSystem.PROTOCOL_PREFIX + getPath(file) + JrtFileSystem.SEPARATOR);
      if (jrt != null) {
        ContainerUtil.addAll(result, jrt.getChildren());
      }
    }
    else {
      for (File root : JavaSdkUtil.getJdkClassesRoots(file, isJre)) {
        String url = VfsUtil.getUrlForLibraryRoot(root);
        ContainerUtil.addIfNotNull(result, fileManager.findFileByUrl(url));
      }
    }

    Collections.sort(result, Comparator.comparing(VirtualFile::getPath));

    return result;
  }

  private static void addSources(@NotNull File jdkHome, @NotNull SdkModificator sdkModificator) {
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
        VirtualFile fxDocUrl = VirtualFileManager.getInstance().findFileByUrl("http://docs.oracle.com/javafx/2/api/");
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
/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.DeploymentUtilImpl;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.io.ZipUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author nik
 */
public class JarsBuilder {
  private final Set<JarInfo> myJarsToBuild;
  private final FileFilter myFileFilter;
  private final CompileContext myContext;
  private Map<JarInfo, File> myBuiltJars;
  private MultiValuesMap<JarInfo, Pair<String, JarInfo>> myNestedJars;
  private List<ExplodedDestinationInfo> myJarsDestinations;
  private Set<File> myJarsToDelete;

  public JarsBuilder(Set<JarInfo> jarsToBuild, FileFilter fileFilter, CompileContext context) {
    myJarsToBuild = jarsToBuild;
    myFileFilter = fileFilter;
    myContext = context;
    myJarsDestinations = new ArrayList<ExplodedDestinationInfo>();
  }

  public boolean buildJars(Set<String> writtenPaths) throws IOException {
    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.building.archives"));

    addDependentJars();

    final JarInfo[] sortedJars = sortJars();
    if (sortedJars == null) {
      return false;
    }

    computeNestedJars();

    myBuiltJars = new HashMap<JarInfo, File>();
    for (JarInfo jar : sortedJars) {
      myContext.getProgressIndicator().checkCanceled();
      buildJar(jar);
    }

    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.copying.archives"));
    copyJars(writtenPaths);

    deleteTemporaryJars();

    return true;
  }

  private void deleteTemporaryJars() {
    for (File file : myJarsToDelete) {
      deleteFile(file);
    }
  }

  protected void deleteFile(final File file) {
    FileUtil.delete(file);
  }

  public List<ExplodedDestinationInfo> getJarsDestinations() {
    return myJarsDestinations;
  }

  private void copyJars(final Set<String> writtenPaths) throws IOException {
    myJarsToDelete = new HashSet<File>(myBuiltJars.values());

    for (Map.Entry<JarInfo, File> entry : myBuiltJars.entrySet()) {
      File fromFile = entry.getValue();
      boolean first = true;
      for (DestinationInfo destination : entry.getKey().getAllDestinations()) {
        if (destination instanceof ExplodedDestinationInfo) {
          myJarsDestinations.add((ExplodedDestinationInfo)destination);
          File toFile = new File(FileUtil.toSystemDependentName(destination.getOutputPath()));

          if (first ) {
            first = false;
            renameFile(fromFile, toFile, writtenPaths);
            fromFile = toFile;
          }
          else {
            copyFile(fromFile, toFile, writtenPaths);
          }

        }
      }
    }
  }

  protected void renameFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
    FileUtil.rename(fromFile, toFile);
    writtenPaths.add(toFile.getPath());
  }

  protected void copyFile(final File fromFile, final File toFile, final Set<String> writtenPaths) throws IOException {
    DeploymentUtil.getInstance().copyFile(fromFile, toFile, myContext, writtenPaths, myFileFilter);
  }

  private void computeNestedJars() {
    myNestedJars = new MultiValuesMap<JarInfo, Pair<String, JarInfo>>();
    for (JarInfo jarInfo : myJarsToBuild) {
      for (JarDestinationInfo destination : jarInfo.getJarDestinations()) {
        myNestedJars.put(destination.getJarInfo(), Pair.create(destination.getPathInJar(), jarInfo));
      }
    }
  }

  @Nullable
  private JarInfo[] sortJars() {
    final DFSTBuilder<JarInfo> builder = new DFSTBuilder<JarInfo>(GraphGenerator.create(CachingSemiGraph.create(new JarsGraph())));
    if (!builder.isAcyclic()) {
      final Pair<JarInfo, JarInfo> dependency = builder.getCircularDependency();
      String message = CompilerBundle.message("packaging.compiler.error.cannot.build.circular.dependency.found.between.0.and.1",
                                              dependency.getFirst().getPresentableDestination(),
                                              dependency.getSecond().getPresentableDestination());
      myContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      return null;
    }

    JarInfo[] jars = myJarsToBuild.toArray(new JarInfo[myJarsToBuild.size()]);
    Arrays.sort(jars, builder.comparator());
    jars = ArrayUtil.reverseArray(jars);
    return jars;
  }

  private void addDependentJars() {
    final JarInfo[] jars = myJarsToBuild.toArray(new JarInfo[myJarsToBuild.size()]);
    for (JarInfo jarInfo : jars) {
      addDependentJars(jarInfo);
    }
  }

  public Set<JarInfo> getJarsToBuild() {
    return myJarsToBuild;
  }

  private void addDependentJars(final JarInfo jarInfo) {
    for (JarDestinationInfo destination : jarInfo.getJarDestinations()) {
      JarInfo dependency = destination.getJarInfo();
      if (myJarsToBuild.add(dependency)) {
        addDependentJars(dependency);
      }
    }
  }

  private void buildJar(final JarInfo jar) throws IOException {
    myContext.getProgressIndicator().setText(CompilerBundle.message("packaging.compiler.message.building.0",
                                                                    jar.getPresentableDestination()));
    File jarFile = createTempFile();
    myBuiltJars.put(jar, jarFile);

    Manifest manifest = createManifest(jar);
    DeploymentUtilImpl.setManifestAttributes(manifest.getMainAttributes(), jar.getClasspath());
    final JarOutputStream jarOutputStream = createJarOutputStream(jarFile, manifest);

    try {
      final THashSet<String> writtenPaths = new THashSet<String>();
      writtenPaths.add(JarFile.MANIFEST_NAME);
      for (Pair<String, VirtualFile> pair : jar.getContent()) {
        File file = VfsUtil.virtualToIoFile(pair.getSecond());
        String relativePath = pair.getFirst();
        if (!JarFile.MANIFEST_NAME.equals(relativePath)) {
          addFileToJar(jarOutputStream, file, relativePath, writtenPaths);
        }
      }

      final Collection<Pair<String, JarInfo>> nestedJars = myNestedJars.get(jar);
      if (nestedJars != null) {
        for (Pair<String, JarInfo> nestedJar : nestedJars) {
          File nestedJarFile = myBuiltJars.get(nestedJar.getSecond());
          addFileToJar(jarOutputStream, nestedJarFile, nestedJar.getFirst(), writtenPaths);
        }
      }
    }
    finally {
      jarOutputStream.close();
    }
  }

  private static Manifest createManifest(final JarInfo jar) throws IOException {
    for (Pair<String, VirtualFile> pair : jar.getContent()) {
      if (JarFile.MANIFEST_NAME.equals(pair.getFirst())) {
        return new Manifest(pair.getSecond().getInputStream());
      }
    }
    return new Manifest();
  }

  protected JarOutputStream createJarOutputStream(final File jarFile, final Manifest manifest) throws IOException {
    FileUtil.createParentDirs(jarFile);
    return new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest);
  }

  protected File createTempFile() throws IOException {
    return FileUtil.createTempFile("packagingCompiler", "tmp");
  }

  protected void addFileToJar(final JarOutputStream jarOutputStream, final File file, String relativePath,
                            final THashSet<String> writtenPaths) throws IOException {
    //todo[nik] check file exists?
    while (relativePath.startsWith("/")) {
      relativePath = relativePath.substring(1);
    }
    int i = relativePath.indexOf('/');
    while (i != -1) {
      String prefix = relativePath.substring(0, i+1);
      if (!writtenPaths.contains(prefix) && prefix.length() > 1) {
        addEntry(jarOutputStream, prefix);
        writtenPaths.add(prefix);
      }
      i = relativePath.indexOf('/', i + 1);
    }

    myContext.getProgressIndicator().setText2(relativePath);
    ZipUtil.addFileToZip(jarOutputStream, file, relativePath, writtenPaths, myFileFilter);
  }

  private static void addEntry(final ZipOutputStream output, @NonNls final String relativePath) throws IOException {
    ZipEntry e = new ZipEntry(relativePath);
    e.setMethod(ZipEntry.STORED);
    e.setSize(0);
    e.setCrc(0);
    output.putNextEntry(e);
    output.closeEntry();
  }

  private class JarsGraph implements GraphGenerator.SemiGraph<JarInfo> {
    public Collection<JarInfo> getNodes() {
      return myJarsToBuild;
    }

    public Iterator<JarInfo> getIn(final JarInfo n) {
      Set<JarInfo> ins = new HashSet<JarInfo>();
      for (JarDestinationInfo destination : n.getJarDestinations()) {
        ins.add(destination.getJarInfo());
      }
      return ins.iterator();
    }
  }
}

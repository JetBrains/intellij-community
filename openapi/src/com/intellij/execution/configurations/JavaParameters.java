/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

public class JavaParameters {
  private ProjectJdk myJdk;
  private final PathsList myClassPath = new PathsList();
  private String myMainClass;
  private final ParametersList myVmParameters = new ParametersList();
  private final ParametersList myProgramParameters = new ParametersList();
  private String myWorkingDirectory;
  private Charset myCharset = CharsetToolkit.getIDEOptionsCharset();
  private HashMap<String, String> myEnv;

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public ProjectJdk getJdk() {
    return myJdk;
  }

  public String getJdkPath() throws CantRunException {
    final ProjectJdk jdk = getJdk();
    if(jdk == null) {
      throw new CantRunException("No JDK specified");
    }

    final String jdkHome = jdk.getHomeDirectory().getPresentableUrl();
    if(jdkHome == null || jdkHome.length() == 0) {
      throw new CantRunException("Home directory is not specified for JDK");
    }
    return jdkHome;
  }

  public void setJdk(final ProjectJdk jdk) {
    myJdk = jdk;
  }

  public void setMainClass(final String mainClass) {
    this.myMainClass = mainClass;
  }

  public void setWorkingDirectory(final File path) {
    setWorkingDirectory(path.getPath());
  }

  public void setWorkingDirectory(final String path) {
    myWorkingDirectory = path;
  }

  public static final int JDK_ONLY = 0x1;
  public static final int CLASSES_ONLY = 0x2;
  private static final int TESTS_ONLY = 0x4;
  public static final int JDK_AND_CLASSES = JDK_ONLY | CLASSES_ONLY;
  public static final int JDK_AND_CLASSES_AND_TESTS = JDK_ONLY | CLASSES_ONLY | TESTS_ONLY;

  public void configureByModule(final Module module, final int classPathType) throws CantRunException {
    if ((classPathType & JDK_ONLY) != 0) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ProjectJdk jdk = rootManager.getJdk();
      if (jdk == null) {
        throw CantRunException.noJdkForModule(module);
      }
      myJdk = jdk;
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null || !homeDirectory.isValid()) {
        throw CantRunException.jdkMisconfigured(jdk, module);
      }
    }

    if((classPathType & CLASSES_ONLY) == 0) {
      return;
    }

    ProjectRootsTraversing.collectRoots(module, (classPathType & TESTS_ONLY) != 0 ? ProjectRootsTraversing.FULL_CLASSPATH_RECURSIVE : ProjectRootsTraversing.FULL_CLASSPATH_WITHOUT_TESTS, myClassPath);
  }

  public ParametersList getVMParametersList() {
    return myVmParameters;
  }

  public ParametersList getProgramParametersList() {
    return myProgramParameters;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(final Charset charset) {
    myCharset = charset;
  }

  public PathsList getClassPath() {
    return myClassPath;
  }

  public HashMap<String, String> getEnv() {
    return myEnv;
  }

  public void setEnv(final HashMap<String, String> env) {
    myEnv = env;
  }
}
/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.compiler.CompileContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

public interface JarAndCopyBuildInstruction extends FileCopyInstruction {
  void makeJar(CompileContext context, File jarFile, FileFilter fileFilter) throws IOException;
  File getJarFile();
}
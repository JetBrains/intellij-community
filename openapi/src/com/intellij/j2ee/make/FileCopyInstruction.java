/**
 * @author cdr
 */
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.make;

import java.io.File;

public interface FileCopyInstruction extends BuildInstruction {
  File getFile();
  void setFile(File file, boolean isDirectory);

  boolean isDirectory();
}
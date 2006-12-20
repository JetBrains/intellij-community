/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 19-Dec-2006
 */
package com.intellij.compiler.ant.taskdefs;

import com.intellij.compiler.ant.Tag;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;

public class Zip extends Tag {
  public Zip(@NonNls final String destFile) {
    //noinspection HardCodedStringLiteral
    super("zip", new Pair[] {Pair.create("destfile", destFile)});
  }
}
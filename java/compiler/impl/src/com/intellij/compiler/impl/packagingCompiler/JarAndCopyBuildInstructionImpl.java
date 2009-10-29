/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.compiler.make.BuildInstructionVisitor;
import com.intellij.openapi.compiler.make.JarAndCopyBuildInstruction;
import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jun 21, 2004
 * Time: 4:08:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class JarAndCopyBuildInstructionImpl extends FileCopyInstructionImpl implements JarAndCopyBuildInstruction {

  public JarAndCopyBuildInstructionImpl(Module module, File directoryToJar, String outputRelativePath, @Nullable PackagingFileFilter fileFilter) {
    super(directoryToJar, false, module, outputRelativePath, fileFilter);
  }

  public JarAndCopyBuildInstructionImpl(Module module, File directoryToJar, String outputRelativePath) {
    super(directoryToJar, false, module, outputRelativePath);
  }

  public boolean accept(BuildInstructionVisitor visitor) throws Exception {
    return visitor.visitJarAndCopyBuildInstruction(this);
  }

  @NonNls public String toString() {
    return "JAR and copy: " + getFile() + "->"+getOutputRelativePath();
  }

}

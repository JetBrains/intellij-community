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

/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.cls.ClsFormatException;

public class ClassFileStubBuilder implements BinaryFileStubBuilder {
  public boolean acceptsFile(final VirtualFile file) {
    return !isInner(file.getNameWithoutExtension(), new ParentDirectory(file));
  }

  static boolean isInner(final String name, final Directory directory) {
    return isInner(name, 0, directory);
  }

  private static boolean isInner(final String name, final int from, final Directory directory) {
    final int index = name.indexOf('$', from);
    return index == -1 ? false
                       : containsPart(directory, name, index) ? true : isInner(name, index + 1, directory);
  }

  private static boolean containsPart(Directory directory, String name, int endIndex) {
    return endIndex > 0 && directory.contains(name.substring(0, endIndex));
  }

  public StubElement buildStubTree(final VirtualFile file, final byte[] content, final Project project) {
    try {
      return ClsStubBuilder.build(file, content);
    }
    catch (ClsFormatException e) {
      return null;
    }
  }

  public int getStubVersion() {
    return JavaFileElementType.STUB_VERSION;
  }


  interface Directory {
    boolean contains(String name);
  }

  private static class ParentDirectory implements Directory {
    private final VirtualFile myDirectory;
    private final String myExtension;

    private ParentDirectory(final VirtualFile file) {
      myDirectory = file.getParent();
      myExtension = file.getExtension();
    }

    public boolean contains(final String name) {
      final String fullName = myExtension == null ? name : name + "." + myExtension;
      return myDirectory == null ? false : myDirectory.findChild(fullName) != null;
    }
  }
}
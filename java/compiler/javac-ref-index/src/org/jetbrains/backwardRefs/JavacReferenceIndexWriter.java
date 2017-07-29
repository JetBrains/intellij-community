/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.backwardRefs;

import com.intellij.util.Function;
import com.intellij.util.indexing.InvertedIndex;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.backwardRefs.index.CompiledFileData;
import org.jetbrains.backwardRefs.javac.ast.api.JavacRef;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class JavacReferenceIndexWriter {
  private final CompilerBackwardReferenceIndex myIndex;

  public JavacReferenceIndexWriter(CompilerBackwardReferenceIndex index) {myIndex = index;}

  public Exception getRebuildRequestCause() {
    return myIndex.getRebuildRequestCause();
  }

  void setRebuildCause(Exception e) {
    myIndex.setRebuildRequestCause(e);
  }

  synchronized LightRef.JavaLightClassRef asClassUsage(JavacRef aClass) throws IOException {
    return new LightRef.JavaLightClassRef(id(aClass, myIndex.getByteSeqEum()));
  }

  public File getIndicesDir() {
    return myIndex.getIndicesDir();
  }

  public void processDeletedFiles(Collection<String> files) throws IOException {
    for (String file : files) {
      writeData(enumeratePath(new File(file).getPath()), null);
    }
  }

  void writeData(int id, CompiledFileData d) {
    for (InvertedIndex<?, ?, CompiledFileData> index : myIndex.getIndices()) {
      index.update(id, d).compute();
    }
  }

  synchronized int enumeratePath(String file) throws IOException {
    return myIndex.getFilePathEnumerator().enumerate(file);
  }

  public void close() {
    myIndex.close();
  }

  @Nullable
  LightRef enumerateNames(JavacRef ref, Function<String, Integer> ownerIdReplacer) throws IOException {
    NameEnumerator nameEnumerator = myIndex.getByteSeqEum();
    if (ref instanceof JavacRef.JavacClass) {
      if (!isPrivate(ref) && !((JavacRef.JavacClass)ref).isAnonymous()) {
        return new LightRef.JavaLightClassRef(id(ref, nameEnumerator));
      }
    }
    else {
      if (isPrivate(ref)) {
        return null;
      }
      String ownerName = ref.getOwnerName();
      final Integer ownerPrecalculatedId = ownerIdReplacer.fun(ownerName);
      if (ref instanceof JavacRef.JavacField) {
        return new LightRef.JavaLightFieldRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator));
      }
      else if (ref instanceof JavacRef.JavacMethod) {
        int paramCount = ((JavacRef.JavacMethod) ref).getParamCount();
        return new LightRef.JavaLightMethodRef(ownerPrecalculatedId != null ? ownerPrecalculatedId : id(ownerName, nameEnumerator), id(ref, nameEnumerator), paramCount);
      }
      else {
        throw new AssertionError("unexpected symbol: " + ref + " class: " + ref.getClass());
      }
    }
    return null;
  }

  public void flush() {
    myIndex.flush();
  }

  private static boolean isPrivate(JavacRef ref) {
    return ref.getModifiers().contains(Modifier.PRIVATE);
  }

  private static int id(JavacRef ref, NameEnumerator nameEnumerator) throws IOException {
    return id(ref.getName(), nameEnumerator);
  }

  private static int id(String name, NameEnumerator nameEnumerator) throws IOException {
    return nameEnumerator.enumerate(name);
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.module.JpsModule;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

/**
 * @author nik
 */
public class ClassMoveTest extends JpsBuildTestCase {
  public void testMoveClassAndDelete() {
    String a1 = createFile("src1/A.java", "class A{}");
    String b = createFile("src2/B.java", "class B{}");
    JpsModule m = addModule("m", PathUtil.getParentPath(a1), PathUtil.getParentPath(b));
    makeAll();
    assertOutput(m, fs().file("A.class").file("B.class"));

    delete(a1);
    String a2 = createFile("src2/A.java", "class A{}");
    makeAll();
    assertOutput(m, fs().file("A.class").file("B.class"));

    delete(a2);
    makeAll();
    assertOutput(m, fs().file("B.class"));
  }
}

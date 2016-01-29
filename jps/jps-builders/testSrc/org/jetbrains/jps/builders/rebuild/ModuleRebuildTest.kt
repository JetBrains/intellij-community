/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.rebuild

import org.jetbrains.jps.util.JpsPathUtil
import com.intellij.util.PathUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

/**
 * @author nik
 */
class ModuleRebuildTest: JpsRebuildTestCase() {
  fun testModuleCycle() {
    doTest("moduleCycle/moduleCycle.ipr", fs {
        dir("production") {
          dir("module1") {
            file("Bar1.class")
          }
          dir("module2") {
            file("Bar2.class")
          }
          dir("moduleCycle") {
            file("Foo.class")
          }
        }
    })
  }

  fun testOverlappingSourceRoots() {
    doTest("overlappingSourceRoots/overlappingSourceRoots.ipr", fs {
        dir("production") {
          dir("inner") {
            dir("y") {
              file("a.properties")
              file("Class2.class")
            }
          }
          dir("overlappingSourceRoots") {
            dir("x") {
              file("MyClass.class")
            }
          }
        }
    })
  }

  fun testResourceCopying() {
    doTest("resourceCopying/resourceCopying.ipr", fs {
      dir("production") {
        dir("resourceCopying") {
          dir("copy") {
            file("file.txt")
          }
          dir("copyTree") {
            dir("subdir") {
              file("abc.txt")
            }
            file("xyz.txt")
          }
          file("a.txt")
          file("index.html")
        }
      }
    })
  }

  fun testResourceRootWithRelativeOutputPath() {
    val m = addModule("m")
    val res = PathUtil.getParentPath(createFile("res/a.txt", "42"))
    val url = JpsPathUtil.pathToUrl(res)
    m.addSourceRoot(url, JavaResourceRootType.RESOURCE, JpsJavaExtensionService.getInstance().createResourceRootProperties("foo", false))
    rebuild()
    assertOutput(getAbsolutePath("out/production/m"), fs {
      dir("foo") {
        file("a.txt", "42")
      }
    })
  }
}

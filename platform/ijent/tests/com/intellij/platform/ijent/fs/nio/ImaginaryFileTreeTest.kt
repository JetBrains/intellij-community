// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs.nio

import io.kotest.matchers.be
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import org.junit.jupiter.api.Test

class ImaginaryFileTreeTest {
  private fun makeFs() = ImaginaryFileTree().apply {
    modify {
      root("/") {
        dir("usr") {
          dir("bin") {
            file("cowsay") {
              text("#!/bin/bash")
            }
          }
          dir("lib") {
            file("libcowsay.so") {
              text("something")
            }
            dir("empty") {}
          }
        }
      }
    }
  }

  @Test
  fun contents() {
    val fs = makeFs()

    val expected = ImaginaryFileTree(mutableListOf(
      ImaginaryFileTree.Node.Directory("/", mutableListOf(
        ImaginaryFileTree.Node.Directory("usr", mutableListOf(
          ImaginaryFileTree.Node.Directory("bin", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
          )),
          ImaginaryFileTree.Node.Directory("lib", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
            ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
          )),
        )),
      )),
    ))

    fs should be(expected)
  }

  @Test
  fun string() {
    val fs = makeFs()

    fs.toPrettyString() should be("""
        root("/") {
          dir("usr") {
            dir("bin") {
              file("cowsay") {
                text("#!/bin/bash")
              }
            }
            dir("lib") {
              file("libcowsay.so") {
                text("something")
              }
              dir("empty") {}
            }
          }
        }
        """.trimIndent())
  }

  @Test
  fun `deepCopy and modifications`() {
    val fs = makeFs()
    val fsCopy = fs.deepCopy()

    fs should be(fsCopy)

    fs.modify {
      root("/") {
        dir("root") {
          file("example.txt") {
            text("hello world")
          }
        }
      }
    }

    fs shouldNot be(fsCopy)

    fsCopy.modify {
      root("/") {
        dir("root") {}
      }
    }

    fs shouldNot be(fsCopy)

    fsCopy.modify {
      root("/") {
        dir("root") {
          file("example.txt") {
            text("something else")
          }
        }
      }
    }

    fs shouldNot be(fsCopy)

    fsCopy.modify {
      root("/") {
        dir("root") {
          file("example.txt") {
            text("hello world")
          }
        }
      }
    }

    fs should be(fsCopy)
  }

  @Test
  fun traverseDepthFirst() {
    val fs = makeFs()
    fs.traverseDepthFirst().toList() should be(listOf(
      ImaginaryFileTree.Node.Directory("/", mutableListOf(
        ImaginaryFileTree.Node.Directory("usr", mutableListOf(
          ImaginaryFileTree.Node.Directory("bin", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
          )),
          ImaginaryFileTree.Node.Directory("lib", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
            ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
          )),
        )),
      )),

      ImaginaryFileTree.Node.Directory("usr", mutableListOf(
        ImaginaryFileTree.Node.Directory("bin", mutableListOf(
          ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
        )),
        ImaginaryFileTree.Node.Directory("lib", mutableListOf(
          ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
          ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
        )),
      )),

      ImaginaryFileTree.Node.Directory("bin", mutableListOf(
        ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
      )),

      ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),

      ImaginaryFileTree.Node.Directory("lib", mutableListOf(
        ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
        ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
      )),

      ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),

      ImaginaryFileTree.Node.Directory("empty", mutableListOf()),

      ))
  }

  @Test
  fun traverseBreadthFirst() {
    val fs = makeFs()
    fs.traverseBreadthFirst().toList() should be(listOf(
      ImaginaryFileTree.Node.Directory("/", mutableListOf(
        ImaginaryFileTree.Node.Directory("usr", mutableListOf(
          ImaginaryFileTree.Node.Directory("bin", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
          )),
          ImaginaryFileTree.Node.Directory("lib", mutableListOf(
            ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
            ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
          )),
        )),
      )),

      ImaginaryFileTree.Node.Directory("usr", mutableListOf(
        ImaginaryFileTree.Node.Directory("bin", mutableListOf(
          ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
        )),
        ImaginaryFileTree.Node.Directory("lib", mutableListOf(
          ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
          ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
        )),
      )),

      ImaginaryFileTree.Node.Directory("bin", mutableListOf(
        ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),
      )),

      ImaginaryFileTree.Node.Directory("lib", mutableListOf(
        ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),
        ImaginaryFileTree.Node.Directory("empty", mutableListOf()),
      )),

      ImaginaryFileTree.Node.RegularFile("cowsay", "#!/bin/bash"),

      ImaginaryFileTree.Node.RegularFile("libcowsay.so", "something"),

      ImaginaryFileTree.Node.Directory("empty", mutableListOf()),

      ))
  }
}
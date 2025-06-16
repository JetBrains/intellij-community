// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.daemon.impl.JavaInheritorsCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.JavaReferencesCodeVisionProvider
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
internal class JavaMultiverseCodeVisionProviderTest {

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()
    private val moduleAFixture : TestFixture<Module> = projectFixture.moduleFixture("moduleA")
    private val moduleBFixture : TestFixture<Module> = projectFixture.moduleFixture("moduleB")
    private val sharedSourceRootFixture = sharedSourceRootFixture(moduleAFixture, moduleBFixture)
  }

  private val sourceRootFixtureA = moduleAFixture.sourceRootFixture()
  private val sourceRootFixtureB = moduleBFixture.sourceRootFixture()

  private val sharedFileFixture = sharedSourceRootFixture.psiFileFixture("Shared.java", "")
  private val editorFixture = sharedFileFixture.editorFixture()
  private val codeVisionFixture = codeVisionFixture(editorFixture, sharedFileFixture)

  @Test
  fun testClassUsages() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A {
          void foo() {
            new Shared();
          }
          void bar() {
            new Shared();
          }
        }
          """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B {
          void foo() {
            new Shared();
          }
        }
          """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        class Shared {}  /*<# [3 usages] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
      package com.company;
      class Shared {} /*<# [2 usages] #>*/
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  }

  @Test
  fun testMethodUsages() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A {
          void foo() {
            var sh = new Shared();
            sh.doShared()
          }
          void bar() {
            new Shared();
          }
        }
          """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B {
          void foo() {
            new Shared();
          }
        }
          """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
      package com.company;
      class Shared { /*<# [3 usages] #>*/
        void doShared() {} /*<# [1 usage] #>*/
      }  
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        class Shared {  /*<# [2 usages] #>*/
          void doShared() {} /*<# [no usages] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)
  }

  @Test
  fun testClassInheritors() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A extends Shared {}
        public class A1 extends Shared {}
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B extends Shared {}
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        class Shared{} /*<# [3 usages   2 inheritors] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        class Shared{} /*<# [2 usages   1 inheritor] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)
  }

  @Test
  fun testInterfaceInheritors() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A implements Shared {}
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B implements Shared {}
        public class C implements Shared {}
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        interface Shared{} /*<# [2 usages   1 implementation] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        interface Shared{} /*<# [3 usages   2 implementations] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testMethodOverrides() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        class A extends Shared {
          void foo(){}
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        class B extends Shared {
          void foo(){}
        }
        class C extends Shared {
          void foo(){}
        }  
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        class Shared{ /*<# [2 usages   1 inheritor] #>*/
          public void foo(){} /*<# [no usages   1 override] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        class Shared{ /*<# [3 usages   2 inheritors] #>*/
          public void foo(){} /*<# [no usages   2 overrides] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testAbstractMethodImplementations() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        class A implements Shared {
          public void foo(){}
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        class B implements Shared {
          public void foo(){}
        }
        class C implements Shared {
          public void foo(){}
        }
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        interface Shared {/*<# [2 usages   1 implementation] #>*/
        void foo();/*<# [no usages   1 implementation] #>*/
      }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        interface Shared {/*<# [3 usages   2 implementations] #>*/
        void foo();/*<# [no usages   2 implementations] #>*/
      }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testEnumMembers() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.E;
        class A {
          E foo() {
            bar(E.E1, E.E2, E.E3, E.E4);
          }
          void bar(E... e) {} 
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.E;
        class B {
          E foo() {
            bar(E.E1, E.E2);
          }
          void bar(E... e) {} 
        }  
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        enum E { /*<# [7 usages] #>*/
          E1, E2, E3, E4/*<# [1 usage] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        enum E { /*<# [5 usages] #>*/
          E1, E2, E3, E4/*<# [1 usage] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

  }

  private suspend fun addFileToSourceRoot(sourceRoot: PsiDirectory, fileName: String, fileContent: String) {
    withContext(Dispatchers.EDT) {
      writeAction {
        sourceRoot.createFile(fileName).also {
          it.virtualFile.setBinaryContent(fileContent.toByteArray())
        }
      }
    }
  }

  private fun setContext(editor: Editor, module: Module) : CodeInsightContext {
    val project = projectFixture.get()
    val context = requireNotNull(ProjectModelContextBridge.getInstance(project).getContext(module))
    EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(context))
    return context
  }
}
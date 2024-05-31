import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.AndroidStudioProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.impl.BuildContextImpl
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeLines

/**
 * Builds a module's tests and all its dependencies. It additionally outputs the classpath to be able to run those tests.
 * This is done later separately run the tests in parallel and distributed.
 */
@Suppress("RAW_RUN_BLOCKING")
object TestModuleTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking {
      val home = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
      val properties = AndroidStudioProperties(home.communityRoot)
      val context = BuildContextImpl.createContext(home.communityRoot, properties)
      val tasks = BuildTasks.create(context)

      val moduleName = System.getProperty("idea.test.module")
      val outDir = context.paths.artifactDir.resolve("module-tests")
      val module = context.findRequiredModule(moduleName)

      val modules = mutableListOf<String>()
      modules.add(moduleName)
      tasks.compileModules(modules, includingTestsInModules = modules)


      val workspace = Path.of(System.getenv("JPS_WORKSPACE"))
      val classPath = mutableListOf<String>()
      for (path in context.getModuleRuntimeClasspath(module, forTests = true)) {
        val p = Path.of(path)
        if (p.exists()) {
          if (p.startsWith(workspace)) {
              val rel = workspace.relativize(p)
              classPath.add(rel.toString())
          } else {
            check(false) { "Class path entry ${path}, not recognized as any source." }
          }
        }
      }

      val classPathFile = outDir.resolve(moduleName + ".classpath.txt")
      outDir.toFile().mkdirs()
      classPathFile.writeLines(classPath)
    }
  }
}
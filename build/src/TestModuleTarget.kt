import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
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
      val home = BuildPaths.COMMUNITY_ROOT
      val properties = AndroidStudioProperties()
      val context = BuildContextImpl.createContext(home.communityRoot, properties)

      val moduleName = System.getProperty("idea.test.module")
      val outDir = context.paths.artifactDir.resolve("module-tests")
      val module = context.findRequiredModule(moduleName)

      val modules = mutableListOf<String>()
      modules.add(moduleName)
      context.compileModules(modules, includingTestsInModules = modules)


      val workspace = Path.of(System.getenv("JPS_WORKSPACE"))
      val classPath = mutableListOf<String>()
      for (path in context.getModuleRuntimeClasspath(module, forTests = true)) {
        if (path.exists()) {
          if (path.startsWith(workspace)) {
              val rel = workspace.relativize(path)
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
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.buildDistributions
import java.nio.file.Paths

/**
 * Generates a JSON file which maps IDE distribution jars to their corresponding JPS modules/libraries.
 * Example usage:
 *
 *   ./platform/jps-bootstrap/jps-bootstrap.sh "$PWD" intellij.idea.community.build AndroidStudioSourceMapBuildTarget /path/to/outfile.json
 */
@Suppress("RAW_RUN_BLOCKING")
object AndroidStudioSourceMapBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking {
      val outfile = args.singleOrNull()
        ?: error("Expected a single argument specifying the output file path")
      val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
      val communityRoot = communityHome.communityRoot
      val ideProperties = AndroidStudioProperties(communityRoot)
      val buildContext = BuildContextImpl.createContext(communityRoot, ideProperties)
      val compilationTasks = CompilationTasks.create(buildContext)
      compilationTasks.compileModules(ideProperties.productLayout.bundledPluginModules)
      buildDistributions(buildContext)
    }
  }
}

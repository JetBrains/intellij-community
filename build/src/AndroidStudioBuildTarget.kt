import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.AndroidStudioProperties
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.impl.BuildContextImpl

@Suppress("RAW_RUN_BLOCKING")
object AndroidStudioBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking {
      val home = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
      val properties = AndroidStudioProperties(home.communityRoot)
      val buildContext = BuildContextImpl.createContext(home.communityRoot, properties)
      val tasks = createBuildTasks(buildContext)
      tasks.buildDistributions()
    }
  }
}

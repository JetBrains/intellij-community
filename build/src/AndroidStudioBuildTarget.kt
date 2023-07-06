import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.AndroidStudioBuilder
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil

@Suppress("RAW_RUN_BLOCKING")
object AndroidStudioBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    runBlocking {
      AndroidStudioBuilder(communityHome).buildDistributions()
    }
  }
}
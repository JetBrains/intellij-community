import org.jetbrains.intellij.build.AndroidStudioBuilder
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil

object AndroidStudioBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    AndroidStudioBuilder(communityHome, BuildOptions()).buildDistributions()
  }
}
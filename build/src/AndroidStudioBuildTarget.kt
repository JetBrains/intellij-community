import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.AndroidStudioProperties
import org.jetbrains.intellij.build.BuildContext
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
      validateProductVersion(buildContext)
      val tasks = createBuildTasks(buildContext)
      tasks.buildDistributions()
    }
  }

  /**
   * Checks that the product version in AndroidStudioApplicationInfo.xml is consistent with the build number in build.txt
   * For example, product version  "2025.1.X.X" is consistent with build number "251.XXX" because 251 is shorthand for 2025.1.
   */
  private fun validateProductVersion(buildContext: BuildContext) {
    val appInfo = buildContext.applicationInfo
    val buildNum = buildContext.buildNumber
    check(buildNum.substringBefore('.') == "${appInfo.majorVersion.drop(2)}${appInfo.minorVersion}") {
      "The version specified in AndroidStudioApplicationInfo.xml is inconsistent with the build number in build.txt"
    }
  }
}

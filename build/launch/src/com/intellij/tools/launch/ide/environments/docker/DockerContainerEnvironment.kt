package com.intellij.tools.launch.ide.environments.docker

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.docker.BindMount
import com.intellij.tools.launch.environments.FsCorrespondence
import com.intellij.tools.launch.environments.LaunchEnvironment
import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import com.intellij.tools.launch.environments.limitedFsCorrespondence
import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import com.intellij.util.SystemProperties
import com.sun.security.auth.module.UnixSystem
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal const val UNIX_FILE_SEPARATOR = '/'
internal const val UNIX_PATH_SEPARATOR = ':'

class DockerContainerEnvironment(
  private val uid: String,
  private val gid: String,
  private val userName: String,
  private val userHome: String,
  private val fileSeparator: String,
  bindMounts: List<BindMount>,
) : LaunchEnvironment {
  val bindMounts = bindMounts.toList()

  private val fsCorrespondence: FsCorrespondence =
    limitedFsCorrespondence(mappings = bindMounts.associate { it.hostPath to it.containerPath }, envFileSeparator = UNIX_FILE_SEPARATOR)

  override fun uid(): String = uid

  override fun gid(): String = gid

  override fun userName(): String = userName

  override fun userHome(): String = userHome

  override fun resolvePath(base: PathInLaunchEnvironment, relative: PathInLaunchEnvironment): PathInLaunchEnvironment {
    return when (base) {
      fileSeparator -> base + relative
      else -> base.trimEnd(UNIX_FILE_SEPARATOR) + fileSeparator + relative
    }
  }

  companion object {
    private val localMavenRepositoryPath: Path
      get() = Path.of(SystemProperties.getUserHome(), ".m2", "repository")

    private const val DOCKER_CONTAINER_MAVEN_REPO_PATH = "/host/.m2/repository"

    private val localUltimateRepositoryPath: Path
      get() = Path.of(PathManager.getHomePath())

    fun createDefaultDockerContainerEnvironment(dockerContainerOptions: DockerContainerOptions,
                                                localPaths: PathsProvider): DockerContainerEnvironment {
      val uid = if (SystemInfo.isUnix) UnixSystem().uid.toString() else "0"
      val gid = if (SystemInfo.isUnix) UnixSystem().gid.toString() else "0"
      val userName: String = System.getProperty("user.name")!!
      val userHome: String = getCanonicalUserHome(userName)
      val ideBindMounts =
        if (!dockerContainerOptions.legacy) {
          listOf(
            BindMount(localUltimateRepositoryPath, dockerContainerOptions.ultimateRepositoryPathInContainer),
            BindMount(localMavenRepositoryPath, DOCKER_CONTAINER_MAVEN_REPO_PATH),
          )
        }
        else {
          // this section is preserved from the previous version to keep rd tests passing
          emptyList<BindMount>()
            // **** RW ****
            .fulfillBindMounts(
              listOf(
                localPaths.logFolder,
                localPaths.tempFolder,
                localPaths.configFolder,
                localPaths.systemFolder,
                localPaths.communityRootFolder.resolve("build/download"), // quiche lib
              ),
              readonly = false
            )
            // **** RO ****
            .fulfillBindMounts(
              listOf(
                localPaths.javaHomeFolder,
                localPaths.outputRootFolder,
                // Required to ultimate root detection
                localPaths.ultimateRootMarker,
                // jars
                localPaths.communityBinFolder,
                localPaths.communityRootFolder.resolve("lib"),
                localPaths.sourcesRootFolder.resolve("lib"),
                localPaths.sourcesRootFolder.resolve("plugins"),
                localPaths.sourcesRootFolder.resolve("contrib"),
              ),
              readonly = true
            )
            .run {
              // on buildserver agents libraries may be cached in ~/.m2.base
              if (TeamCityHelper.isUnderTeamCity) {
                val mavenCache = File(SystemProperties.getUserHome()).resolve(".m2.base")
                if (mavenCache.isDirectory) {
                  return@run fulfillBindMounts(listOf(mavenCache), readonly = false, optional = true)
                }
              }
              this
            }
            .fulfillBindMounts(
              listOf(
                // a lot of jars in classpaths, /plugins, /xml, so we'll just mount the whole root
                localPaths.communityRootFolder,
                // main jar itself
                localPaths.launcherFolder,
                // ~/.m2
                localPaths.mavenRepositoryFolder,
                // quiche
                localPaths.sourcesRootFolder.resolve(".idea"),
              ),
              readonly = true,
              optional = true
            )
        }

      return DockerContainerEnvironment(uid, gid, userName, userHome, fileSeparator = "/",
                                        bindMounts = ideBindMounts + dockerContainerOptions.bindMounts)
    }

    /**
     * Does not work with Docker on Windows.
     */
    private fun List<BindMount>.fulfillBindMounts(
      localFiles: List<File>,
      readonly: Boolean,
      optional: Boolean = false,
    ): List<BindMount> =
      buildList {
        addAll(this@fulfillBindMounts)

        localFiles
          // sort paths to effectively minimize the number of bind mounts
          .sorted()
          .forEach { localFile ->
            if (optional && !localFile.exists()) {
              // if the bind mount is optional and the local path does not exist, skip it
            }
            else {
              val localPath = localFile.toPath()
              val canonicalLocalPath = Path.of(localFile.canonicalPath)
              if (none { (boundLocalPath) -> localPath.startsWith(boundLocalPath) || canonicalLocalPath.startsWith(boundLocalPath) }) {
                addBindMount(canonicalLocalPath, readonly, localFile)
                // docker can create these under root, so we create them ourselves
                if (!readonly) {
                  Files.createDirectories(localPath)
                }
              }
            }
          }
      }

    private fun MutableList<BindMount>.addBindMount(canonicalLocalPath: Path, readonly: Boolean, localFile: File) {
      add(BindMount(hostPath = canonicalLocalPath, containerPath = canonicalLocalPath.toString(), readonly = readonly))
      val notResolvingSymlinks = Path.of(localFile.pathNotResolvingSymlinks())
      if (notResolvingSymlinks != canonicalLocalPath) {
        add(BindMount(hostPath = notResolvingSymlinks, containerPath = notResolvingSymlinks.toString(), readonly = readonly))
      }
    }

    private fun getCanonicalUserHome(userName: String): String = if (userName == "root") "/root" else "/home/$userName"
  }

  override fun fsCorrespondence(): FsCorrespondence = fsCorrespondence
}
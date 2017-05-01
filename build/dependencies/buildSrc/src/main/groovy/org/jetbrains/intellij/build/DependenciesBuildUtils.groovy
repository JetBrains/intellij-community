package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.os.OperatingSystem

@CompileStatic
class DependenciesBuildUtils {
  private static final Logger LOG = Logging.getLogger('DependenciesBuild')
  private static Boolean isInJetBrainsNetwork = null

  static def getInJetBrainsNetwork() {
    if (isInJetBrainsNetwork == null) {
      try {
        isInJetBrainsNetwork = InetAddress.getByName("repo.labs.intellij.net").isReachable(1000)
        if (!isInJetBrainsNetwork && OperatingSystem.current().isWindows()) {
          isInJetBrainsNetwork = Runtime.runtime.exec("ping -n 1 repo.labs.intellij.net").waitFor() == 0
        }
        if (!isInJetBrainsNetwork) {
          LOG.info('repo.labs.intellij.net is not reachable')
        }
      }
      catch (UnknownHostException e) {
        LOG.info('repo.labs.intellij.net is not reachable', e)
        isInJetBrainsNetwork = false
      }
    }
    return isInJetBrainsNetwork
  }

  static def getJdkRepo() {
    return inJetBrainsNetwork ? 'http://repo.labs.intellij.net/intellij-jdk' : 'https://dl.bintray.com/jetbrains/intellij-jdk'
  }

  static def intellijProjectDir(File gradleProjectDir) {
    def communityDir = new File(gradleProjectDir, "../..").canonicalFile
    return communityDir.name == 'community' ? communityDir.parent : communityDir
  }
  
  static def isInUltimateContext(File gradleProjectDir) {
    return new File(gradleProjectDir, "../..").canonicalFile.name == 'community'
  }
}
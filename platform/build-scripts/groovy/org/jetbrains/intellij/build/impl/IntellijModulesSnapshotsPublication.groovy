// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext

@CompileStatic
@SuppressWarnings("unused")
class IntellijModulesSnapshotsPublication {
  private final BuildContext context
  private final String home

  IntellijModulesSnapshotsPublication(BuildContext context, String home) {
    this.context = context
    this.home = home
  }

  def publish(Collection<String> modulesToPublish) {
    def options = new IntellijModulesPublication.Options(
      version: context.buildNumber,
      repositoryUrl: "https://repo.labs.intellij.net/intellij-snapshot-modules",
      outputDir: new File("$home/out/idea-ue/artifacts/maven-artifacts"),
      modulesToPublish: modulesToPublish
    )
    new IntellijModulesPublication(context, options).publish()
  }
}

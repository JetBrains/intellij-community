// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

/**
 * This request allows client to choose between copying files to the remote target and
 * accessing them directly. While copying may take some time, remote access may be slow
 */
interface VolumeCopyingRequest {
  var shouldCopyVolumes: Boolean
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.value.TargetValue


//FIXME: javadoc:
interface TargetEnvironmentVolume {

  /**
   * //FIXME: correct from youtrack
   * Mode defines the semantics of the volume, as follows:
   *
   *  * Upload: The remote path should match the state of the local path, before the process is started
   *  * Download: The local path should match the state of the remote path, after the process completes (and the transfer completes)
   *  * Upload temporarily: The remote path should match the state of the local path, before the process is started.
   * The remote file should be removed after if applicable
   *  * Persist remotely: The remote path should be mounted from a persistent object storage, which should match
   * the state it had after the previous execution completed. On initial configuration it is expected to
   * be an empty directory
   *
   *
   */
  sealed class VolumeMode {
    class Default() : VolumeMode() { // FIXME: how to embed enum literal to sealed class
      override fun equals(other: Any?): Boolean {
        return other is Default
      }

      override fun hashCode(): Int {
        return 42
      }

      companion object {
        @JvmStatic
        val Instance = Default()
      }
    }

    data class Upload(val remoteRoot: String?, val temporary: Boolean) : VolumeMode()
    data class Download(val remoteRoot: String) : VolumeMode()
    //data class Persisted(val volumeId: String) : VolumeMode()
  }

  val request: TargetEnvironmentRequest

  /**
   * Returns the [VolumeMode] for this volume. Mode is set on volume creation and can't be changed during life time
   */
  val mode: VolumeMode

  //val rootPath: TargetValue<String>
  //val id: String

  /**
   * Creates the requirement to upload the local path up to the target environment.
   * <p/>
   * Returned value may be used in [TargetedCommandLineBuilder]
   * where it will be replaced to the corresponding **absolute** path at the target machine.
   */
  fun createUpload(localPath: String): TargetValue<String>

  /**
   * Creates the requirement to download the local path from the target environment.
   * //FIXME: when and which promises are being resolved?
   */
  fun createDownload(rootRelativePath: String): TargetValue<String>


  companion object {
    const val DEFAULT_TEMP_VOLUME_ID = "DefaultTempVolume"
  }
}

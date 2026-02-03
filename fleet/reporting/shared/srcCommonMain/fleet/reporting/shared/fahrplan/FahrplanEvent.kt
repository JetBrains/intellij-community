// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.reporting.shared.fahrplan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FahrplanEvent {
  abstract val id: String
  abstract val ts: Long

  @Serializable
  @SerialName("RecordingStart")
  data class RecordingStart(override val id: String,
                            override val ts: Long) : FahrplanEvent()

  @Serializable
  @SerialName("RecordingEnd")
  data class RecordingEnd(override val id: String,
                          override val ts: Long) : FahrplanEvent()

  @Serializable
  @SerialName("Start")
  data class StartSpan(override val id: String,
                       override val ts: Long,
                       val name: String,
                       val jobId: Int,
                       val parent: String?,
                       val meta: Map<String, String>?,
                       val cause: String?,
                       val isScope: Boolean) : FahrplanEvent()

  @Serializable
  @SerialName("End")
  data class EndSpan(override val id: String,
                     override val ts: Long,
                     val status: Status) : FahrplanEvent() {
    @Serializable
    sealed class Status {
      @Serializable
      @SerialName("Success")
      data object Success : Status()

      @Serializable
      @SerialName("Cancelled")
      data object Cancelled : Status()

      @Serializable
      @SerialName("Failed")
      data class Failed(val error: String) : Status()
    }
  }
}
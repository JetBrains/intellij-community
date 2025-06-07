package org.jetbrains.jewel.buildlogic.ideversion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiIdeaReleasesItem(
    @SerialName("code") val code: String,
    @SerialName("releases") val releases: List<Release>,
) {

    @Serializable
    internal data class Release(
        @SerialName("build") val build: String,
        @SerialName("type") val type: String,
        @SerialName("version") val version: String,
        @SerialName("majorVersion") val majorVersion: String,
    )
}

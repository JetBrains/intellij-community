package org.jetbrains.jewel.buildlogic.demodata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiAndroidStudioReleases(@SerialName("content") val content: Content = Content()) {

    @Serializable
    internal data class Content(
        @SerialName("item") val item: List<Item> = listOf(),
        @SerialName("version") val version: Int = 0,
    ) {

        @Serializable
        internal data class Item(
            @SerialName("build") val build: String,
            @SerialName("channel") val channel: String,
            @SerialName("date") val date: String,
            @SerialName("download") val download: List<Download> = listOf(),
            @SerialName("name") val name: String,
            @SerialName("platformBuild") val platformBuild: String,
            @SerialName("platformVersion") val platformVersion: String?,
            @SerialName("version") val version: String,
        ) {

            @Serializable
            internal data class Download(
                @SerialName("checksum") val checksum: String,
                @SerialName("link") val link: String,
                @SerialName("size") val size: String,
            )
        }
    }
}

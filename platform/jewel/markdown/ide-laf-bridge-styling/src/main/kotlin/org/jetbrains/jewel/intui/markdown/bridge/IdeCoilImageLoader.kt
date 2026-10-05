// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.images.Coil3ImageRendererExtension

/**
 * A default image loader that uses the IntelliJ Platform network settings to fetch remote images.
 *
 * Use this instead of [Coil3ImageRendererExtension.withDefaultLoader] when Jewel runs inside an IntelliJ Platform
 * application. The loader has the same limited in-memory cache. It fetches remote images with a Ktor client on the Java
 * engine, which uses:
 * - The IDE proxy settings, including PAC and automatic proxy detection. [java.net.ProxySelector], which the IDE
 *   installs at startup.
 * - The IDE proxy authentication, from [JdkProxyProvider].
 * - The IDE trusted certificates, including custom certificate authorities, from [CertificateManager].
 *
 * This shouldn't be used if there is an app-wide image loader already available; instead, use the constructor to pass
 * in the already available image loader.
 *
 * Note that every invocation creates a new [ImageLoader] and a new HTTP client, so it is not recommended to call this
 * method multiple times in a process. Instead, create one top-level instance and share it throughout the process if at
 * all possible.
 *
 * This function does not remember its result. If you call it from a composable, wrap the call in `remember`, keyed on
 * the [context], so that recompositions reuse the same instance. The composable overload of [withIdeDefaultLoader] does
 * this for you.
 *
 * @param context The [PlatformContext] to use to create the [ImageLoader].
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun Coil3ImageRendererExtension.Companion.withIdeDefaultLoader(
    context: PlatformContext
): Coil3ImageRendererExtension =
    Coil3ImageRendererExtension.withDefaultLoader(context) {
        components {
            add(
                KtorNetworkFetcherFactory(
                    httpClient =
                        HttpClient(Java) {
                            engine {
                                config {
                                    authenticator(JdkProxyProvider.getInstance().authenticator)
                                    sslContext(CertificateManager.getInstance().sslContext)
                                }
                            }
                        }
                )
            )
        }
    }

/**
 * A default image loader that uses the IntelliJ Platform network settings to fetch remote images.
 *
 * This is the same as [withIdeDefaultLoader], but it reads the [PlatformContext] from [LocalPlatformContext].
 *
 * Do not call this if there is an app-wide image loader already available. Instead, use the constructor to pass in the
 * already available image loader.
 *
 * The result is remembered for the current [LocalPlatformContext], so recompositions reuse the same [ImageLoader] and
 * HTTP client. Each call site still creates its own instance, so it is not recommended to call this from multiple
 * places. Instead, create one top-level instance and share it throughout the process if at all possible.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun Coil3ImageRendererExtension.Companion.withIdeDefaultLoader(): Coil3ImageRendererExtension {
    val context = LocalPlatformContext.current
    return remember(context) { Coil3ImageRendererExtension.withIdeDefaultLoader(context) }
}

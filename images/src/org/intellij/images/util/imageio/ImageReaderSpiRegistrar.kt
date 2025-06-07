// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.util.imageio

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import org.intellij.images.util.imageio.svg.SvgImageReaderSpi
import java.nio.file.Path
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi

class ImageReaderSpiRegistrar: ApplicationLoadListener {
  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    val defaultInstance = IIORegistry.getDefaultInstance()
    defaultInstance.registerServiceProvider(CommonsImagingImageReaderSpi(), ImageReaderSpi::class.java)
    defaultInstance.registerServiceProvider(SvgImageReaderSpi(), ImageReaderSpi::class.java)
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.SVGLoader.load
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@Service(Service.Level.APP)
internal class ConvertSvgToPngService(
  private val coroutineScope: CoroutineScope,
) {

  fun convert(inputFile: File, outputFile: File) {
    coroutineScope.launch(Dispatchers.IO + CoroutineName("convert $inputFile to $outputFile")) {
      kotlin.runCatching {
        val image = load(inputFile.toURI().toURL(), 1f)
        ImageIO.write(image as BufferedImage, "png", outputFile)
        VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
      }.getOrLogException(LOG)
    }
  }

}

private val LOG = logger<ConvertSvgToPngService>()

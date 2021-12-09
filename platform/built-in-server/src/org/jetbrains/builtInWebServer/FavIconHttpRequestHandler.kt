// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.ide.ui.ProductIcons
import com.intellij.util.ui.ImageUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addNoCache
import org.jetbrains.io.response
import org.jetbrains.io.send
import java.awt.image.BufferedImage

internal object FavIconHttpRequestHandler {
  fun sendDefaultFavIcon(rawPath: String, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    if (rawPath != "/favicon.ico") {
      return false
    }
    val icon = ProductIcons.getInstance().productIcon
    val image = ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    icon.paintIcon(null, image.graphics, 0, 0)
    val icoBytes = Imaging.writeImageToBytes(image, ImageFormats.ICO, null)
    response(FileResponses.getContentType(rawPath), Unpooled.wrappedBuffer(icoBytes))
      .addNoCache()
      .send(context.channel(), request)
    return true
  }
}
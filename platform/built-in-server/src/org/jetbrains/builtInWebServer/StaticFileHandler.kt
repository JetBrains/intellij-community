package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.PathUtilRt
import io.netty.buffer.ByteBufUtf8Writer
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import org.jetbrains.builtInWebServer.ssi.SsiExternalResolver
import org.jetbrains.builtInWebServer.ssi.SsiProcessor
import org.jetbrains.io.FileResponses
import org.jetbrains.io.Responses
import java.io.File

private class StaticFileHandler : WebServerFileHandler() {
  private var ssiProcessor: SsiProcessor? = null

  override fun process(pathInfo: PathInfo, canonicalRequestPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, isCustomHost: Boolean): Boolean {
    if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
      val ioFile = pathInfo.ioFile ?: File(pathInfo.file!!.path)

      val nameSequence = pathInfo.name
      //noinspection SpellCheckingInspection
      if (StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtml") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".stm") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtm")) {
        processSsi(ioFile, canonicalRequestPath, project, request, channel, isCustomHost)
        return true
      }

      if (hasAccess(ioFile)) {
        FileResponses.sendFile(request, channel, ioFile)
      }
      else {
        Responses.sendStatus(HttpResponseStatus.FORBIDDEN, channel, request)
      }
    }
    else {
      val file = pathInfo.file!!
      val response = FileResponses.prepareSend(request, channel, file.timeStamp, file.path) ?: return true

      val keepAlive = Responses.addKeepAliveIfNeed(response, request)
      if (request.method() != HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, file.length)
      }

      channel.write(response)

      if (request.method() != HttpMethod.HEAD) {
        channel.write(ChunkedStream(file.inputStream))
      }

      val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE)
      }
    }
    return true
  }

  private fun processSsi(file: File, canonicalRequestPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, isCustomHost: Boolean) {
    var path = PathUtilRt.getParentPath(canonicalRequestPath.toString())
    if (!isCustomHost) {
      // remove project name - SSI resolves files only inside current project
      path = path.substring(path.indexOf('/', 1) + 1)
    }

    if (ssiProcessor == null) {
      ssiProcessor = SsiProcessor(false)
    }

    val buffer = channel.alloc().ioBuffer()
    val keepAlive: Boolean
    var releaseBuffer = true
    try {
      val lastModified = ssiProcessor!!.process(SsiExternalResolver(project, request, path, file.parentFile), FileUtil.loadFile(file), file.lastModified(), ByteBufUtf8Writer(buffer))
      val response = FileResponses.prepareSend(request, channel, lastModified, file.path) ?: return
      keepAlive = Responses.addKeepAliveIfNeed(response, request)
      if (request.method() != HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, buffer.readableBytes().toLong())
      }

      channel.write(response)

      if (request.method() != HttpMethod.HEAD) {
        releaseBuffer = false
        channel.write(buffer)
      }
    }
    finally {
      if (releaseBuffer) {
        buffer.release()
      }
    }

    val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
  }

  // deny access to .htaccess files
  private fun hasAccess(result: File) = !result.isDirectory && result.canRead() && !(result.isHidden || result.name.startsWith(".ht"))
}
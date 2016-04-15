package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.util.PathUtilRt
import com.intellij.util.isDirectory
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private class StaticFileHandler : WebServerFileHandler() {
  private var ssiProcessor: SsiProcessor? = null

  override fun process(pathInfo: PathInfo, canonicalPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, projectNameIfNotCustomHost: String?): Boolean {
    if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
      val ioFile = pathInfo.ioFile ?: File(pathInfo.file!!.path)

      val nameSequence = pathInfo.name
      //noinspection SpellCheckingInspection
      if (StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtml") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".stm") || StringUtilRt.endsWithIgnoreCase(nameSequence, ".shtm")) {
        processSsi(ioFile, PathUtilRt.getParentPath(canonicalPath.toString()), project, request, channel)
        return true
      }

      sendIoFile(channel, ioFile.toPath(), Paths.get(pathInfo.root.path), request)
    }
    else {
      val file = pathInfo.file!!
      if (file.`is`(VFileProperty.HIDDEN)) {
        Responses.sendStatus(HttpResponseStatus.FORBIDDEN, channel, request)
        return true
      }

      val response = FileResponses.prepareSend(request, channel, file.timeStamp, file.name) ?: return true

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

  private fun processSsi(file: File, path: String, project: Project, request: FullHttpRequest, channel: Channel) {
    if (ssiProcessor == null) {
      ssiProcessor = SsiProcessor(false)
    }

    val buffer = channel.alloc().ioBuffer()
    val keepAlive: Boolean
    var releaseBuffer = true
    try {
      val lastModified = ssiProcessor!!.process(SsiExternalResolver(project, request, path, file.parentFile), file, ByteBufUtf8Writer(buffer))
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
}

private fun sendIoFile(channel: Channel, file: Path, root: Path, request: HttpRequest) {
  if (file.isDirectory()) {
    Responses.sendStatus(HttpResponseStatus.FORBIDDEN, channel, request)
  }
  else if (checkAccess(channel, file, request, root)) {
    FileResponses.sendFile(request, channel, file.toFile())
  }
}

fun checkAccess(channel: Channel, file: Path, request: HttpRequest, root: Path): Boolean {
  var parent = file
  do {
    if (!hasAccess(parent)) {
      Responses.sendStatus(HttpResponseStatus.FORBIDDEN, channel, request)
      return false
    }
    parent = parent.parent ?: break
  }
  while (parent != root)
  return true
}

// deny access to .htaccess files
private fun hasAccess(result: Path) = Files.isReadable(result) && !(Files.isHidden(result) || result.fileName.toString().startsWith(".ht"))
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.Magnificator
import com.intellij.ui.components.ZoomableViewport
import com.intellij.ui.jcef.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.ImageZoomModel
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions
import org.intellij.images.ui.ImageComponentDecorator
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.swing.JComponent

class JCefImageViewer(private val myFile: VirtualFile,
                      mimeType: String) : UserDataHolderBase(), FileEditor, DocumentListener, ZoomableViewport, ImageComponentDecorator {

  companion object {
    private const val NAME = "SvgViewer"
    private const val HOST_NAME = "localhost"
    private const val PROTOCOL = "http"
    private const val VIEWER_PATH = "/index.html"
    private const val IMAGE_PATH = "/image"
    private const val VIEWER_URL = "$PROTOCOL://$HOST_NAME$VIEWER_PATH"
    private const val IMAGE_URL = "$PROTOCOL://$HOST_NAME$IMAGE_PATH"
    private val REJECTING_HANDLER: CefResourceHandler = object : CefResourceHandlerAdapter() {
      override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        callback.cancel()
        return false
      }
    }
  }

  private val myDocument: Document = FileDocumentManager.getInstance().getDocument(myFile)!!
  private val myCefClient: JBCefClient = JBCefApp.getInstance().createClient()
  private val myBrowser: JBCefBrowser = JBCefBrowserBuilder().setClient(myCefClient).build()
  private val myUIComponent: JCefImageViewerUI
  private var myState = ViewerState()

  override fun getComponent(): JComponent = myUIComponent
  override fun getPreferredFocusedComponent(): JComponent? = null
  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String = NAME

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun dispose() {}
  override fun documentChanged(event: DocumentEvent) = execute("reload()")

  override fun getMagnificator(): Magnificator? = myViewportDelegate.magnificator
  override fun magnificationStarted(at: Point) = myViewportDelegate.magnificationStarted(at)
  override fun magnificationFinished(magnification: Double) = myViewportDelegate.magnificationFinished(magnification)
  override fun magnify(magnification: Double) = myViewportDelegate.magnify(magnification)

  override fun setTransparencyChessboardVisible(visible: Boolean) {
    if (myState.status != ViewerState.Status.OK) return
    execute("setChessboardVisible(" + (if (visible) "true" else "false") + ");")
  }

  override fun setGridVisible(visible: Boolean) {
    if (myState.status != ViewerState.Status.OK) return
    execute("setGridVisible(${if (visible) "true" else "false"});")
  }

  override fun isTransparencyChessboardVisible(): Boolean = myState.chessboardEnabled
  override fun isEnabledForActionPlace(place: String): Boolean = ThumbnailViewActions.ACTION_PLACE != place
  override fun getZoomModel(): ImageZoomModel = ZOOM_MODEL
  override fun isGridVisible(): Boolean = myState.status == ViewerState.Status.OK && myState.gridEnabled

  private fun execute(script: String) = myBrowser.cefBrowser.executeJavaScript(script, myBrowser.cefBrowser.url, 0)

  private val ZOOM_MODEL: ImageZoomModel = object : ImageZoomModel {
    override fun getZoomFactor(): Double = myState.zoom
    override fun setZoomFactor(zoomFactor: Double) = execute("setZoom($zoomFactor);")

    override fun fitZoomToWindow() = execute("fitToViewport();")
    override fun zoomOut() = execute("zoomOut();")
    override fun zoomIn() = execute("zoomIn();")
    override fun setZoomLevelChanged(value: Boolean) {}
    override fun canZoomOut(): Boolean = myState.status == ViewerState.Status.OK && myState.zoomOutPossible
    override fun canZoomIn(): Boolean = myState.status == ViewerState.Status.OK && myState.zoomInPossible
    override fun isZoomLevelChanged(): Boolean = myState.status != ViewerState.Status.OK || !myState.fittedToViewport
  }

  private val myViewportDelegate: ZoomableViewport = object : ZoomableViewport {
    private var currentZoom = 1.0
    private var x = 0.5
    private var y = 0.5
    override fun getMagnificator(): Magnificator = Magnificator { _: Double, _: Point? -> null }

    override fun magnificationStarted(at: Point) {
      currentZoom = myState.zoom
      x = (at.x - myState.canvasBBox.x) / myState.canvasBBox.width
      y = (at.y - myState.canvasBBox.y) / myState.canvasBBox.height
    }

    override fun magnificationFinished(magnification: Double) {}
    override fun magnify(magnification: Double) {
      val scale = if (magnification < 0) 1f / (1 - magnification) else 1 + magnification
      execute("setZoom(${currentZoom * scale}, {'x': ${x}, 'y': ${y}});")
    }
  }

  private val jsonParser = Json { ignoreUnknownKeys = true }

  init {
    myDocument.addDocumentListener(this)
    myCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
      override fun getResourceRequestHandler(browser: CefBrowser,
                                             frame: CefFrame,
                                             request: CefRequest,
                                             isNavigation: Boolean,
                                             isDownload: Boolean,
                                             requestInitiator: String,
                                             disableDefaultHandling: BoolRef): CefResourceRequestHandler {
        return object : CefResourceRequestHandlerAdapter() {
          override fun getResourceHandler(browser: CefBrowser, frame: CefFrame, request: CefRequest): CefResourceHandler {
            val url: URL = try {
              URL(request.url)
            }
            catch (e: MalformedURLException) {
              Logger.getInstance(JCefImageViewer::class.java).warn("Failed to parse URL", e)
              return REJECTING_HANDLER
            }
            if (url.host != HOST_NAME || url.protocol != PROTOCOL) {
              return REJECTING_HANDLER
            }
            val handler = when (url.path) {
                            VIEWER_PATH -> javaClass.getResourceAsStream("resources/image_viewer.html")?.let {
                              MyResourceHandler(it, "text/html")
                            }
                            IMAGE_PATH -> {
                              var stream: InputStream? = null
                              try {
                                stream = if (FileUtilRt.isTooLarge(myFile.length)) myFile.inputStream
                                else ByteArrayInputStream(myDocument.text.toByteArray(
                                  StandardCharsets.UTF_8))
                              }
                              catch (e: IOException) {
                                Logger.getInstance(
                                  JCefImageViewer::class.java).warn("Failed to read the file", e)
                              }
                              stream?.let { MyResourceHandler(it, mimeType) }
                            }
                            else -> null
                          } ?: return REJECTING_HANDLER
            Disposer.register(this@JCefImageViewer, handler)
            return handler
          }
        }
      }
    }, myBrowser.cefBrowser)
    @Suppress("DEPRECATION") val query = JBCefJSQuery.create(myBrowser)
    myUIComponent = JCefImageViewerUI(myBrowser.component, this)
    query.addHandler { s: String ->
      myState = jsonParser.decodeFromString(s)
      myUIComponent.setInfo(
        ImagesBundle.message("image.info.svg",
                             myState.imageSize.width, myState.imageSize.height, StringUtil.formatFileSize(myFile.length)))
      null
    }
    myCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        execute("setImageUrl('$IMAGE_URL');")
        execute("send_info = function(info_text) {${query.inject("info_text")};}")
      }
    }, myBrowser.cefBrowser)
    if (RegistryManager.getInstance().`is`("ide.browser.jcef.svg-viewer.debug")) {
      myBrowser.loadURL("$VIEWER_URL?debug")
    }
    else {
      myBrowser.loadURL(VIEWER_URL)
    }
  }

  private class MyResourceHandler constructor(val myStream: InputStream, val myMimeType: String) : CefResourceHandler, Disposable {
    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
      callback.Continue()
      return true
    }

    override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
      response.mimeType = myMimeType
      response.status = 200
    }

    override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
      try {
        bytesRead.set(myStream.read(dataOut, 0, bytesToRead))
        if (bytesRead.get() != -1) {
          return true
        }
      }
      catch (e: IOException) {
        Logger.getInstance(JCefImageViewer::class.java).warn("Failed to read from the stream", e)
        callback.cancel()
      }
      bytesRead.set(0)
      Disposer.dispose(this)
      return false
    }

    override fun cancel() {
      Disposer.dispose(this)
    }

    override fun dispose() {
      try {
        myStream.close()
      }
      catch (e: IOException) {
        Logger.getInstance(JCefImageViewer::class.java).warn("Failed to close the stream", e)
      }
    }
  }

  @Serializable
  private data class ViewerState(
    val status: Status = Status.OK,
    val zoom: Double = 0.0,
    val imageSize: Size = Size(0, 0),
    val canvasBBox: Rectangle = Rectangle(0.0, 0.0, 0.0, 0.0),
    val zoomInPossible: Boolean = false,
    val zoomOutPossible: Boolean = false,
    val fittedToViewport: Boolean = false,
    val realSize: Boolean = false,
    val gridEnabled: Boolean = false,
    val chessboardEnabled: Boolean = false
  ) {
    @Serializable
    enum class Status { OK, FAILED }

    @Serializable
    data class Rectangle(val x: Double, val y: Double, val width: Double, val height: Double)

    @Serializable
    data class Size(val width: Int, val height: Int)
  }
}
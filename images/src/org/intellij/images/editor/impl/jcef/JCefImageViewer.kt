// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.ImageZoomModel
import org.intellij.images.editor.impl.ImageFileEditorState
import org.intellij.images.options.OptionsManager
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions
import org.intellij.images.thumbnail.actions.ShowBorderAction
import org.intellij.images.ui.ImageComponentDecorator
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.beans.PropertyChangeListener
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.SwingUtilities

class JCefImageViewer(private val myFile: VirtualFile,
                      mimeType: String) : UserDataHolderBase(), FileEditor, DocumentListener, ImageComponentDecorator {

  companion object {
    private const val NAME = "SvgViewer"
    private const val HOST_NAME = "localhost"
    private const val PROTOCOL = "http"
    private const val VIEWER_PATH = "/index.html"
    private const val IMAGE_PATH = "/image"
    private const val VIEWER_URL = "$PROTOCOL://$HOST_NAME$VIEWER_PATH"
    private const val IMAGE_URL = "$PROTOCOL://$HOST_NAME$IMAGE_PATH"
  }

  private val myDocument: Document = FileDocumentManager.getInstance().getDocument(myFile)!!
  private val myCefClient: JBCefClient = JBCefApp.getInstance().createClient()
  private val myBrowser: JBCefBrowser = JBCefBrowserBuilder().setClient(myCefClient).build()
  private val myUIComponent: JCefImageViewerUI
  private val myViewerStateJSQuery: JBCefJSQuery

  private val myInitializer: AtomicReference<() -> Unit> = AtomicReference()
  private var myState = ViewerState()

  override fun getComponent(): JComponent = myUIComponent
  override fun getPreferredFocusedComponent(): JComponent = myBrowser.cefBrowser.uiComponent as JComponent
  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String = NAME

  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getFile(): VirtualFile = myFile
  override fun getState(level: FileEditorStateLevel): FileEditorState =
    ImageFileEditorState(myState.chessboardEnabled, myState.gridEnabled, myState.zoom, !myState.realSize)

  // TODO: Simplify initialization
  override fun setState(state: FileEditorState) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater { setState(state) }
      return
    }
    val initializer = myInitializer.get()

    if (initializer != null && myInitializer.compareAndSet(initializer) { initializer(); setState(state) }) {
      return
    }

    if (state is ImageFileEditorState) {
      isTransparencyChessboardVisible = state.isBackgroundVisible
      isGridVisible = state.isGridVisible
      execute("setZoom(${state.zoomFactor});")
    }
  }

  override fun dispose() {
    myViewerStateJSQuery.clearHandlers()
    myDocument.removeDocumentListener(this)
  }

  override fun documentChanged(event: DocumentEvent) = execute("reload()")

  fun setZoom(scale: Double, at: Point) {
    execute("setZoom(${scale}, {'x': ${at.x}, 'y': ${at.y}});")
  }

  override fun setTransparencyChessboardVisible(visible: Boolean) {
    if (myState.status != ViewerState.Status.OK) return
    execute("setChessboardVisible(" + (if (visible) "true" else "false") + ");")
  }

  override fun setGridVisible(visible: Boolean) {
    if (myState.status != ViewerState.Status.OK) return
    execute("setGridVisible(${if (visible) "true" else "false"});")
  }

  override fun setBorderVisible(visible: Boolean) {
    execute("setBorderVisible(${if (visible) "true" else "false"});")
  }

  override fun isTransparencyChessboardVisible(): Boolean = myState.chessboardEnabled
  override fun isEnabledForActionPlace(place: String): Boolean = ThumbnailViewActions.ACTION_PLACE != place
  override fun getZoomModel(): ImageZoomModel = ZOOM_MODEL
  override fun isGridVisible(): Boolean = myState.status == ViewerState.Status.OK && myState.gridEnabled
  fun getZoom() = myState.zoom

  private fun execute(/*language=javascript*/ script: String) = myBrowser.cefBrowser.executeJavaScript(script, myBrowser.cefBrowser.url, 0)

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

  private val jsonParser = Json { ignoreUnknownKeys = true }

  init {
    myDocument.addDocumentListener(this)
    val resourceRequestHandler = CefLocalRequestHandler(PROTOCOL, HOST_NAME)

    resourceRequestHandler.addResource(VIEWER_PATH) {
      javaClass.getResourceAsStream("resources/image_viewer.html")?.let {
        CefStreamResourceHandler(it, "text/html", this@JCefImageViewer)
      }
    }

    resourceRequestHandler.addResource(IMAGE_PATH) {
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
      stream?.let { CefStreamResourceHandler(it, mimeType, this@JCefImageViewer) }
    }

    myCefClient.addRequestHandler(resourceRequestHandler, myBrowser.cefBrowser)

    myUIComponent = JCefImageViewerUI(myBrowser.cefBrowser.uiComponent, this)
    Disposer.register(this, myUIComponent)

    @Suppress("DEPRECATION")
    myViewerStateJSQuery = JBCefJSQuery.create(myBrowser)
    myViewerStateJSQuery.addHandler { s: String ->
      try {
        myState = jsonParser.decodeFromString(s)
      }
      catch (_: Exception) {
      }
      myUIComponent.setInfo(
        ImagesBundle.message("image.info.svg",
                             myState.imageSize.width, myState.imageSize.height, StringUtil.formatFileSize(myFile.length)))
      if (myState.status == ViewerState.Status.ERROR) {
        myUIComponent.showError()
      }
      else {
        myUIComponent.showImage()
      }
      null
    }

    myInitializer.set {
      execute("send_info = function(info_text) {${myViewerStateJSQuery.inject("info_text")};}")
      execute("setImageUrl('$IMAGE_URL');")
      isGridVisible = OptionsManager.getInstance().options.editorOptions.gridOptions.isShowDefault
      isTransparencyChessboardVisible = OptionsManager.getInstance().options.editorOptions.transparencyChessboardOptions.isShowDefault
      setBorderVisible(ShowBorderAction.isBorderVisible())
    }

    myCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        SwingUtilities.invokeLater { myInitializer.getAndSet(null).invoke() }
      }
    }, myBrowser.cefBrowser)

    if (RegistryManager.getInstance().`is`("ide.browser.jcef.svg-viewer.debug")) {
      myBrowser.loadURL("$VIEWER_URL?debug")
    }
    else {
      myBrowser.loadURL(VIEWER_URL)
    }
  }

  @Serializable
  private data class ViewerState(
    val status: Status = Status.OK,
    val error_message: String = "",
    val zoom: Double = 0.0,
    val viewportSize: Size = Size(0, 0),
    val imageSize: Size = Size(0, 0),
    val zoomInPossible: Boolean = false,
    val zoomOutPossible: Boolean = false,
    val fittedToViewport: Boolean = false,
    val realSize: Boolean = false,
    val gridEnabled: Boolean = false,
    val chessboardEnabled: Boolean = false
  ) {
    @Serializable
    enum class Status { OK, ERROR }

    @Serializable
    data class Size(val width: Int, val height: Int)
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.ScrollBarPainter
import com.intellij.ui.jcef.*
import com.intellij.util.IncorrectOperationException
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
import org.intellij.lang.annotations.Language
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
    private const val SCROLLBARS_CSS_PATH = "/scrollbars.css"

    private const val VIEWER_URL = "$PROTOCOL://$HOST_NAME$VIEWER_PATH"
    private const val IMAGE_URL = "$PROTOCOL://$HOST_NAME$IMAGE_PATH"
    private const val SCROLLBARS_STYLE_URL = "$PROTOCOL://$HOST_NAME$SCROLLBARS_CSS_PATH"
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
  override fun isValid(): Boolean = myState.status == ViewerState.Status.OK

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getFile(): VirtualFile = myFile
  override fun getState(level: FileEditorStateLevel): FileEditorState = ImageFileEditorState(myState.chessboardEnabled, myState.gridEnabled,
                                                                                             myState.zoom, !myState.realSize)

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
  private fun execute(@Language("javascript") script: String) = myBrowser.cefBrowser.executeJavaScript(script, myBrowser.cefBrowser.url, 0)

  private val ZOOM_MODEL: ImageZoomModel = object : ImageZoomModel {
    override fun getZoomFactor(): Double = myState.zoom
    override fun setZoomFactor(zoomFactor: Double) = execute("setZoom($zoomFactor);")
    override fun fitZoomToWindow() = execute("fitToViewport();")
    override fun zoomOut() = execute("zoomOut();")
    override fun zoomIn() = execute("zoomIn();")
    override fun setZoomLevelChanged(value: Boolean) {}
    override fun canZoomOut(): Boolean = myState.status == ViewerState.Status.OK && myState.zoomOutPossible
    override fun canZoomIn(): Boolean = myState.status == ViewerState.Status.OK && myState.zoomInPossible
    override fun isZoomLevelChanged(): Boolean = myState.status == ViewerState.Status.ERROR || !myState.fittedToViewport
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
        else ByteArrayInputStream(myDocument.text.toByteArray(StandardCharsets.UTF_8))
      }
      catch (e: IOException) {
        Logger.getInstance(JCefImageViewer::class.java).warn("Failed to read the file", e)
      }

      var resourceHandler: CefStreamResourceHandler? = null
      stream?.let {
        try {
          resourceHandler = CefStreamResourceHandler(it, mimeType, this@JCefImageViewer)
        }
        catch (_: IncorrectOperationException) { // The viewer has been disposed just return null that will reject all requests
        }

        return@addResource resourceHandler
      }
    }

    resourceRequestHandler.addResource(SCROLLBARS_CSS_PATH) {
      CefStreamResourceHandler(ByteArrayInputStream(buildScrollbarsStyle().toByteArray(StandardCharsets.UTF_8)), "text/css", this)
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
        myUIComponent.showError()
        return@addHandler JBCefJSQuery.Response(null, 255, "Failed to parse the viewer state")
      }

      SwingUtilities.invokeLater {
        myUIComponent.setInfo(ImagesBundle.message("image.info.svg", myState.imageSize.width, myState.imageSize.height,
                                                   StringUtil.formatFileSize(myFile.length)))
        if (myState.status == ViewerState.Status.ERROR) {
          myUIComponent.showError()
        }
        else {
          myUIComponent.showImage()
        }
      }
      JBCefJSQuery.Response(null)
    }

    myInitializer.set {
      execute("sendInfo = function(info_text) {${myViewerStateJSQuery.inject("info_text")};}")
      execute("setImageUrl('$IMAGE_URL');")
      isGridVisible = OptionsManager.getInstance().options.editorOptions.gridOptions.isShowDefault
      isTransparencyChessboardVisible = OptionsManager.getInstance().options.editorOptions.transparencyChessboardOptions.isShowDefault
      setBorderVisible(ShowBorderAction.isBorderVisible())
      reloadStyles()
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

    ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(EditorColorsManager.TOPIC,
                                                                             EditorColorsListener { reloadStyles() })
  }

  @Serializable
  private data class ViewerState(val status: Status = Status.OK,
                                 val zoom: Double = 0.0,
                                 val viewportSize: Size = Size(0, 0),
                                 val imageSize: Size = Size(0, 0),
                                 val zoomInPossible: Boolean = false,
                                 val zoomOutPossible: Boolean = false,
                                 val fittedToViewport: Boolean = false,
                                 val realSize: Boolean = false,
                                 val gridEnabled: Boolean = false,
                                 val chessboardEnabled: Boolean = false) {
    @Serializable
    enum class Status { OK, ERROR }

    @Serializable
    data class Size(val width: Int, val height: Int)
  }

  private fun getColorCSS(key: ColorKey): String {
    val colorScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    return (colorScheme.getColor(key) ?: key.defaultColor).let { "rgba(${it.red}, ${it.blue}, ${it.green}, ${it.alpha / 255.0})" }
  }

  private fun buildScrollbarsStyle(): String {
    val background = getColorCSS(ScrollBarPainter.BACKGROUND)
    val trackColor = getColorCSS(ScrollBarPainter.TRACK_OPAQUE_BACKGROUND)
    val trackColorHovered = getColorCSS(ScrollBarPainter.TRACK_OPAQUE_HOVERED_BACKGROUND)
    val thumbColor = getColorCSS(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND)
    val thumbHoveredColor = getColorCSS(ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND)
    val thumbBorder = getColorCSS(ScrollBarPainter.THUMB_OPAQUE_FOREGROUND)
    val thumbBorderHovered = getColorCSS(ScrollBarPainter.THUMB_OPAQUE_HOVERED_FOREGROUND)

    val trackSize = if (SystemInfo.isMac) "14px" else "10px"
    val thumbBorderSize = if (SystemInfo.isMac) "3px" else "1px"
    val thumbRadius = if (SystemInfo.isMac) "14px" else "0"

    return /*language=css*/ """
      ::-webkit-scrollbar {
        width: $trackSize;
        height: $trackSize;
        background-color: $background;
      }
      
      /*!* background of the scrollbar except button or resizer *!*/
      ::-webkit-scrollbar-track {
        background-color:$trackColor;
      }
      
      ::-webkit-scrollbar-track:hover {
        background-color:$trackColorHovered;
      }
      
      /*!* scrollbar itself *!*/
      ::-webkit-scrollbar-thumb {
        background-color:$thumbColor;
        border-radius:$thumbRadius;
        border-width: $thumbBorderSize;
        border-style: solid;
        border-color: $trackColor;
        background-clip: padding-box;
        outline: 1px solid $thumbBorder;
        outline-offset: -$thumbBorderSize;
      }
      
      ::-webkit-scrollbar-thumb:hover {
        background-color:$thumbHoveredColor;
        border-radius:$thumbRadius;
        border-width: $thumbBorderSize;
        border-style: solid;
        border-color: $trackColor;
        background-clip: padding-box;
        outline: 1px solid $thumbBorderHovered;
        outline-offset: -$thumbBorderSize;
      }
      
      /* set button(top and bottom of the scrollbar) */
      ::-webkit-scrollbar-button {
        display:none;
      }
      
      ::-webkit-scrollbar-corner {
        background-color: $background;
      }
    """.trimIndent()
  }

  private fun reloadStyles() {
    execute("""
      loadScrollbarsStyle('$SCROLLBARS_STYLE_URL');
    """.trimIndent())
  }
}
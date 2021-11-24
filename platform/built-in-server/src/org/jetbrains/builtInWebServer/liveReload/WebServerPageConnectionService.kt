// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer.liveReload

import com.google.common.net.HttpHeaders
import com.intellij.CommonBundle
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.browsers.ReloadMode
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.ide.browsers.WebBrowserXmlService
import com.intellij.ide.browsers.actions.WebPreviewFileEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.GotItTooltip
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.CharsetUtil
import org.jetbrains.ide.BuiltInServerBundle
import org.jetbrains.io.jsonRpc.Client
import org.jetbrains.io.jsonRpc.ClientManager
import org.jetbrains.io.jsonRpc.JsonRpcServer
import org.jetbrains.io.jsonRpc.MessageServer
import org.jetbrains.io.webSocket.WebSocketClient
import org.jetbrains.io.webSocket.WebSocketHandshakeHandler
import java.awt.Point
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Provides support for automatic reloading of pages opened on built-in web server on related files modification.
 *
 * Implementation:
 *
 * <-- html page with [.RELOAD_URL_PARAM] is requested
 * --> response with modified html which opens WebSocket connection listening for reload message
 *
 * <-- script or other resource of html is requested
 * start listening for related file changes
 *
 * file is changed
 * --> reload associated pages by sending WebSocket message
 */
@Service(Service.Level.APP)
class WebServerPageConnectionService {
  private val RELOAD_PAGE_MESSAGE = Unpooled.copiedBuffer(RELOAD_WS_REQUEST, CharsetUtil.US_ASCII).asReadOnly()
  private var myServer: ClientManager? = null
  private var myRpcServer: JsonRpcServer? = null
  private val myState = RequestedPagesState()

  /**
   * @return suffix to add to requested file in response
   */
  fun fileRequested(request: FullHttpRequest, onlyIfHtmlFile: Boolean, fileSupplier: Supplier<out VirtualFile?>): String? {
    var reloadRequest = ReloadMode.DISABLED
    val uri = request.uri()
    if (uri != null && uri.contains(RELOAD_URL_PARAM)) {
      val decoder = QueryStringDecoder(uri)
      reloadRequest = decoder.parameters()[RELOAD_URL_PARAM]?.get(0)?.let { ReloadMode.valueOf(it) } ?: ReloadMode.DISABLED
    }
    if (reloadRequest == ReloadMode.DISABLED && myState.isEmpty) return null
    val file = fileSupplier.get()
    if (reloadRequest == ReloadMode.DISABLED && file != null) {
      myState.resourceRequested(request, file)
      return null
    }
    if (reloadRequest == ReloadMode.DISABLED) return null
    if (file == null) {
      LOGGER.warn("VirtualFile for $uri isn't resolved, reload on save can't be started")
      return null
    }
    if (onlyIfHtmlFile && !WebBrowserXmlService.getInstance().isHtmlFile(file)) return null
    val clientId = myState.pageRequested(uri, file, reloadRequest)

    val optionalConsoleLog =
      if (LOGGER.isDebugEnabled) "\nconsole.log('JetBrains Reload on Save script loaded, clientId = $clientId');" else ""
    //language=HTML
    return """
<script>
(function() {$optionalConsoleLog
  var ws = new WebSocket('ws://' + window.location.host + '/jb-server-page?$RELOAD_CLIENT_ID_URL_PARAMETER=$clientId');
  ws.onmessage = function (msg) {
      if (msg.data === 'reload') {
          window.location.reload();
      }
      if (msg.data.startsWith('$UPDATE_LINK_WS_REQUEST_PREFIX')) {
          var messageId = msg.data.substring(${UPDATE_LINK_WS_REQUEST_PREFIX.length});
          var links = document.getElementsByTagName('link');
          for (var i = 0; i < links.length; i++) {
              var link = links[i];
              if (link.rel !== 'stylesheet') continue;
              var clonedLink = link.cloneNode(true);
              var newHref = link.href.replace(/(&|\?)$UPDATE_LINKS_ID_URL_PARAMETER=\d+/, "$1$UPDATE_LINKS_ID_URL_PARAMETER=" + messageId);
              if (newHref !== link.href) {
                clonedLink.href = newHref;
              }
              else {
                var indexOfQuest = newHref.indexOf('?');
                if (indexOfQuest >= 0) {
                  // to support ?foo#hash 
                  clonedLink.href = newHref.substring(0, indexOfQuest + 1) + '$UPDATE_LINKS_ID_URL_PARAMETER=' + messageId + '&' + 
                                    newHref.substring(indexOfQuest + 1);
                }
                else {
                  clonedLink.href += '?' + '$UPDATE_LINKS_ID_URL_PARAMETER=' + messageId;
                }
              }
              link.replaceWith(clonedLink);
          }
      }
  };
})();
</script>
    """.trimIndent()
  }

  fun reloadRelatedClients(modifiedFiles: List<VirtualFile>): AsyncFileListener.ChangeApplier? {
    myServer ?: return null
    if (myState.isRequestedFileWithoutReferrerModified(modifiedFiles)) {
      return object : AsyncFileListener.ChangeApplier {
        override fun afterVfsChange() {
          showGotItTooltip(modifiedFiles)
          myState.clear()
          val server = myServer
          server?.send<Any>(-1, RELOAD_PAGE_MESSAGE.retainedDuplicate(), null)
        }
      }
    }

    val affectedClients = myState.collectAffectedPages(modifiedFiles)
    return if (affectedClients.isEmpty()) null
    else object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        showGotItTooltip(modifiedFiles)
        for ((requestedPage, affectedFiles) in affectedClients) {
          val clientFuture = requestedPage.myClient
          if (affectedFiles.isEmpty()) {
            LOGGER.debug("Reload page for clientId = ${requestedPage.myClientId} scheduled")
            clientFuture.thenAccept { client: WebSocketClient? ->
              LOGGER.debug("Reload page for clientId = ${requestedPage.myClientId} sent")
              client!!.send(RELOAD_PAGE_MESSAGE.retainedDuplicate())
            }
          }
          else {
            val messageId = myState.getNextMessageId()
            myState.linkedFilesRequested(messageId, affectedFiles)
            for (affectedFile in affectedFiles) {
              LOGGER.debug("Reload file ${affectedFile.name} for clientId = ${requestedPage.myClientId}")
              clientFuture.thenAccept { client: WebSocketClient? ->
                val message = UPDATE_LINK_WS_REQUEST_PREFIX + messageId
                client!!.send(Unpooled.copiedBuffer(message, Charsets.UTF_8))
              }
            }
            JobScheduler.getScheduler().schedule({
              if (!myState.isAllLinkedFilesReloaded(messageId)) {
                LOGGER.debug("Some files weren't reloaded, reload whole page for clientId = ${requestedPage.myClientId}")
                clientFuture.thenAccept { client: WebSocketClient? -> client!!.send(RELOAD_PAGE_MESSAGE.retainedDuplicate()) }
              }
            }, 1, TimeUnit.SECONDS)
          }
        }
      }
    }
  }

  private fun clientConnected(client: WebSocketClient, clientId: Int) {
    myState.clientConnected(client, clientId)
  }

  private fun clientDisconnected(client: WebSocketClient) {
    myState.clientDisconnected(client)
  }

  private fun showGotItTooltip(modifiedFiles: List<VirtualFile>) {
    val gotItTooltip = GotItTooltip(SERVER_RELOAD_TOOLTIP_ID, BuiltInServerBundle.message("reload.on.save.got.it.content"), myServer!!)
    if (!gotItTooltip.canShow() || WebPreviewFileEditor.isPreviewOpened()) return

    if (WebBrowserManager.BROWSER_RELOAD_MODE_DEFAULT !== ReloadMode.RELOAD_ON_SAVE) {
      Logger.getInstance(WebServerPageConnectionService::class.java).error(
        "Default value for " + BuiltInServerBundle.message("reload.on.save.got.it.title") + " has changed, tooltip is outdated.")
      return
    }
    if (WebBrowserManager.getInstance().webServerReloadMode !== ReloadMode.RELOAD_ON_SAVE) {
      // changed before gotIt was shown
      return
    }

    gotItTooltip
      .withHeader(BuiltInServerBundle.message("reload.on.save.got.it.title"))
      .withPosition(Balloon.Position.above)

    val editorComponent = IdeFocusManager.getGlobalInstance().focusOwner as? EditorComponentImpl ?: return
    val editorFile = FileDocumentManager.getInstance().getFile(editorComponent.editor.document)
    if (!modifiedFiles.contains(editorFile)) return

    gotItTooltip.withLink(CommonBundle.message("action.text.configure.ellipsis")) {
      ShowSettingsUtil.getInstance().showSettingsDialog(
        editorComponent.editor.project,
        { (it as? ConfigurableWrapper)?.id == "reference.settings.ide.settings.web.browsers" },
        null)
    }

    gotItTooltip.show(editorComponent) { component, _ ->
      val editor = (component as? EditorComponentImpl)?.editor ?: return@show Point(0,0)
      val p = editor.visualPositionToXY(editor.caretModel.currentCaret.visualPosition)
      val v = component.visibleRect
      Point(p.x.coerceIn(v.x, v.x + v.width), p.y.coerceIn(v.y, v.y + v.height))
    }
  }

  internal class WebServerPageRequestHandler : WebSocketHandshakeHandler() {
    override fun serverCreated(server: ClientManager) {
      val instance = instance
      instance.myServer = server
      instance.myRpcServer = JsonRpcServer(server)
    }

    override fun isSupported(request: FullHttpRequest): Boolean {
      return super.isSupported(request) && checkPrefix(request.uri(), RELOAD_WS_URL_PREFIX)
    }

    override fun getMessageServer(): MessageServer {
      return instance.myRpcServer!!
    }

    override fun connected(client: Client, parameters: Map<String, List<String>>?) {
      if (parameters == null || client !is WebSocketClient) return
      val ids = parameters[RELOAD_CLIENT_ID_URL_PARAMETER]!!
      if (ids.size != 1) return
      val id = StringUtil.parseInt(ids[0], -1)
      if (id == -1) return
      instance.clientConnected(client, id)
    }

    override fun disconnected(client: Client) {
      if (client is WebSocketClient) {
        instance.clientDisconnected(client)
      }
    }
  }

  private class RequestedPagesState {
    private var myLastClientId = 0
    private val myRequestedFilesWithoutReferrer: MutableSet<VirtualFile?> = HashSet()
    private val myRequestedPages = MultiMap<String, RequestedPage?>()

    /**
     * Start to listen for files changes on HTTP request with RELOAD_URL_PARAM, stop on last WS client disconnect
     */
    private var myFileListenerDisposable: Disposable? = null
    private var myDocumentListenerDisposable: Disposable? = null

    private val myMessageId = AtomicInteger(0)
    private val myLinkedFilesToReload: MutableMap<Int, MutableSet<VirtualFile>> = HashMap()

    @Synchronized
    fun clear() {
      LOGGER.debug("Requested pages cleared")
      for (requestedPage in myRequestedPages.values()) {
        requestedPage!!.myClient.cancel(false)
      }
      myRequestedPages.clear()
      cleanupIfEmpty()
    }

    @Synchronized
    fun resourceRequested(request: FullHttpRequest, file: VirtualFile) {
      val referer = request.headers()[HttpHeaders.REFERER]
      var associatedPageFound = false
      try {
        val refererUri = URI.create(referer)
        val refererWithoutHost = refererUri.path + "?" + refererUri.query
        val pages = myRequestedPages[refererWithoutHost]
        for (page in pages) {
          page!!.myFiles.add(file)
        }
        associatedPageFound = !pages.isEmpty()

        val messageIds = QueryStringDecoder(request.uri()).parameters()[UPDATE_LINKS_ID_URL_PARAMETER]
        if (messageIds != null && messageIds.size == 1) {
          myLinkedFilesToReload[messageIds[0].toInt()]?.remove(file)
        }
      }
      catch (ignore: Throwable) {
      }
      if (!associatedPageFound) {
        myRequestedFilesWithoutReferrer.add(file)
      }
    }

    @Synchronized
    fun pageRequested(uri: String, file: VirtualFile, reloadMode: ReloadMode): Int {
      LOGGER.assertTrue(myRequestedPages.isEmpty == (myFileListenerDisposable == null),
        "isEmpty: ${myRequestedPages.isEmpty}, disposable is null: ${myFileListenerDisposable == null}")

      if (myFileListenerDisposable == null) {
        val disposable = Disposer.newDisposable(ApplicationManager.getApplication(), "RequestedPagesState.myFileListenerDisposable")
        VirtualFileManager.getInstance().addAsyncFileListener(WebServerFileContentListener(), disposable)
        myFileListenerDisposable = disposable
      }
      if (reloadMode == ReloadMode.RELOAD_ON_CHANGE && myDocumentListenerDisposable == null) {
        val disposable = Disposer.newDisposable(ApplicationManager.getApplication(), "RequestedPagesState.myDocumentListenerDisposable")
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
          override fun documentChanged(event: DocumentEvent) {
            val virtualFile = FileDocumentManager.getInstance().getFile(event.document)
            if (isTrackedFile(virtualFile)) {
              FileDocumentManager.getInstance().saveDocument(event.document)
            }
          }
        }, disposable)
        myDocumentListenerDisposable = disposable
      }
      val clientId = ++myLastClientId
      LOGGER.debug("Page is requested for $uri, clientId = $clientId")
      val newPage = RequestedPage(clientId, file, reloadMode)
      myRequestedPages.putValue(uri, newPage)
      JobScheduler.getScheduler().schedule({ stopWaitingForClient(uri, newPage) }, 30, TimeUnit.SECONDS)
      return clientId
    }

    private fun isTrackedFile(virtualFile: VirtualFile?) = myRequestedFilesWithoutReferrer.contains(virtualFile)
      || myRequestedPages.values().stream().filter{ p -> p?.reloadMode == ReloadMode.RELOAD_ON_CHANGE }
                                                             .anyMatch { it!!.myFiles.contains(virtualFile) }


    @Synchronized
    private fun stopWaitingForClient(uri: String, page: RequestedPage) {
      if (page.myClient.isDone) return
      page.myClient.cancel(false)
      myRequestedPages.remove(uri, page)
      cleanupIfEmpty()
      LOGGER.error("Timeout on waiting for WebSocket client for $uri, clientId = ${page.myClientId}")
    }

    @Synchronized
    fun clientConnected(client: WebSocketClient, clientId: Int) {
      val requestedPage = ContainerUtil.find(myRequestedPages.values()) { it?.myClientId == clientId }
      if (requestedPage != null) {
        LOGGER.debug("WebSocket client connected for clientId = $clientId")
        requestedPage.myClient.complete(client)
      }
      else {
        // possible if 'clear' happens between page is registered and connected
        LOGGER.info("Cannot find client for clientId = $clientId")
      }
    }

    @Synchronized
    fun clientDisconnected(client: WebSocketClient) {
      var requestedPageKey: String? = null
      var requestedPage: RequestedPage? = null
      for ((key, value) in myRequestedPages.entrySet()) {
        for (page in value) {
          if (page!!.myClient.isDone && page.myClient.getNow(null) === client) {
            requestedPageKey = key
            requestedPage = page
            break
          }
        }
      }
      LOGGER.debug("WebSocket client disconnected for URI $requestedPageKey")
      if (requestedPageKey != null) {
        myRequestedPages.remove(requestedPageKey, requestedPage)
      }
      cleanupIfEmpty()
    }

    private fun cleanupIfEmpty() {
      if (myRequestedPages.isEmpty) {
        myRequestedFilesWithoutReferrer.clear()
        if (myFileListenerDisposable != null) {
          Disposer.dispose(Objects.requireNonNull(myFileListenerDisposable)!!)
          myFileListenerDisposable = null
        }
        if (myDocumentListenerDisposable != null) {
          Disposer.dispose(Objects.requireNonNull(myDocumentListenerDisposable)!!)
          myDocumentListenerDisposable = null
        }
      }
    }

    @Synchronized
    fun isRequestedFileWithoutReferrerModified(files: List<VirtualFile?>): Boolean {
      for (file in files) {
        if (myRequestedFilesWithoutReferrer.contains(file)) return true
      }
      return false
    }

    /**
     * @return For each affected clients either a list of linked files to reload, or reload the whole page if list is empty
     */
    @Synchronized
    fun collectAffectedPages(files: List<VirtualFile>): Map<RequestedPage, List<VirtualFile>> {
      val result = HashMap<RequestedPage, List<VirtualFile>>()
      for (modifiedFile in files) {
        for (requestedPage in myRequestedPages.values()) {
          if (requestedPage!!.myFiles.contains(modifiedFile)) {
            if (StringUtil.equalsIgnoreCase(modifiedFile.extension, "css")) {
              if (!result.containsKey(requestedPage)) {
                result[requestedPage] = mutableListOf(modifiedFile)
              }
              else if (result[requestedPage]!!.isNotEmpty()) {
                (result[requestedPage] as MutableList).add(modifiedFile)
              }
              // else other resource was requested, so reload whole page
            }
            else {
              result[requestedPage] = emptyList()
            }
          }
        }
      }
      return result
    }

    fun getNextMessageId(): Int {
      return myMessageId.incrementAndGet()
    }

    @Synchronized
    fun linkedFilesRequested(messageId: Int, affectedFiles: List<VirtualFile>) {
      myLinkedFilesToReload[messageId] = HashSet(affectedFiles)
    }

    @Synchronized
    fun isAllLinkedFilesReloaded(messageId: Int): Boolean {
      val isEmpty = myLinkedFilesToReload[messageId]?.isEmpty() ?: true
      myLinkedFilesToReload.remove(messageId)
      return isEmpty
    }

    @get:Synchronized
    val isEmpty: Boolean
      get() = myRequestedPages.isEmpty
  }

  private class RequestedPage(val myClientId: Int, requestedPageFile: VirtualFile, val reloadMode: ReloadMode) {
    val myFiles: MutableSet<VirtualFile?> = HashSet()
    val myClient = CompletableFuture<WebSocketClient?>()

    init {
      myFiles.add(requestedPageFile)
    }
  }

  companion object {
    const val RELOAD_URL_PARAM = "_ij_reload"
    const val SERVER_RELOAD_TOOLTIP_ID = "builtin.web.server.reload.on.save"
    private const val RELOAD_WS_REQUEST = "reload"
    private const val UPDATE_LINK_WS_REQUEST_PREFIX = "update-css "
    private const val RELOAD_WS_URL_PREFIX = "jb-server-page"
    private const val RELOAD_CLIENT_ID_URL_PARAMETER = "reloadServiceClientId"
    private const val UPDATE_LINKS_ID_URL_PARAMETER = "jbUpdateLinksId"
    val instance: WebServerPageConnectionService
      get() = ApplicationManager.getApplication().getService(WebServerPageConnectionService::class.java)
    private val LOGGER = Logger.getInstance(WebServerPageConnectionService::class.java)
  }
}
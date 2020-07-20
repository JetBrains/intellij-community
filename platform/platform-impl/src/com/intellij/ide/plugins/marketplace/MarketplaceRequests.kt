// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import org.jetbrains.annotations.ApiStatus
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.net.HttpURLConnection
import java.net.URLConnection
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import com.jetbrains.plugin.blockmap.BlockMap
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@ApiStatus.Internal
open class MarketplaceRequests {

  private val LOG = Logger.getInstance(MarketplaceRequests::class.java)

  companion object {
    private const val TAG_EXT = ".etag"

    private const val FULL_PLUGINS_XML_IDS_FILENAME = "pluginsXMLIds.json"

    private const val FILENAME = "filename="

    private val INSTANCE = MarketplaceRequests()

    private const val MAX_HTTP_HEADER_LENGTH = 1000

    @JvmStatic
    fun getInstance(): MarketplaceRequests {
      return INSTANCE
    }

    @JvmStatic
    fun parsePluginList(reader: Reader): List<PluginNode> {
      try {
        val parser = SAXParserFactory.newInstance().newSAXParser()
        val handler = RepositoryContentHandler()
        parser.parse(InputSource(reader), handler)
        return handler.pluginsList
      }
      catch (e: Exception) {
        when (e) {
          is ParserConfigurationException, is SAXException, is RuntimeException -> throw IOException(e)
          else -> throw e
        }
      }
    }
  }

  private val PLUGIN_MANAGER_URL = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl.trimEnd('/')

  private val AVAILABLE_PLUGINS_XML_IDS_URL = "${PLUGIN_MANAGER_URL}/files/$FULL_PLUGINS_XML_IDS_FILENAME"

  private val IDE_BUILD_FOR_REQUEST = URLUtil.encodeURIComponent(getBuildForPluginRepositoryRequests())

  private val MARKETPLACE_ORGANIZATIONS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/aggregation/organizations"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val MARKETPLACE_TAGS_URL = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/api/search/aggregation/tags"
  ).addParameters(mapOf("build" to IDE_BUILD_FOR_REQUEST))

  private val COMPATIBLE_UPDATE_URL = "${PLUGIN_MANAGER_URL}/api/search/compatibleUpdates"

  private val objectMapper by lazy { ObjectMapper() }

  private fun getUpdatesMetadataFilesDirectory() = File(PathManager.getPluginsPath()).resolve("meta")

  internal fun getBrokenPluginsFile() = File(PathManager.getPluginsPath()).resolve("brokenPlugins.json")

  private fun getUpdateMetadataFile(update: IdeCompatibleUpdate) = getUpdatesMetadataFilesDirectory().resolve(
    update.externalUpdateId + ".json")

  private fun getUpdateMetadataUrl(update: IdeCompatibleUpdate) =
    "${PLUGIN_MANAGER_URL}/files/${update.externalPluginId}/${update.externalUpdateId}/meta.json"

  private fun createSearchUrl(query: String, count: Int): Url = Urls.newFromEncoded(
    "$PLUGIN_MANAGER_URL/api/search/plugins?$query&build=$IDE_BUILD_FOR_REQUEST&max=$count"
  )

  private fun createFeatureUrl(param: Map<String, String>) = Urls.newFromEncoded(
    "${PLUGIN_MANAGER_URL}/feature/getImplementations"
  ).addParameters(param)

  private val BROKEN_PLUGIN_PATH = "${PLUGIN_MANAGER_URL}/files/brokenPlugins.json"

  fun getFeatures(param: Map<String, String>): List<FeatureImpl> = try {
    if (param.isEmpty()) emptyList()
    else {
      HttpRequests
        .request(createFeatureUrl(param))
        .throwStatusCodeException(false)
        .productNameAsUserAgent()
        .connect {
          objectMapper.readValue(
            it.inputStream,
            object : TypeReference<List<FeatureImpl>>() {}
          )
        }
    }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get features from Marketplace", e)
    emptyList()
  }

  @Throws(IOException::class)
  fun getMarketplacePlugins(indicator: ProgressIndicator?): List<String> {
    val pluginXmlIdsFile = File(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    return readOrUpdateFile(
      pluginXmlIdsFile,
      AVAILABLE_PLUGINS_XML_IDS_URL,
      indicator,
      IdeBundle.message("progress.downloading.available.plugins"),
      ::parseXmlIds
    )
  }

  @Throws(IOException::class)
  fun getMarketplaceCachedPlugins(): List<String>? {
    val pluginXmlIdsFile = File(PathManager.getPluginsPath(), FULL_PLUGINS_XML_IDS_FILENAME)
    return if (pluginXmlIdsFile.length() > 0) pluginXmlIdsFile.bufferedReader().use(::parseXmlIds) else null
  }

  fun getBuildForPluginRepositoryRequests(): String {
    val instance = ApplicationInfoImpl.getShadowInstance()
    val compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild()
    return if (compatibleBuild != null) {
      BuildNumber.fromStringWithProductCode(
        compatibleBuild,
        instance.build.productCode
      )!!.asString()
    }
    else instance.apiVersion
  }

  @Throws(IOException::class)
  fun searchPlugins(query: String, count: Int): List<PluginNode> {
    val marketplaceSearchPluginData = HttpRequests.request(createSearchUrl(query, count))
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(
          it.inputStream,
          object : TypeReference<List<MarketplaceSearchPluginData>>() {}
        )
      }
    // Marketplace Search Service can produce objects without "externalUpdateId". It means that an update is not in the search index yet.
    return marketplaceSearchPluginData.filter { it.externalUpdateId != null }.map { it.toPluginNode() }
  }

  fun getAllPluginsVendors(): List<String> = try {
    HttpRequests
      .request(MARKETPLACE_ORGANIZATIONS_URL)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get organizations from Marketplace", e)
    emptyList()
  }

  fun getBrokenPlugins(): List<MarketplaceBrokenPlugin> {
    return try {
      readOrUpdateFile(
        getBrokenPluginsFile(),
        BROKEN_PLUGIN_PATH,
        null,
        "",
        ::parseBrokenPlugins
      )
    }
    catch (e: Exception) {
      logWarnOrPrintIfDebug("Can not get broken plugins file from Marketplace", e)
      emptyList()
    }
  }


  fun getAllPluginsTags(): List<String> = try {
    HttpRequests
      .request(MARKETPLACE_TAGS_URL)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        objectMapper.readValue(it.inputStream, AggregationSearchResponse::class.java).aggregations.keys.toList()
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get tags from Marketplace", e)
    emptyList()
  }

  @Throws(IOException::class)
  fun loadPluginDescriptor(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator? = null): PluginNode {
    return readOrUpdateFile(
      getUpdateMetadataFile(ideCompatibleUpdate),
      getUpdateMetadataUrl(ideCompatibleUpdate),
      indicator,
      IdeBundle.message("progress.downloading.plugins.meta", xmlId),
      ::parseJsonPluginMeta
    ).toPluginNode()
  }

  @JvmOverloads
  fun loadLastCompatiblePluginDescriptors(ids: List<String>, buildNumber: BuildNumber? = null): List<PluginNode> {
    if (ids.isEmpty()) return emptyList()
    val data: List<IdeCompatibleUpdate> = getLastCompatiblePluginUpdate(ids, buildNumber)
    return data.map { loadPluginDescriptor(it.pluginId, it, null) }
  }


  fun loadPluginDetails(pluginNode: PluginNode): PluginNode {
    val externalPluginId = pluginNode.externalPluginId
    val externalUpdateId = pluginNode.externalUpdateId
    if (externalPluginId == null || externalUpdateId == null) return pluginNode
    val ideCompatibleUpdate = IdeCompatibleUpdate(externalUpdateId = externalUpdateId, externalPluginId = externalPluginId)
    return loadPluginDescriptor(pluginNode.pluginId.idString, ideCompatibleUpdate)
  }

  @Throws(IOException::class)
  fun <T> readOrUpdateFile(
    file: File?,
    url: String,
    indicator: ProgressIndicator?,
    indicatorMessage: String,
    parser: (Reader) -> T
  ): T {
    val eTag = if (file != null) loadEtagForFile(file) else null
    return HttpRequests
      .request(url)
      .tuner { connection -> connection.setUpETag(eTag) }
      .productNameAsUserAgent()
      .connect { request ->
        indicator?.checkCanceled()
        val connection = request.connection
        if (file != null && connection.isNotModified(file)) {
          return@connect file.bufferedReader().use(parser)
        }
        if (indicator != null) {
          indicator.checkCanceled()
          indicator.text2 = indicatorMessage
        }
        if (file != null) {
          synchronized(INSTANCE) {
            request.saveToFile(file, indicator)
            connection.getHeaderField("ETag")?.let { saveETagForFile(file, it) }
          }
          return@connect file.bufferedReader().use(parser)
        }
        else {
          return@connect request.reader.use(parser)
        }
      }
  }

  fun getLastCompatiblePluginUpdate(ids: List<String>, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> = try {
    if (ids.isEmpty()) emptyList()
    else {
      val data = objectMapper.writeValueAsString(CompatibleUpdateRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), ids))
      val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
      HttpRequests
        .post(url, HttpRequests.JSON_CONTENT_TYPE)
        .productNameAsUserAgent()
        .throwStatusCodeException(false)
        .connect {
          it.write(data)
          objectMapper
            .readValue(
              it.inputStream,
              object : TypeReference<List<IdeCompatibleUpdate>>() {}
            )
        }
    }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get compatible updates from Marketplace", e)
    emptyList()
  }

  @JvmOverloads
  fun getLastCompatiblePluginUpdate(id: String, buildNumber: BuildNumber? = null, indicator: ProgressIndicator? = null): PluginNode? {
    val data = getLastCompatiblePluginUpdate(listOf(id), buildNumber).firstOrNull()
    return data?.let { loadPluginDescriptor(id, it, indicator) }
  }

  @JvmOverloads
  fun getCompatibleUpdatesByModule(module: String, buildNumber: BuildNumber? = null): List<IdeCompatibleUpdate> = try {
    val data = objectMapper.writeValueAsString(
      CompatibleUpdateForModuleRequest(PluginDownloader.getBuildNumberForDownload(buildNumber), module)
    )
    val url = Urls.newFromEncoded(COMPATIBLE_UPDATE_URL).toExternalForm()
    HttpRequests
      .post(url, HttpRequests.JSON_CONTENT_TYPE)
      .productNameAsUserAgent()
      .throwStatusCodeException(false)
      .connect {
        it.write(data)
        objectMapper
          .readValue(
            it.inputStream,
            object : TypeReference<List<IdeCompatibleUpdate>>() {}
          )
      }
  }
  catch (e: Exception) {
    logWarnOrPrintIfDebug("Can not get compatible update by module from Marketplace", e)
    emptyList()
  }

  private fun parseBrokenPlugins(reader: Reader) = objectMapper.readValue(
    reader,
    object : TypeReference<List<MarketplaceBrokenPlugin>>() {}
  )

  private fun parseXmlIds(reader: Reader) = objectMapper.readValue(reader, object : TypeReference<List<String>>() {})

  private fun parseJsonPluginMeta(reader: Reader) = objectMapper.readValue(reader, IntellijUpdateMetadata::class.java)

  private fun getETagFile(file: File): File = file.resolveSibling(file.name + TAG_EXT)

  private fun loadEtagForFile(file: File): String? {
    val eTagFile = getETagFile(file)
    if (eTagFile.exists()) {
      try {
        val lines = eTagFile.readLines()
        if (lines.size != 1) {
          LOG.warn("Can't load ETag from '" + eTagFile.absolutePath + "'. Unexpected number of lines: " + lines.size)
          FileUtil.delete(eTagFile)
        }
        else {
          return lines[0]
        }
      }
      catch (e: IOException) {
        LOG.warn("Can't load ETag from '" + eTagFile.absolutePath + "'", e)
      }
    }
    return ""
  }

  private fun saveETagForFile(file: File, eTag: String) {
    val eTagFile = getETagFile(file)
    try {
      eTagFile.writeText(eTag)
    }
    catch (e: IOException) {
      LOG.warn("Can't save ETag to '" + eTagFile.absolutePath + "'", e)
    }
  }

  @Throws(IOException::class)
  open fun download(pluginUrl: String, indicator: ProgressIndicator): File {
    val pluginsTemp = File(PathManager.getPluginTempPath())
    if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
      throw IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp))
    }
    val file = FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false)
    return HttpRequests.request(pluginUrl).gzip(false).productNameAsUserAgent().connect(
      HttpRequests.RequestProcessor { request: HttpRequests.Request ->
        request.saveToFile(file, indicator)
        val fileName: String = guessFileName(request.connection, file, pluginUrl)
        val newFile = File(file.parentFile, fileName)
        FileUtil.rename(file, newFile)
        newFile
      })
  }

  @Throws(IOException::class)
  open fun download(pluginUrl: String, prevPlugin: Path, indicator: ProgressIndicator): File {
    val start = System.currentTimeMillis()

    val prevPluginArchive = getPrevPluginArchive(prevPlugin)
    if(!prevPluginArchive.exists()) throw FileNotFoundException()

    val pluginsTemp = File(PathManager.getPluginTempPath())
    if (!pluginsTemp.exists() && !pluginsTemp.mkdirs()) {
      throw IOException(IdeBundle.message("error.cannot.create.temp.dir", pluginsTemp))
    }
    val file = FileUtil.createTempFile(pluginsTemp, "plugin_", "_download", true, false)
    println(file.toString())

    val oldBlockMap = FileInputStream(prevPluginArchive.toFile()).use { input -> BlockMap(input) }
    println("prevBlockMap chunks=${oldBlockMap.chunks.size}")

    /*println(pluginUrl)
    var blockMapUrl = pluginUrl.replace("/pluginManager", "/pluginManager/blockmap")
    println(blockMapUrl)

    val newBlockMap = HttpRequests.request(blockMapUrl).connect { request ->
      ObjectInputStream(request.inputStream).readObject() as BlockMap
    }*/
    val newBlockMap = ObjectInputStream(FileInputStream("C:\\Users\\Ivan\\JetBrains\\Internship\\intellij\\system\\idea\\plugins\\blockmap.bin")).use { input -> input.readObject() as BlockMap }
    println("newBlockMap chunks=${newBlockMap.chunks.size}")

    val blockMapDiff = oldBlockMap.compare(newBlockMap)
    println("blockMapDiff chunks=${blockMapDiff.size}")

    val curPluginUrl = if(pluginUrl.contains("localhost:8080")) pluginUrl.replace("localhost:8080","plugins.jetbrains.com") else pluginUrl


    val output = FileOutputStream(file).buffered()
    val prevPluginRAF = RandomAccessFile(prevPluginArchive.toFile(), "r")
    val buffer = ByteArray(64 * 1024)

    val oldMap = oldBlockMap.chunks.associateBy { it.hash }
    val rangeBytes = StringBuilder()
    var rangeCnt = 0
    var curChunk = 0
    var summaryDownloadedBytes = 0
    for (newChunk in newBlockMap.chunks) {
      if (!oldMap.containsKey(newChunk.hash)) {
        rangeBytes.append("${newChunk.offset}-${newChunk.offset + newChunk.length - 1},")
        rangeCnt++
        summaryDownloadedBytes+=newChunk.length
      }
      if(rangeBytes.length >= MAX_HTTP_HEADER_LENGTH){
        HttpRequests.requestWithRange(curPluginUrl, rangeBytes.removeSuffix(",").toString()).productNameAsUserAgent().connect { request ->
          val boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
          request.inputStream.buffered().use { input ->
            while(curChunk < newBlockMap.chunks.size) {
              val chunk = newBlockMap.chunks[curChunk]
              val oldChunk = oldMap[chunk.hash]
              if (oldChunk != null) {
                prevPluginRAF.seek(oldChunk.offset.toLong())
                prevPluginRAF.read(buffer, 0, oldChunk.length)
                for (i in 0 until oldChunk.length) {
                  output.write(buffer[i].toInt())
                }
              }
              else {
                try {
                  do {
                    val line = nextLine(input)
                  } while(!line.contains(boundary))
                  nextLine(input)
                  nextLine(input)
                  nextLine(input)
                  for(i in 0 until chunk.length) output.write(input.read())
                  rangeCnt--
                }catch (e : Exception){
                  println(e)
                }
              }
              curChunk++
              if (rangeCnt == 0) break
            }
          }
        }
        rangeCnt = 0
        rangeBytes.clear()
      }
    }
    if(rangeCnt != 0){
      HttpRequests.requestWithRange(curPluginUrl, rangeBytes.removeSuffix(",").toString()).productNameAsUserAgent().connect { request ->
        val boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
        request.inputStream.buffered().use { input ->
          while(curChunk < newBlockMap.chunks.size) {
            val chunk = newBlockMap.chunks[curChunk]
            val oldChunk = oldMap[chunk.hash]
            if (oldChunk != null) {
              prevPluginRAF.seek(oldChunk.offset.toLong())
              prevPluginRAF.read(buffer, 0, oldChunk.length)
              for (i in 0 until oldChunk.length) {
                output.write(buffer[i].toInt())
              }
            }
            else {
              try {
                do {
                  val line = nextLine(input)
                } while(!line.contains(boundary))
                nextLine(input)
                nextLine(input)
                nextLine(input)
                for(i in 0 until chunk.length) output.write(input.read())
                rangeCnt--
              }catch (e : Exception){
                println(e)
              }
            }
            curChunk++
            if (rangeCnt == 0) break
          }
        }
      }
    }
    output.close()
    prevPluginRAF.close()




     /* FileOutputStream(file).buffered().use { output ->
        val randomAccessFile = RandomAccessFile(prevPluginArchive.toFile(), "r")
        val buffer = ByteArray(64 * 1024)
        for (newChunk in newBlockMap.chunks) {
          val oldChunk = oldMap[newChunk.hash]
          if (oldChunk != null) {
            randomAccessFile.seek(oldChunk.offset.toLong())
            randomAccessFile.read(buffer, 0, oldChunk.length)
            for (i in 0 until oldChunk.length) {
              output.write(buffer[i].toInt())
            }
          }
          else {
            try{
              while(!input.readLine().contains(boundary)){}
              input.readLine()
              input.readLine()
              input.readLine()
              for (i in 0 until newChunk.length) output.write(input.read())
              curRangeCnt--
              println(curRangeCnt)
              if(curRangeCnt == 0){
                curRange++
                if(curRange < rangeBytesStrings.size){
                  curRangeCnt = rangeBytesStrings[curRange].first
                  curRangeString = rangeBytesStrings[curRange].second
                  input.close()
                  input = HttpRequests.requestWithRange(curPluginUrl, curRangeString).productNameAsUserAgent().connect { request ->
                    boundary = request.connection.contentType.removePrefix("multipart/byteranges; boundary=")
                    InputStreamReader(request.inputStream).buffered()
                  }
                }else if(curRange == rangeBytesStrings.size) input.close()
              }
            }catch (e : Exception){
              println(e)
            }

          }
        }
      }*/


    println("summary downloaded bytes=$summaryDownloadedBytes")
    /*println("time=${System.currentTimeMillis() - start}")
    val newFileBytes = FileInputStream("D:\\plugins\\IntelliJ IDEA Help\\IntelliJIDEAHelp.zip").use { input -> input.readBytes() }

    val mergeFileBytes = FileInputStream(file).use { input -> input.readBytes() }
    println("new bytes=${newFileBytes.size}, merge bytes=${mergeFileBytes.size}")
    var s = 0
    for(i in newFileBytes.indices) if(newFileBytes[i] != mergeFileBytes[i]) s++
    println(s)
    println(newFileBytes.contentEquals(mergeFileBytes))*/
    throw Exception("")
    return File("")
  }

  private fun nextLine(input : BufferedInputStream) : String{
    ByteArrayOutputStream().use { baos ->
      do{
        val byte = input.read()
        baos.write(byte)
      }while(byte.toChar() != '\n')
      return String(baos.toByteArray(), Charset.defaultCharset())
    }
  }


  private fun getPrevPluginArchive(prevPlugin: Path): Path {
    val suffix = if(prevPlugin.endsWith(".jar")) "" else ".zip"
    println("${PathManager.getPluginTempPath()}\\${prevPlugin.fileName}$suffix")
    return Paths.get("${PathManager.getPluginTempPath()}\\${prevPlugin.fileName}$suffix")
  }

  @Throws(IOException::class)
  private fun guessFileName(connection: URLConnection, file: File, pluginUrl: String): String {
    var fileName: String? = null
    val contentDisposition = connection.getHeaderField("Content-Disposition")
    LOG.debug("header: $contentDisposition")
    if (contentDisposition != null && contentDisposition.contains(FILENAME)) {
      val startIdx = contentDisposition.indexOf(FILENAME)
      val endIdx = contentDisposition.indexOf(';', startIdx)
      fileName = contentDisposition.substring(startIdx + FILENAME.length, if (endIdx > 0) endIdx else contentDisposition.length)
      if (StringUtil.startsWithChar(fileName, '\"') && StringUtil.endsWithChar(fileName, '\"')) {
        fileName = fileName.substring(1, fileName.length - 1)
      }
    }
    if (fileName == null) {
      // try to find a filename in an URL
      val usedURL = connection.url.toString()
      LOG.debug("url: $usedURL")
      fileName = usedURL.substring(usedURL.lastIndexOf('/') + 1)
      if (fileName.isEmpty() || fileName.contains("?")) {
        fileName = pluginUrl.substring(pluginUrl.lastIndexOf('/') + 1)
      }
    }
    if (!PathUtil.isValidFileName(fileName)) {
      LOG.debug("fileName: $fileName")
      FileUtil.delete(file)
      throw IOException("Invalid filename returned by a server")
    }
    return fileName
  }

  private fun URLConnection.setUpETag(eTag: String?) {
    eTag?.also { this.setRequestProperty("If-None-Match", it) }
  }

  private fun URLConnection.isNotModified(file: File?): Boolean =
    file != null && file.length() > 0 && this is HttpURLConnection && this.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED

  private data class CompatibleUpdateRequest(val build: String, val pluginXMLIds: List<String>)
  private data class CompatibleUpdateForModuleRequest(val build: String, val module: String)

  private fun logWarnOrPrintIfDebug(message: String, throwable: Throwable) {
    if (LOG.isDebugEnabled) {
      LOG.debug(message, throwable)
    }
    else {
      LOG.warn("$message:${throwable.message}")
    }
  }

}
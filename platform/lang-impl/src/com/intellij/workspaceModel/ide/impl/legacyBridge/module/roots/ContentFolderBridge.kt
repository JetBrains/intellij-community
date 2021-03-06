package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ContentFolder
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.module.UnknownSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension

internal abstract class ContentFolderBridge(private val entry: ContentEntryBridge, private val contentFolderUrl: VirtualFileUrl) : ContentFolder {
  override fun getContentEntry(): ContentEntryBridge = entry
  override fun getUrl(): String = contentFolderUrl.url
  override fun isSynthetic(): Boolean = false
  override fun equals(other: Any?): Boolean = contentFolderUrl == (other as? ContentFolderBridge)?.contentFolderUrl
  override fun hashCode(): Int = contentFolderUrl.hashCode()
}

internal class SourceFolderBridge(private val entry: ContentEntryBridge, val sourceRootEntity: SourceRootEntity)
  : ContentFolderBridge(entry, sourceRootEntity.url), SourceFolder {

  override fun getFile(): VirtualFile? {
    val virtualFilePointer = sourceRootEntity.url as VirtualFilePointer
    return virtualFilePointer.file
  }

  private var packagePrefixVar: String? = null
  private val sourceRootType: JpsModuleSourceRootType<out JpsElement> = getSourceRootType(sourceRootEntity)

  override fun getRootType() = sourceRootType
  override fun isTestSource() = sourceRootEntity.tests
  override fun getPackagePrefix() = packagePrefixVar ?:
                                    sourceRootEntity.asJavaSourceRoot()?.packagePrefix ?:
                                    sourceRootEntity.asJavaResourceRoot()?.relativeOutputPath?.replace('/', '.') ?: ""

  override fun getJpsElement(): JpsModuleSourceRoot {
    return entry.model.getOrCreateJpsRootProperties(sourceRootEntity.url) {
      SourceRootPropertiesHelper.loadRootProperties(sourceRootEntity, rootType, url)
    }
  }

  override fun <P : JpsElement> changeType(newType: JpsModuleSourceRootType<P>, properties: P) {
    (ModuleRootManager.getInstance(contentEntry.rootModel.module) as ModuleRootComponentBridge).dropRootModelCache()
  }

  override fun hashCode() = entry.url.hashCode()
  override fun equals(other: Any?): Boolean {
    if (other !is SourceFolderBridge) return false

    if (sourceRootEntity.url != other.sourceRootEntity.url) return false
    if (sourceRootEntity.rootType != other.sourceRootEntity.rootType) return false
    if (sourceRootEntity.tests != other.sourceRootEntity.tests) return false

    val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
    val otherJavaSourceRoot = other.sourceRootEntity.asJavaSourceRoot()
    if (javaSourceRoot?.generated != otherJavaSourceRoot?.generated) return false
    if (javaSourceRoot?.packagePrefix != otherJavaSourceRoot?.packagePrefix) return false

    val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
    val otherJavaResourceRoot = other.sourceRootEntity.asJavaResourceRoot()
    if (javaResourceRoot?.generated != otherJavaResourceRoot?.generated) return false
    if (javaResourceRoot?.relativeOutputPath != otherJavaResourceRoot?.relativeOutputPath) return false

    val customRoot = sourceRootEntity.asCustomSourceRoot()
    val otherCustomRoot = other.sourceRootEntity.asCustomSourceRoot()
    if (customRoot?.propertiesXmlTag != otherCustomRoot?.propertiesXmlTag) return false

    return true
  }

  override fun setPackagePrefix(packagePrefix: String) {
    if (getPackagePrefix() == packagePrefix) return

    val updater = entry.updater ?: error("Model is read-only")

    val javaSourceRoot = sourceRootEntity.asJavaSourceRoot()
    if (javaSourceRoot == null) {
      val javaResourceRoot = sourceRootEntity.asJavaResourceRoot()
      // Original setPackagePrefix silently does nothing on any non-java-source-roots
      if (javaResourceRoot != null) return

      updater { diff ->
        diff.addJavaSourceRootEntity(sourceRootEntity, false, packagePrefix)
      }
    }
    else {
      updater { diff ->
        diff.modifyEntity(ModifiableJavaSourceRootEntity::class.java, javaSourceRoot) {
          this.packagePrefix = packagePrefix
        }
      }
    }
    //we need to also update package prefix in Jps root properties, otherwise this change will be lost if some code changes some other
    // property (e.g. 'forGeneratedProperties') in Jps root later
    jpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES)?.packagePrefix = packagePrefix

    packagePrefixVar = packagePrefix
  }

  private fun getSourceRootType(entity: SourceRootEntity): JpsModuleSourceRootType<out JpsElement> {
    JpsModelSerializerExtension.getExtensions().forEach { extensions ->
      extensions.moduleSourceRootPropertiesSerializers.forEach {
        if (it.typeId == entity.rootType) return it.type
      }
    }
    return UnknownSourceRootType.getInstance(entity.rootType)
  }

  companion object {
    val LOG by lazy { logger<ContentFolderBridge>() }
  }
}

internal class ExcludeFolderBridge(val entry: ContentEntryBridge, val excludeFolderUrl: VirtualFileUrl)
  : ContentFolderBridge(entry, excludeFolderUrl), ExcludeFolder {
  override fun getFile(): VirtualFile? {
    val virtualFilePointer = excludeFolderUrl as VirtualFilePointer
    return virtualFilePointer.file
  }
}

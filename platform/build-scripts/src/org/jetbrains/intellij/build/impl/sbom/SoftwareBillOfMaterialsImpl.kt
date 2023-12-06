// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.sbom

import com.intellij.openapi.util.SystemInfoRt
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.outputStream
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.apache.commons.codec.digest.DigestUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Options
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.util.JpsPathUtil
import org.jsoup.Jsoup
import org.spdx.jacksonstore.MultiFormatStore
import org.spdx.library.ModelCopyManager
import org.spdx.library.SpdxConstants
import org.spdx.library.Version
import org.spdx.library.model.*
import org.spdx.library.model.SpdxPackage.SpdxPackageBuilder
import org.spdx.library.model.enumerations.ChecksumAlgorithm
import org.spdx.library.model.enumerations.ReferenceCategory
import org.spdx.library.model.enumerations.RelationshipType
import org.spdx.library.model.license.AnyLicenseInfo
import org.spdx.library.model.license.ExtractedLicenseInfo
import org.spdx.library.model.license.InvalidLicenseStringException
import org.spdx.library.model.license.LicenseInfoFactory
import org.spdx.library.model.license.SpdxNoAssertionLicense
import org.spdx.storage.IModelStore.IdType
import org.spdx.storage.ISerializableModelStore
import org.spdx.storage.simple.InMemSpdxStore
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.bufferedReader
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

internal class SoftwareBillOfMaterialsImpl(
  private val context: BuildContext,
  private val distributions: List<DistributionForOsTaskResult>,
  private val distributionFiles: List<DistributionFileEntry>
): SoftwareBillOfMaterials {
  private val specVersion: String = Version.TWO_POINT_THREE_VERSION

  private val creator: String
    get() = context.productProperties.sbomOptions.creator ?: error("Creator isn't specified")

  private val version: String
    get() = "${context.applicationInfo.productCode}-${context.buildNumber}"

  private val baseDownloadUrl: String?
    get() = context.productProperties.baseDownloadUrl?.removeSuffix("/")

  private val documentNamespace: String?
    get() = context.productProperties.sbomOptions.documentNamespace ?: baseDownloadUrl

  private val license: Options.DistributionLicense by lazy {
    when (val license = context.productProperties.sbomOptions.license) {
      null -> throw IllegalArgumentException("Distribution license isn't specified")
      Options.DistributionLicense.JETBRAINS -> jetBrainsOwnLicense
      else -> license
    }
  }

  /**
   * See [com.intellij.ide.gdpr.EndUserAgreement]
   */
  private val jetBrainsOwnLicense: Options.DistributionLicense by lazy {
    val eula = context.paths.communityHomeDir
      .resolve("platform/platform-resources/src")
      .resolve(when {
                 context.applicationInfo.isEAP -> "euaEap.html"
                 else -> "eua.html"
               })
    check(eula.exists()) {
      "$eula is missing"
    }
    Options.DistributionLicense(
      name = "JetBrains User Agreement",
      text = Jsoup.parse(eula.readText()).text(),
      url = "https://www.jetbrains.com/legal/docs/toolbox/user/"
    )
  }

  private val repositories by lazy {
    JpsRemoteRepositoryService.getInstance()
      .getOrCreateRemoteRepositoriesConfiguration(context.project)
      .repositories
  }

  private val DistributionForOsTaskResult.files: List<Path>
    get() = when (builder.targetOs) {
      OsFamily.LINUX -> sequenceOf(".tar.gz")
      OsFamily.MACOS -> sequenceOf(".dmg", ".sit", ".mac.${arch.name}.zip")
      OsFamily.WINDOWS -> sequenceOf(".exe", ".win.zip")
    }.map { extension ->
      context.productProperties.getBaseArtifactName(context) +
      OsSpecificDistributionBuilder.suffix(arch) +
      extension
    }.plus(
      when {
        builder is LinuxDistributionBuilder -> builder.snapArtifactName
        else -> null
      }
    ).filterNotNull()
      .map(context.paths.artifactDir::resolve)
      .filter { it.exists() }.toList()

  private fun spdxDocument(name: String): SpdxDocument {
    val uri = "$documentNamespace/$specVersion/$name.spdx"
    val modelStore = MultiFormatStore(InMemSpdxStore(), MultiFormatStore.Format.JSON_PRETTY)
    val document = SpdxModelFactory.createSpdxDocument(modelStore, uri, ModelCopyManager())
    val creationDate = Date(TimeUnit.SECONDS.toMillis(context.options.buildDateInSeconds))
    document.creationInfo = document.createCreationInfo(
      listOf(creator),
      SimpleDateFormat(SpdxConstants.SPDX_DATE_FORMAT).format(creationDate)
    )
    document.specVersion = specVersion
    document.dataLicense = document.parseLicense(SpdxConstants.SPDX_DATA_LICENSE_ID)
    document.setName(name)
    return document
  }

  private val SpdxDocument.outputFile: Path
    get() = context.paths.artifactDir.resolve("${name.get()}.spdx.json")

  private fun SpdxDocument.write() {
    outputFile.outputStream().use {
      (modelStore as ISerializableModelStore).serialize(documentUri, it)
    }
  }

  private fun ModelObject.validate() {
    val errors = verify()
    check(errors.none()) {
      errors.joinToString(separator = "\n")
    }
  }

  override suspend fun generate() {
    val skipReason = when {
      !context.shouldBuildDistributions() -> "No distribution was built"
      documentNamespace == null -> "Document namespace isn't specified"
      context.productProperties.sbomOptions.creator == null -> "Document creator isn't specified"
      context.productProperties.sbomOptions.copyrightText == null -> "Copyright text isn't specified"
      context.productProperties.sbomOptions.license == null -> "Distribution license isn't specified"
      else -> null
    }
    if (skipReason != null) {
      Span.current().addEvent("$skipReason, skipping")
      return
    }
    check(distributionFiles.any()) {
      "No distribution was built"
    }
    val documents = if (distributions.any { it.files.any() }) {
      generateFromDistributions()
    }
    else {
      generateFromContentReport()
    }
    check(documents.any()) {
      "No SBOM documents were generated"
    }
    documents.forEach {
      Span.current().addEvent("SBOM document generated", Attributes.of(AttributeKey.stringKey("file"), "$it"))
    }
    checkNtiaConformance(documents)
  }

  private class Checksums(val path: Path) {
    val sha1sum: String = Files.newInputStream(path).use(DigestUtils::sha1Hex)
    val sha256sum: String = Files.newInputStream(path).use(DigestUtils::sha256Hex)
  }

  private suspend fun generateFromDistributions(): List<Path> {
    return withContext(Dispatchers.IO) {
      distributions.associateWith { distribution ->
        distribution.files
          .map { async { Checksums(it) } }
          .map { it.await() }
      }
    }.flatMap { (distribution, filesWithChecksums) ->
      filesWithChecksums.map {
        val document = spdxDocument(it.path.name)
        val rootPackage = document.spdxPackage(it.path.name, sha256sum = it.sha256sum, sha1sum = it.sha1sum) {
          setVersionInfo(version)
            .setSupplier(creator)
            .setDownloadLocation(baseDownloadUrl?.let { url ->
              "$url/${it.path.name}"
            } ?: SpdxConstants.NOASSERTION_VALUE)
        }
        document.documentDescribes.add(rootPackage)
        val runtimePackage = if (isRuntimeBundled(it.path, distribution.builder.targetOs)) {
          document.runtimePackage(distribution.builder.targetOs, distribution.arch)
        } else null
        generate(
          document, rootPackage,
          runtimePackage = runtimePackage,
          distributionDir = distribution.outDir
        )
      }
    }
  }

  private fun isRuntimeBundled(file: Path, os: OsFamily): Boolean {
    return when (os) {
      OsFamily.LINUX -> !file.name.contains(LinuxDistributionBuilder.NO_RUNTIME_SUFFIX)
      OsFamily.MACOS -> !file.name.contains(MacDistributionBuilder.NO_RUNTIME_SUFFIX)
      OsFamily.WINDOWS -> true
    }
  }

  /**
   * Used until external document reference for Runtime is supplied,
   * then should be replaced with [addRuntimeDocumentRef]
   */
  private suspend fun SpdxDocument.runtimePackage(os: OsFamily, arch: JvmArchitecture): SpdxPackage {
    val checksums = Checksums(context.bundledRuntime.findArchive(os = os, arch = arch))
    val version = context.bundledRuntime.build
    val supplier = "Organization: ${Suppliers.JETBRAINS}"
    val runtimeArchivePackage = spdxPackage(
      name = context.bundledRuntime.archiveName(os = os, arch = arch),
      sha256sum = checksums.sha256sum, sha1sum = checksums.sha1sum,
      licenseDeclared = jetBrainsOwnLicense
    ) {
      setVersionInfo(version)
      setSupplier(supplier)
      setDownloadLocation(context.bundledRuntime.downloadUrlFor(os = os, arch = arch))
    }
    runtimeArchivePackage.claimContainedFiles(document = this)
    /**
     * See [BundledRuntime.extract]
     */
    val extractedRuntimePackage = spdxPackage(name = "./jbr/**") {
      setVersionInfo(version)
      setSupplier(supplier)
      setDownloadLocation(SpdxConstants.NOASSERTION_VALUE)
    }
    extractedRuntimePackage.relatesTo(runtimeArchivePackage, RelationshipType.EXPANDED_FROM_ARCHIVE)
    addRuntimeUpstreams(runtimeArchivePackage, os, arch)
    runtimeArchivePackage.validate()
    return extractedRuntimePackage
  }

  private fun SpdxDocument.addRuntimeUpstreams(runtimeArchivePackage: SpdxPackage, os: OsFamily, arch: JvmArchitecture) {
    val cefVersion = context.dependenciesProperties["cef.version"]
    val cefSuffix = when (os) {
      OsFamily.LINUX -> when (arch) {
        JvmArchitecture.aarch64 -> "linuxarm64"
        JvmArchitecture.x64 -> "linux64"
      }
      OsFamily.MACOS -> when (arch) {
        JvmArchitecture.aarch64 -> "macosarm64"
        JvmArchitecture.x64 -> "macosx64"
      }
      OsFamily.WINDOWS -> when (arch) {
        JvmArchitecture.aarch64 -> "windowsarm64"
        JvmArchitecture.x64 -> "windows64"
      }
    }
    val cefArchive = "cef_binary_${cefVersion}_$cefSuffix.tar.bz2"
    val cefPackage = spdxPackage(name = cefArchive) {
      setVersionInfo(cefVersion)
      setSupplier("Organization: The Chromium Embedded Framework Authors")
      setDownloadLocation("https://cef-builds.spotifycdn.com/$cefArchive")
    }
    val jcefUpstream = spdxPackage("Java Chromium Embedded Framework") {
      val revision = context.dependenciesProperties["jcef.commit"]
      setVersionInfo(revision)
      setSourceInfo("Revision $revision of https://github.com/chromiumembedded/java-cef")
      setSupplier("Organization: The Chromium Embedded Framework Authors")
      setDownloadLocation(SpdxConstants.NOASSERTION_VALUE)
    }
    val openJdkUpstream = spdxPackage("OpenJDK") {
      val tag = context.dependenciesProperties["openjdk.tag"]
      setVersionInfo(tag)
      setSourceInfo("Tag $tag of https://github.com/openjdk/jdk17u")
      setSupplier("Organization: Oracle Corporation and/or its affiliates")
      setDownloadLocation(SpdxConstants.NOASSERTION_VALUE)
    }
    runtimeArchivePackage.relatesTo(cefPackage, RelationshipType.DEPENDS_ON, "repacked from")
    runtimeArchivePackage.relatesTo(openJdkUpstream, RelationshipType.VARIANT_OF)
    runtimeArchivePackage.relatesTo(jcefUpstream, RelationshipType.VARIANT_OF)
  }

  /**
   * @param runtimeDocumentId expected format: https://github.com/JetBrains/JetBrainsRuntime/[specVersion]/${[BundledRuntime.prefix] + [BundledRuntime.build]}.spdx
   * @param runtimeRootPackageId expected format: ${[SpdxConstants.SPDX_ELEMENT_REF_PRENUM] + [InMemSpdxStore.GENERATED] + 0}
   */
  @Suppress("unused")
  private fun SpdxDocument.addRuntimeDocumentRef(
    rootPackage: SpdxPackage,
    runtimeDocumentId: String,
    runtimeDocumentChecksum: Checksum,
    runtimeRootPackageId: String,
  ) {
    val runtimeRef = createExternalDocumentRef(
      modelStore.getNextId(IdType.DocumentRef, documentUri),
      runtimeDocumentId, runtimeDocumentChecksum
    )
    externalDocumentRefs.add(runtimeRef)
    val runtimePackage = ExternalSpdxElement(modelStore, documentUri, "${runtimeRef.id}:$runtimeRootPackageId", copyManager, true)
    runtimePackage.validate()
    rootPackage.relatesTo(runtimePackage, RelationshipType.CONTAINS)
  }

  /**
   * [org.jetbrains.intellij.build.BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP] step is skipped,
   * but documents with all distributions content specified will be built anyway.
   */
  private suspend fun generateFromContentReport(): List<Path> {
    return SUPPORTED_DISTRIBUTIONS.asFlow().filter { (os, arch) ->
      context.shouldBuildDistributionForOS(os, arch)
    }.map { (os, arch) ->
      val distributionDir = getOsAndArchSpecificDistDirectory(os, arch, context)
      val name = context.productProperties.getBaseArtifactName(context) + "-${distributionDir.name}"
      val document = spdxDocument(name)
      val rootPackage = document.spdxPackage(name) {
        setVersionInfo(version)
          .setDownloadLocation(SpdxConstants.NOASSERTION_VALUE)
          .setSupplier(creator)
      }
      document.documentDescribes.add(rootPackage)
      generate(
        document, rootPackage,
        runtimePackage = document.runtimePackage(os, arch),
        distributionDir = distributionDir,
        // distributions weren't built
        claimContainedFiles = false
      )
    }.toList()
  }

  private fun generate(
    document: SpdxDocument,
    rootPackage: SpdxPackage,
    runtimePackage: SpdxPackage?,
    distributionDir: Path,
    claimContainedFiles: Boolean = true
  ): Path {
    val filePackages = generatePackagesForDistributionFiles(document, distributionDir)
    if (claimContainedFiles) {
      val containedPackages = filePackages.values
        .asSequence()
        .plus(runtimePackage)
        .filterNotNull()
        .toList()
      containedPackages.forEach {
        rootPackage.relatesTo(it, RelationshipType.CONTAINS)
      }
      rootPackage.claimContainedFiles(containedPackages.flatMap { it.files }, document)
    }
    val libraryPackages = mavenLibraries.mapNotNull { lib ->
      val libraryPackage = document.spdxPackage(lib)
      val filePackage = filePackages[lib.entry.path] ?: return@mapNotNull null
      filePackage.relatesTo(libraryPackage, RelationshipType.DEPENDS_ON, "repacked from")
      libraryPackage
    }
    val duplicates = libraryPackages.asSequence().filter {
      it.externalRefs.any()
    }.groupBy {
      checkNotNull(it.externalRefs.singleOrNull()?.referenceLocator) {
        "Single external reference is expected for ${it.name.get()} but got " +
        it.externalRefs.joinToString(separator = "\n")
      }
    }.filterValues { it.count() > 1 }
    check(duplicates.isEmpty()) {
      duplicates.entries.joinToString(separator = "\n") { (refLocator, packages) ->
        packages.joinToString(prefix = "Duplicated library locator $refLocator\n\t", separator = "\n\t") {
          it.name.get()
        }
      }
    }
    document.write()
    document.validate()
    return document.outputFile
  }

  private val distributionFilesChecksums: List<Checksums> by lazy {
    runBlocking(Dispatchers.IO) {
      distributionFiles.asSequence()
        .filterIsInstance<LibraryFileEntry>()
        .map { it.path }.distinct()
        // non-bundled plugins, for example
        .filterNot { it.startsWith(context.paths.tempDir) }
        .toList().map { async { Checksums(it) } }
        .map { it.await() }
    }
  }

  private fun generatePackagesForDistributionFiles(document: SpdxDocument, distributionDir: Path): Map<Path, SpdxPackage?> {
    return distributionFilesChecksums.associate {
      val filePath = when {
        it.path.startsWith(distributionDir) -> distributionDir.relativize(it.path)
        it.path.startsWith(context.paths.distAllDir) -> context.paths.distAllDir.relativize(it.path)
        else -> return@associate it.path to null
      }
      val filePackage = document.spdxPackage("./$filePath", sha256sum = it.sha256sum, sha1sum = it.sha1sum) {
        setVersionInfo(version)
          .setDownloadLocation(SpdxConstants.NOASSERTION_VALUE)
          .setSupplier(creator)
      }
      filePackage.claimContainedFiles(document = document)
      filePackage.validate()
      it.path to filePackage
    }
  }

  private fun SpdxPackage.relatesTo(other: SpdxElement, type: RelationshipType, comment: String? = null) {
    val relationship = createRelationship(other, type, comment)
    addRelationship(relationship)
  }

  private val mavenLibraries: List<MavenLibrary> by lazy {
    runBlocking(Dispatchers.IO) {
      val usedModulesNames = distributionFiles.includedModules.toSet()
      val usedModules = context.project.modules.asSequence().filter {
        usedModulesNames.contains(it.name)
      }.toSet()
      val librariesBundledInDistributions = distributionFiles.asSequence()
        .filterIsInstance<LibraryFileEntry>()
        .associateBy {
          when (it) {
            is ProjectLibraryEntry -> it.data.libraryName
            is ModuleLibraryFileEntry -> it.libraryName
          }
        }
      usedModules.asSequence().flatMap { module ->
        JpsJavaExtensionService.dependencies(module)
          .includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
          .libraries.asSequence()
          .map { it to module }
      }.distinctBy {
        it.first.mavenDescriptor?.mavenId ?: it.first.name
      }.groupBy({ it.first }, { it.second }).map { (library, modules) ->
        async {
          val libraryName = LibraryLicensesListGenerator.getLibraryName(library)
          val libraryEntry = librariesBundledInDistributions[libraryName]
          val libraryFile = libraryEntry?.libraryFile ?: return@async null
          val libraryLicense = context.productProperties.allLibraryLicenses.firstOrNull {
            it.getLibraryNames().contains(libraryName)
          }
          checkNotNull(libraryLicense) {
            "Missing license for '$libraryName' used in ${modules.joinToString { "'${it.name}'" }} modules"
          }
          val mavenDescriptor = library.mavenDescriptor
          if (mavenDescriptor != null) {
            mavenLibrary(mavenDescriptor, libraryFile, libraryEntry, libraryLicense)
          }
          else {
            anonymousMavenLibrary(libraryFile, libraryEntry, libraryLicense)
          }
        }
      }.mapNotNull { it.await() }.toList()
    }
  }

  private val JpsLibrary.mavenDescriptor: JpsMavenRepositoryLibraryDescriptor?
    get() = asTyped(JpsRepositoryLibraryType.INSTANCE)?.properties?.data

  private fun mavenLibrary(
    mavenDescriptor: JpsMavenRepositoryLibraryDescriptor,
    libraryFile: Path,
    libraryEntry: LibraryFileEntry,
    libraryLicense: LibraryLicense
  ): MavenLibrary {
    val coordinates = MavenCoordinates(mavenDescriptor.groupId, mavenDescriptor.artifactId, mavenDescriptor.version)
    val repositoryUrl = if (mavenDescriptor.jarRepositoryId != null) {
      repositories
        .firstOrNull { it.id == mavenDescriptor.jarRepositoryId }
        ?.let { translateRepositoryUrl(it.url) }
        ?.removeSuffix("/")
      ?: error("Unknown jar repository ID: ${mavenDescriptor.jarRepositoryId}")
    }
    else null
    val libraryName = coordinates.getFileName(packaging = mavenDescriptor.packaging, classifier = "")
    val checksums = mavenDescriptor.artifactsVerification.filter {
      Path.of(JpsPathUtil.urlToOsPath(it.url)).name == libraryName
    }
    check(checksums.count() == 1) {
      "Missing checksum for $coordinates: ${checksums.map { it.url }}"
    }
    val pomXmlName = coordinates.getFileName(packaging = "pom", classifier = "")
    val pomXmlModel = libraryFile
      .resolveSibling(libraryFile.nameWithoutExtension + ".pom")
      .takeIf { it.exists() }
      ?.bufferedReader()?.use {
        MavenXpp3Reader().read(it, false)
      }
    return MavenLibrary(
      coordinates = coordinates,
      repositoryUrl = repositoryUrl,
      downloadUrl = repositoryUrl?.let { "$it/${coordinates.directoryPath}/$libraryName" },
      pomXmlUrl = repositoryUrl?.let { "$it/${coordinates.directoryPath}/$pomXmlName" },
      sha256Checksum = checksums.single().sha256sum,
      license = libraryLicense,
      entry = libraryEntry,
      pomXmlModel = pomXmlModel
    )
  }

  private val ByteBuffer.reader: Reader
    get() = ByteArray(remaining())
      .also(::get)
      .inputStream()
      .bufferedReader()

  private fun anonymousMavenLibrary(libraryFile: Path,
                                    libraryEntry: LibraryFileEntry,
                                    libraryLicense: LibraryLicense): MavenLibrary? {
    var coordinates: MavenCoordinates? = null
    var pomXmlModel: Model? = null
    readZipFile(libraryFile) { name, data ->
      when {
        !name.startsWith("META-INF/") -> return@readZipFile
        name.endsWith("/pom.xml") -> data().reader.use {
          pomXmlModel = MavenXpp3Reader().read(it, false)
        }
        name.endsWith("/pom.properties") -> {
          val pom = Properties()
          data().reader.use(pom::load)
          coordinates = MavenCoordinates(
            groupId = pom.getProperty("groupId"),
            artifactId = pom.getProperty("artifactId"),
            version = pom.getProperty("version")
          )
        }
      }
    }
    return MavenLibrary(
      coordinates = coordinates ?: return null,
      license = libraryLicense,
      entry = libraryEntry,
      sha256Checksum = Files.newInputStream(libraryFile).use(DigestUtils::sha256Hex),
      pomXmlModel = pomXmlModel
    )
  }

  private fun MavenCoordinates.externalRef(document: SpdxDocument, repositoryUrl: String): ExternalRef {
    val (refType, locator) = when (repositoryUrl) {
      "https://repo1.maven.org/maven2" -> "maven-central" to "$groupId:$artifactId:$version"
      else -> "purl" to "pkg:maven/$groupId/$artifactId@$version?repository_url=$repositoryUrl"
    }
    return document.createExternalRef(ReferenceCategory.PACKAGE_MANAGER,
                                      ReferenceType(SpdxConstants.SPDX_LISTED_REFERENCE_TYPES_PREFIX + refType),
                                      locator, null)
  }

  private fun translateSupplier(supplier: String): String {
    return when (supplier) {
      "JetBrains" -> Suppliers.JETBRAINS
      "Google" -> Suppliers.GOOGLE
      else -> supplier
    }
  }

  private fun translateRepositoryUrl(url: String): String {
    return url.replace("https://cache-redirector.jetbrains.com/", "https://")
  }

  private inner class MavenLibrary(
    val coordinates: MavenCoordinates,
    val repositoryUrl: String? = null,
    val downloadUrl: String? = null,
    val sha256Checksum: String,
    val license: LibraryLicense,
    val entry: LibraryFileEntry,
    val pomXmlUrl: String? = null,
    pomXmlModel: Model?
  ) {
    val organizations = (pomXmlModel?.organization?.name ?: pomXmlModel
      ?.developers?.asSequence()
      ?.mapNotNull { it.organization }
      ?.filter { it.isNotBlank() }
      ?.map(::translateSupplier)
      ?.distinct()
      ?.joinToString())
      ?.takeIf { it.isNotBlank() }
      ?.let { "Organization: $it" }

    val developers = pomXmlModel?.developers?.asSequence()
      ?.mapNotNull {
        when {
          it.name?.isNotBlank() == true && it.email?.isNotBlank() == true -> "${translateSupplier(it.name)} <${it.email}>"
          it.name?.isNotBlank() == true -> translateSupplier(it.name)
          it.email?.isNotBlank() == true -> "<${it.email}>"
          else -> return@mapNotNull null
        }
      }?.distinct()
      ?.joinToString()
      ?.takeIf { it.isNotBlank() }
      ?.let { "Person: $it" }

    val supplier: String? = license.supplier ?: organizations ?: developers

    fun license(document: SpdxDocument): AnyLicenseInfo {
      return when {
        license.licenseUrl == null || license.spdxIdentifier == null -> SpdxNoAssertionLicense()
        license.license == LibraryLicense.JETBRAINS_OWN -> document.jetBrainsOwnLicense
        else -> document.parseLicense(checkNotNull(license.spdxIdentifier))
      }
    }
  }

  private val SpdxDocument.jetBrainsOwnLicense: AnyLicenseInfo
    get() = extractedLicenseInfo(
      name = this@SoftwareBillOfMaterialsImpl.jetBrainsOwnLicense.name,
      text = this@SoftwareBillOfMaterialsImpl.jetBrainsOwnLicense.text,
      url = this@SoftwareBillOfMaterialsImpl.jetBrainsOwnLicense.url
    )

  /**
   * @param id one of [SpdxConstants.LISTED_LICENSE_URL]
   */
  private fun SpdxDocument.parseLicense(id: String): AnyLicenseInfo {
    return try {
      LicenseInfoFactory.parseSPDXLicenseString(id, modelStore, documentUri, copyManager)
    }
    catch (e: InvalidLicenseStringException) {
      throw IllegalArgumentException(id).apply { addSuppressed(e) }
    }
  }

  private fun SpdxDocument.extractedLicenseInfo(name: String, text: String, url: String?): ExtractedLicenseInfo {
    // must only contain letters, numbers, "." and "-" and must begin with "LicenseRef-"
    val licenseRefId = "LicenseRef-$name"
      .replace(" ", "-")
      .replace("/", "-")
      .replace("+", "-")
      .replace("_", "-")
    val licenseInfo = ExtractedLicenseInfo(
      modelStore, documentUri, licenseRefId,
      copyManager, true
    )
    licenseInfo.extractedText = text
    if (url != null) licenseInfo.seeAlso.add(url)
    licenseInfo.validate()
    addExtractedLicenseInfos(licenseInfo)
    return licenseInfo
  }

  private fun SpdxDocument.spdxPackage(library: MavenLibrary): SpdxPackage {
    val document = this
    val upstreamPackage = spdxPackageUpstream(library.license.forkedFrom)
    val libPackage = spdxPackage("${library.coordinates.groupId}:${library.coordinates.artifactId}", library.license(this)) {
      setVersionInfo(checkNotNull(library.coordinates.version) {
        "Missing version for ${library.coordinates}"
      })
      setDownloadLocation(library.downloadUrl ?: SpdxConstants.NOASSERTION_VALUE)
      setOrigin(library, upstreamPackage)
      addChecksum(createChecksum(ChecksumAlgorithm.SHA256, library.sha256Checksum))
      if (library.repositoryUrl != null) {
        addExternalRef(library.coordinates.externalRef(document, library.repositoryUrl))
      }
    }
    if (upstreamPackage != null) {
      libPackage.relatesTo(upstreamPackage, RelationshipType.VARIANT_OF)
    }
    return libPackage
  }

  private fun SpdxDocument.spdxPackageUpstream(upstream: LibraryUpstream?): SpdxPackage? {
    if (upstream?.version == null || upstream.mavenRepositoryUrl == null) {
      return null
    }
    return spdxPackage("${upstream.groupId}:${upstream.artifactId}") {
      setVersionInfo(upstream.version)
      setSupplier(upstream.license.supplier ?: SpdxConstants.NOASSERTION_VALUE)
      val coordinates = MavenCoordinates(
        groupId = upstream.groupId,
        artifactId = upstream.artifactId,
        version = checkNotNull(upstream.version) {
          "Missing version for ${upstream.groupId}:${upstream.artifactId}"
        }
      )
      val repositoryUrl = checkNotNull(upstream.mavenRepositoryUrl) {
        "Missing Maven repository url for ${upstream.groupId}:${upstream.artifactId}"
      }.removeSuffix("/")
      val jarName = coordinates.getFileName(packaging = "jar", classifier = "")
      setDownloadLocation("$repositoryUrl/${coordinates.directoryPath}/$jarName")
      addExternalRef(coordinates.externalRef(this@spdxPackageUpstream, repositoryUrl))
    }
  }

  private fun SpdxPackageBuilder.setOrigin(library: MavenLibrary, upstreamPackage: SpdxPackage?) {
    when {
      library.supplier != null -> setSupplier(library.supplier)
      library.license.license == LibraryLicense.JETBRAINS_OWN ||
      library.license.url?.startsWith("https://github.com/JetBrains/") == true ||
      library.license.licenseUrl?.startsWith("https://github.com/JetBrains/") == true ||
      library.license.url?.startsWith("https://github.com/Kotlin/") == true ||
      library.license.licenseUrl?.startsWith("https://github.com/Kotlin/") == true -> {
        setSupplier("Organization: ${Suppliers.JETBRAINS}")
      }
      library.license.url?.startsWith("https://github.com/apache/") == true ||
      library.license.licenseUrl?.startsWith("https://github.com/apache/") == true -> {
        setSupplier("Organization: ${Suppliers.APACHE}")
      }
      library.license.url?.startsWith("https://github.com/google/") == true ||
      library.license.licenseUrl?.startsWith("https://github.com/google/") == true -> {
        setSupplier("Organization: ${Suppliers.GOOGLE}")
      }
      else -> {
        setSupplier(SpdxConstants.NOASSERTION_VALUE)
        if (library.pomXmlUrl != null) {
          setSourceInfo("Supplier information is not available in ${library.pomXmlUrl}")
        }
      }
    }
    val upstream = library.license.forkedFrom
    when {
      upstreamPackage != null -> setOriginator(upstreamPackage.supplier.get())
      upstream?.revision != null && upstream.sourceCodeUrl != null -> {
        setSourceInfo("Forked from a revision ${upstream.revision} of ${upstream.sourceCodeUrl}")
      }
      upstream?.sourceCodeUrl != null -> {
        setSourceInfo("Forked from ${upstream.sourceCodeUrl}, exact revision is not available")
      }
    }
  }

  private fun SpdxDocument.spdxPackage(name: String,
                                       licenseDeclared: AnyLicenseInfo = SpdxNoAssertionLicense(),
                                       sha256sum: String, sha1sum: String,
                                       init: SpdxPackageBuilder.() -> Unit): SpdxPackage {
    return spdxPackage(name, licenseDeclared) {
      addFile(createSpdxFile(
        modelStore.getNextId(IdType.SpdxId, documentUri),
        name, licenseDeclared, null, SpdxConstants.NONE_VALUE,
        createChecksum(ChecksumAlgorithm.SHA1, sha1sum)
      ).addChecksum(createChecksum(ChecksumAlgorithm.SHA256, sha256sum)).build())
      init()
    }
  }

  private fun SpdxDocument.spdxPackage(name: String,
                                       licenseDeclared: AnyLicenseInfo = SpdxNoAssertionLicense(),
                                       init: SpdxPackageBuilder.() -> Unit): SpdxPackage {
    return createPackage(
      modelStore.getNextId(IdType.SpdxId, documentUri), name,
      SpdxNoAssertionLicense(modelStore, documentUri),
      SpdxConstants.NOASSERTION_VALUE,
      licenseDeclared
    ).setFilesAnalyzed(false).apply(init).build()
  }

  private fun SpdxPackage.claimContainedFiles(files: Collection<SpdxFile> = this.files, document: SpdxDocument) {
    copyrightText = requireNotNull(context.productProperties.sbomOptions.copyrightText) {
      "Copyright text isn't specified"
    }
    licenseDeclared = document.extractedLicenseInfo(
      name = license.name,
      text = license.text,
      url = license.url
    )
    setPackageVerificationCode(document.createPackageVerificationCode(
      packageVerificationCode(files),
      emptyList()
    ))
    setFilesAnalyzed(true)
  }

  /**
   * See https://spdx.github.io/spdx-spec/v2.3/package-information/#79-package-verification-code-field
   */
  private fun packageVerificationCode(files: Collection<SpdxFile>): String {
    check(files.any())
    return files.asSequence()
      .map { it.sha1 }.sorted()
      .joinToString(separator = "")
      .let(DigestUtils::sha1Hex)
  }

  /**
   * See https://pypi.org/project/ntia-conformance-checker/
   */
  private suspend fun checkNtiaConformance(documents: List<Path>) {
    if (Docker.isAvailable && !SystemInfoRt.isWindows) {
      val ntiaChecker = "ntia-checker"
      suspendingRetryWithExponentialBackOff {
        runProcess(
          "docker", "build", ".", "--tag", ntiaChecker,
          workingDir = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/sbom/$ntiaChecker")
        )
      }
      coroutineScope {
        documents.forEach {
          launch {
            try {
              runProcess(
                "docker", "run", "--rm",
                "--volume=${it.parent}:${it.parent}:ro",
                ntiaChecker, "--file", "${it.toAbsolutePath()}", "--verbose"
              )
            }
            catch (e: Exception) {
              context.messages.error("""
                 Generated SBOM $it is not NTIA-conformant. 
                 Please search for 'Components missing an supplier' error message and specify all missing suppliers.
                 You may use https://package-search.jetbrains.com/ to search for them.
              """.trimIndent(), e)
            }
          }
        }
      }
    }
  }
}
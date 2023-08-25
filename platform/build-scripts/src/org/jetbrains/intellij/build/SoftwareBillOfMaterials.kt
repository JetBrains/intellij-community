// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.outputStream
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsRepositoryLibraryType
import org.jetbrains.jps.util.JpsPathUtil
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

/**
 * Generates Software Bill Of Materials (SBOM) for each distribution file in [SPDX format](https://spdx.github.io/spdx-spec)
 */
class SoftwareBillOfMaterials internal constructor(
  private val context: BuildContext,
  private val distributions: List<DistributionForOsTaskResult>,
  private val distributionFiles: List<DistributionFileEntry>
) {
  companion object {
    const val STEP_ID: String = "sbom"
  }

  class Options {
    /**
     * [Specification](https://spdx.github.io/spdx-spec/v2.3/document-creation-information/#68-creator-field)
     */
    var creator: String = "Organization: JetBrains s.r.o."

    var copyrightText: String? = "Copyright 2000-2023 JetBrains s.r.o. and contributors"

    var license: DistributionLicense? = DistributionLicense.JETBRAINS

    class DistributionLicense(val name: String, val text: String, val url: String?) {
      internal companion object {
        val JETBRAINS = DistributionLicense(LibraryLicense.JETBRAINS_OWN, LibraryLicense.JETBRAINS_OWN, null)
      }
    }
  }

  private val specVersion: String = Version.TWO_POINT_THREE_VERSION

  private val creator: String
    get() = context.options.sbomOptions.creator

  private val version: String
    get() = "${context.applicationInfo.productCode}-${context.buildNumber}"

  private val baseDownloadUrl: String?
    get() = context.productProperties.baseDownloadUrl?.removeSuffix("/")

  private val license: Options.DistributionLicense by lazy {
    when (val license = context.options.sbomOptions.license) {
      null -> throw IllegalArgumentException("Distribution license isn't specified")
      Options.DistributionLicense.JETBRAINS -> {
        /**
         * See [com.intellij.ide.gdpr.EndUserAgreement]
         */
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
          text = eula.readText(),
          url = "https://www.jetbrains.com/legal/docs/toolbox/user/"
        )
      }
      else -> license
    }
  }

  private val repositories by lazy {
    JpsRemoteRepositoryService.getInstance()
      .getOrCreateRemoteRepositoriesConfiguration(context.project)
      .repositories
  }

  private val DistributionForOsTaskResult.extension: String
    get() = when (builder.targetOs) {
      OsFamily.LINUX -> ".tar.gz"
      OsFamily.MACOS -> ".dmg"
      OsFamily.WINDOWS -> ".exe"
    }

  private val DistributionForOsTaskResult.files: List<Path>
    get() = when (builder.targetOs) {
              // only the first existing extension will be processed
              OsFamily.LINUX -> sequenceOf(".tar.gz", ".snap")
              OsFamily.MACOS -> sequenceOf(".dmg", ".sit", ".mac.${arch.name}.zip")
              OsFamily.WINDOWS -> sequenceOf(".exe", ".win.zip")
            }.map { extension ->
      context.paths.artifactDir.resolve(context.productProperties.getBaseArtifactName(context) +
                                        OsSpecificDistributionBuilder.suffix(arch) +
                                        extension)
    }.filter { it.exists() }.firstOrNull()?.let(::listOf) ?: emptyList()

  private fun spdxDocument(name: String): SpdxDocument {
    val uri = "$baseDownloadUrl/$specVersion/$name.spdx"
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

  private fun SpdxDocument.write(): Path {
    val result = context.paths.artifactDir.resolve("${name.get()}.spdx")
    result.outputStream().use {
      (modelStore as ISerializableModelStore).serialize(documentUri, it)
    }
    return result
  }

  private fun ModelObject.validate() {
    val errors = verify()
    check(errors.none()) {
      errors.joinToString(separator = "\n")
    }
  }

  internal suspend fun generate() {
    if (!context.shouldBuildDistributions()) {
      Span.current().addEvent("No distribution was built, skipping")
      return
    }
    if (baseDownloadUrl == null) {
      Span.current().addEvent("Base download url isn't specified, skipping")
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
        val name = context.productProperties.getBaseArtifactName(context) +
                   OsSpecificDistributionBuilder.suffix(distribution.arch) +
                   distribution.extension
        val document = spdxDocument(name)
        val rootPackage = document.spdxPackage(it.path.name, sha256sum = it.sha256sum, sha1sum = it.sha1sum) {
          setVersionInfo(version)
            .setDownloadLocation("$baseDownloadUrl/$name")
            .setSupplier(creator)
        }
        document.documentDescribes.add(rootPackage)
        generate(document, rootPackage, distribution.outDir)
      }
    }
  }

  /**
   * [org.jetbrains.intellij.build.BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP] step is skipped,
   * but documents with all distributions content specified will be built anyway.
   */
  private fun generateFromContentReport(): List<Path> {
    return SUPPORTED_DISTRIBUTIONS.asSequence().filter { (os, arch) ->
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
        document, rootPackage, distributionDir,
        // distributions weren't built
        claimContainedFiles = false
      )
    }.toList()
  }

  private fun generate(document: SpdxDocument, rootPackage: SpdxPackage, distributionDir: Path, claimContainedFiles: Boolean = true): Path {
    val filePackages = generatePackagesForDistributionFiles(document, rootPackage, distributionDir)
    if (claimContainedFiles) {
      rootPackage.claimContainedFiles(filePackages.values.filterNotNull().flatMap { it.files }, document)
    }
    val libraryPackages = mavenLibraries.mapNotNull { lib ->
      val libraryPackage = document.spdxPackage(lib)
      val filePackage = filePackages[lib.entry.path] ?: return@mapNotNull null
      filePackage.relatesTo(libraryPackage, RelationshipType.DEPENDS_ON, "repacked into")
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
    document.validate()
    return document.write()
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

  private fun generatePackagesForDistributionFiles(document: SpdxDocument,
                                                   rootPackage: SpdxPackage,
                                                   distributionDir: Path): Map<Path, SpdxPackage?> {
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
      rootPackage.relatesTo(filePackage, RelationshipType.CONTAINS)
      filePackage.claimContainedFiles(filePackage.files, document)
      filePackage.validate()
      it.path to filePackage
    }
  }

  private fun SpdxPackage.relatesTo(other: SpdxPackage, type: RelationshipType, comment: String? = null) {
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
    checkNotNull(mavenDescriptor.jarRepositoryId) {
      "Missing jar repository ID for $coordinates"
    }
    val repositoryUrl = repositories
      .firstOrNull { it.id == mavenDescriptor.jarRepositoryId }
      ?.let { translateRepositoryUrl(it.url) }
      ?.removeSuffix("/")
    checkNotNull(repositoryUrl) {
      "Unknown jar repository ID: ${mavenDescriptor.jarRepositoryId}"
    }
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
      downloadUrl = "$repositoryUrl/${coordinates.directoryPath}/$libraryName",
      pomXmlUrl = "$repositoryUrl/${coordinates.directoryPath}/$pomXmlName",
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

  private val LibraryLicense.supplier: String?
    get() {
      val website = url ?: ""
      return "Organization: " + when {
        license == LibraryLicense.JETBRAINS_OWN ||
        website.startsWith("https://github.com/JetBrains/") ||
        website.startsWith("https://github.com/Kotlin/") -> "JetBrains s.r.o."
        website.startsWith("https://github.com/google/") ||
        website.startsWith("https://source.android.com/") -> "Google LLC"
        else -> return null
      }
    }

  private fun translateSupplier(supplier: String): String {
    return when (supplier) {
      "JetBrains" -> "JetBrains s.r.o."
      "Google" -> "Google LLC"
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
    val supplier: String? =
      (pomXmlModel?.organization?.name ?: pomXmlModel
        ?.developers?.asSequence()
        ?.mapNotNull { it.organization }
        ?.distinct()
        ?.singleOrNull())
        ?.takeIf { it.isNotBlank() }
        ?.let(::translateSupplier)
        ?.let { "Organization: $it" }
      ?: pomXmlModel?.developers?.singleOrNull()?.let {
        "Person: " + when {
          it.name?.isNotBlank() == true && it.email?.isNotBlank() == true -> "${it.name} <${it.email}>"
          it.name?.isNotBlank() == true -> it.name
          it.email?.isNotBlank() == true -> "<${it.email}>"
          else -> return@let null
        }
      } ?: license.supplier

    fun license(document: SpdxDocument): AnyLicenseInfo {
      return when {
        license.licenseUrl == null ||
        license.spdxIdentifier == null ||
        license.license == LibraryLicense.JETBRAINS_OWN -> SpdxNoAssertionLicense()
        else -> document.parseLicense(checkNotNull(license.spdxIdentifier))
      }
    }
  }

  /**
   * @param id one of [SpdxConstants.LISTED_LICENSE_URL]
   */
  private fun SpdxDocument.parseLicense(id: String): AnyLicenseInfo {
    return LicenseInfoFactory.parseSPDXLicenseString(id, modelStore, documentUri, copyManager)
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
      setSupplier(library.supplier ?: SpdxConstants.NOASSERTION_VALUE)
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
    val upstream = library.license.forkedFrom
    when {
      library.supplier == null -> {
        check(upstream == null && upstreamPackage == null) {
          "Missing supplier for ${library.coordinates} which is a fork of ${upstream?.groupId}:${upstream?.artifactId}"
        }
        if (library.pomXmlUrl != null) {
          setSourceInfo("Supplier information is not available in ${library.pomXmlUrl}")
        }
      }
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

  private fun SpdxPackage.claimContainedFiles(files: Collection<SpdxFile>, document: SpdxDocument) {
    copyrightText = requireNotNull(context.options.sbomOptions.copyrightText) {
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
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * How long an EAP build is allowed to be, in days.
 *
 * Mirrors `com.intellij.ide.license.common.EAPProfile.getExpirationPeriodDays`, whose default every IDEA-family
 * product keeps (RustRover and the IntelliJ language server override it upwards, so this is the strictest budget).
 * Duplicated rather than referenced because the licensing code is a product module and this is the build.
 */
const val EAP_EXPIRATION_PERIOD_DAYS: Int = 30

/** The `date` attribute format of `<build>` in `ApplicationInfo.xml`. */
private val APP_INFO_BUILD_DATE_PATTERN = DateTimeFormatter.ofPattern("uuuuMMddHHmm")

private val APP_INFO_ENTRY_REGEX = Regex("idea/[A-Za-z]*ApplicationInfo\\.xml")

/**
 * Why an EAP build stamped with [buildTime] would refuse to start at [now], or `null` if it starts.
 *
 * This is the build-side mirror of `com.intellij.ide.license.impl.UnifiedLicenseManager.initLicenses`, and it has to
 * stay a mirror of *both* of its halves: an EAP build is expired when it is older than the expiration period **and**
 * when its date is more than a day in the future. Only the first half used to be checked here, which is how a
 * distribution stamped with a far-future date shipped and then died at startup with "EAP build expired".
 */
fun eapBuildExpirationProblem(
  buildTime: Instant,
  now: Instant,
  expirationPeriodDays: Int = EAP_EXPIRATION_PERIOD_DAYS,
): String? {
  val elapsed = Duration.between(buildTime, now)
  val stamp = ZonedDateTime.ofInstant(buildTime, ZoneOffset.UTC).toLocalDateTime()
  return when {
    // `UnifiedLicenseManager` tolerates one day of skew to cover timezone differences, and treats anything beyond
    // that as a tampered clock - a far-future date is just as fatal as an ancient one.
    elapsed.isNegative && elapsed.negated() > Duration.ofDays(1) ->
      "build date $stamp UTC is ${elapsed.negated().toDays()} day(s) in the future " +
      "(anything over 1 day ahead reads as an expired EAP build)"
    elapsed > Duration.ofDays(expirationPeriodDays.toLong()) ->
      "build date $stamp UTC is ${elapsed.toDays()} day(s) old, " +
      "${elapsed.toDays() - expirationPeriodDays} day(s) past the $expirationPeriodDays-day EAP expiration period"
    else -> null
  }
}

/**
 * The same verdict for an assembled distribution on disk, read from the `ApplicationInfo.xml` it ships.
 *
 * Worth running immediately before a launch, which is the only moment that can decide it: a distribution is assembled
 * once and reused for as long as its inputs are unchanged, so "was it valid when it was built" is a different question
 * from "will the IDE come up". A UI lane that skips this pays its whole launch timeout and gets a verdict naming a
 * stalled daemon instead of an expired build. Reading the central directory of every `lib/` jar costs a few hundred
 * milliseconds against that.
 *
 * Returns `null` when the distribution starts - including when it stamps no build date at all, which is what a dev
 * distribution does and what makes it immune (see [computeAppInfoXml]).
 *
 * Only the top level of `lib/` is read: an embedded frontend under `lib/frontend-split/` carries a different product's
 * descriptor and is not what this launch starts.
 */
fun distributionExpirationProblem(
  distributionHome: Path,
  now: Instant = Instant.now(),
  expirationPeriodDays: Int = EAP_EXPIRATION_PERIOD_DAYS,
): String? {
  val lib = distributionHome.resolve("lib")
  if (!lib.isDirectory()) {
    return null
  }
  for (jar in lib.listDirectoryEntries().sorted()) {
    if (jar.extension != "jar" || !Files.isRegularFile(jar)) {
      continue
    }
    var problem: String? = null
    readZipFile(jar) { name, data ->
      if (!APP_INFO_ENTRY_REGEX.matches(name)) {
        ZipEntryProcessorResult.CONTINUE
      }
      else {
        val appInfoXml = Charsets.UTF_8.decode(data()).toString()
        // `lib/` also carries the platform's own `PlatformApplicationInfo.xml` fixtures, which keep their
        // `__BUILD_NUMBER__` placeholder because no product is built from them. Judging a distribution by one of those
        // would answer for the wrong IDE.
        if (appInfoXml.contains("__BUILD_NUMBER__")) {
          ZipEntryProcessorResult.CONTINUE
        }
        else {
          problem = appInfoExpirationProblem(appInfoXml = appInfoXml, now = now, expirationPeriodDays = expirationPeriodDays)
            ?.let { "$it (from ${jar.fileName}!/$name)" }
          // stop at the first descriptor that would keep the IDE from starting; the rest cannot make it start again
          if (problem == null) ZipEntryProcessorResult.CONTINUE else ZipEntryProcessorResult.STOP
        }
      }
    }
    problem?.let { return it }
  }
  return null
}

/**
 * Why the IDE described by [appInfoXml] would refuse to start at [now], or `null` if it starts.
 *
 * A non-EAP descriptor never expires, and neither does one whose `date` is absent or still the `__BUILD_DATE__`
 * placeholder - `ApplicationInfoImpl.readBuildInfo` leaves `buildTime` unset for those and `getBuildTime()` then
 * answers the startup time.
 */
fun appInfoExpirationProblem(
  appInfoXml: String,
  now: Instant,
  expirationPeriodDays: Int = EAP_EXPIRATION_PERIOD_DAYS,
): String? {
  val root = readXmlAsModel(appInfoXml.encodeToByteArray())
  if (root.getChild("version")?.getAttributeValue("eap")?.toBoolean() != true) {
    return null
  }
  val buildTime = parseAppInfoBuildDate(root.getChild("build")?.getAttributeValue("date")) ?: return null
  return eapBuildExpirationProblem(buildTime = buildTime, now = now, expirationPeriodDays = expirationPeriodDays)
}

private fun parseAppInfoBuildDate(value: String?): Instant? {
  if (value == null || value.startsWith("__")) {
    return null
  }
  val parsed = try {
    APP_INFO_BUILD_DATE_PATTERN.parse(value)
  }
  catch (_: Exception) {
    // `ApplicationInfoImpl.parseDate` swallows an unparseable date and falls back to the startup time, so the IDE
    // starts and there is nothing to report.
    return null
  }
  return ZonedDateTime.of(LocalDateTime.from(parsed), ZoneOffset.UTC).toInstant()
}


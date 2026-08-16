// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

/**
 * The arithmetic `com.intellij.ide.license.impl.UnifiedLicenseManager.initLicenses` runs a few seconds after startup,
 * decided here instead - at build time against the date about to be stamped, and at launch time against the date a
 * distribution already carries.
 *
 * Both halves matter: the future-date half is the one that was missing, and a distribution stamped eleven years ahead
 * shipped and then refused to start.
 */
class EapBuildExpirationTest {
  private val now: Instant = utc(2026, 8, 16, 12)

  @Test
  fun aFreshBuildStarts() {
    assertThat(eapBuildExpirationProblem(buildTime = now - Duration.ofDays(29), now = now)).isNull()
  }

  @Test
  fun aBuildPastTheExpirationPeriodDoesNot() {
    val problem = eapBuildExpirationProblem(buildTime = now - Duration.ofDays(45), now = now)

    assertThat(problem)
      .contains("2026-07-02T12:00 UTC")
      .contains("45 day(s) old")
      .contains("15 day(s) past the 30-day EAP expiration period")
  }

  @Test
  fun aBuildDatedInTheFutureDoesNotEither() {
    // What the dev distribution used to stamp: an epoch chosen to outrun the expiration period, which the licensing
    // code reads as a tampered clock and rejects just as hard.
    val problem = eapBuildExpirationProblem(buildTime = utc(2038, 1, 1, 0), now = now)

    assertThat(problem)
      .contains("2038-01-01T00:00 UTC")
      .contains("in the future")
  }

  @Test
  fun aDayOfClockSkewIsTolerated() {
    // `UnifiedLicenseManager` allows exactly this much to cover timezone differences.
    assertThat(eapBuildExpirationProblem(buildTime = now + Duration.ofHours(20), now = now)).isNull()
  }

  @Test
  fun aLongerExpirationPeriodIsHonoured() {
    // RustRover and the IntelliJ language server run on 60 days.
    assertThat(eapBuildExpirationProblem(buildTime = now - Duration.ofDays(45), now = now, expirationPeriodDays = 60)).isNull()
  }

  @Test
  fun anExpiredDescriptorIsRejected() {
    assertThat(appInfoExpirationProblem(appInfoXml = appInfoXml(eap = true, date = "203801010000"), now = now))
      .contains("in the future")
  }

  @Test
  fun aDescriptorWithoutAStampedDateNeverExpires() {
    // The dev-distribution shape: `ApplicationInfoImpl.readBuildInfo` leaves `buildTime` unset for the placeholder and
    // `getBuildTime()` answers the startup time, so there is nothing to run out.
    assertThat(appInfoExpirationProblem(appInfoXml = appInfoXml(eap = true, date = "__BUILD_DATE__"), now = now)).isNull()
    assertThat(appInfoExpirationProblem(appInfoXml = appInfoXml(eap = true, date = null), now = now)).isNull()
  }

  @Test
  fun aReleaseDescriptorNeverExpires() {
    assertThat(appInfoExpirationProblem(appInfoXml = appInfoXml(eap = false, date = "202001010000"), now = now)).isNull()
  }

  @Test
  fun anAssembledDistributionIsJudgedByTheDescriptorItShips(@TempDir tempDir: Path) {
    val home = distributionWith(
      tempDir,
      // The platform's own fixtures live in `lib/` beside the product descriptor and keep their placeholders because no
      // product is built from them. Judging the distribution by one of those would answer for the wrong IDE.
      "intellij.platform.ide.impl.jar" to """<component><build number="__BUILD_NUMBER__" date="__BUILD_DATE__"/></component>""",
      "intellij.idea.ultimate.customization.jar" to appInfoXml(eap = true, date = "203801010000"),
    )

    assertThat(distributionExpirationProblem(distributionHome = home, now = now))
      .contains("in the future")
      .contains("intellij.idea.ultimate.customization.jar!/idea/ApplicationInfo.xml")
  }

  @Test
  fun aDistributionThatStampsNoBuildDateStarts(@TempDir tempDir: Path) {
    // What a dev distribution ships, and the reason the UI lanes cannot expire.
    val home = distributionWith(tempDir, "intellij.idea.ultimate.customization.jar" to appInfoXml(eap = true, date = "__BUILD_DATE__"))

    assertThat(distributionExpirationProblem(distributionHome = home, now = now)).isNull()
  }

  private fun distributionWith(tempDir: Path, vararg jars: Pair<String, String>): Path {
    val home = tempDir.resolve("dist")
    val lib = home.resolve("lib").createDirectories()
    for ((jarName, appInfoXml) in jars) {
      ZipOutputStream(lib.resolve(jarName).outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("idea/ApplicationInfo.xml"))
        zip.write(appInfoXml.encodeToByteArray())
        zip.closeEntry()
      }
    }
    return home
  }

  private fun utc(year: Int, month: Int, day: Int, hour: Int): Instant =
    LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC)

  private fun appInfoXml(eap: Boolean, date: String?): String = """
    <component xmlns="http://jetbrains.org/intellij/schema/application-info">
      <version major="2026" minor="3" eap="$eap"/>
      <build number="IU-263.SNAPSHOT"${date?.let { " date=\"$it\"" } ?: ""}/>
    </component>
  """.trimIndent()
}

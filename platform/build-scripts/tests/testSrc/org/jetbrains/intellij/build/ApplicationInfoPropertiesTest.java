// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoPropertiesTest {
  @Test
  public void majorReleaseDate() {
    assertEquals("20200401", ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("20200401"));
    assertEquals("20200401", ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("202004012323"));
  }

  @Test
  public void majorReleaseDateGenerated() {
    var now = System.currentTimeMillis() / 1000;
    var expectedDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), ZoneOffset.UTC)
      .format(ApplicationInfoPropertiesImplKt.getMAJOR_RELEASE_DATE_PATTERN());
    assertEquals(expectedDate, ApplicationInfoPropertiesImplKt.formatMajorReleaseDate(null, now));
    assertEquals(expectedDate, ApplicationInfoPropertiesImplKt.formatMajorReleaseDate("__BUILD_DATE__", now));
  }
}
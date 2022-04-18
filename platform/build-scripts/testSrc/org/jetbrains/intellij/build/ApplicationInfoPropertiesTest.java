// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoPropertiesTest {
  @Test
  public void majorReleaseDate() {
    assertEquals("20200401", ApplicationInfoPropertiesImpl.formatMajorReleaseDate("20200401"));
    assertEquals("20200401", ApplicationInfoPropertiesImpl.formatMajorReleaseDate("202004012323"));
  }

  @Test
  public void majorReleaseDateGenerated() {
    var now = System.currentTimeMillis() / 1000;
    var expectedDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), ZoneOffset.UTC)
      .format(ApplicationInfoPropertiesImpl.getMAJOR_RELEASE_DATE_PATTERN());
    assertEquals(expectedDate, ApplicationInfoPropertiesImpl.formatMajorReleaseDate(null, now));
    assertEquals(expectedDate, ApplicationInfoPropertiesImpl.formatMajorReleaseDate("__BUILD_DATE__", now));
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build;

import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoPropertiesTest {
  @Test
  public void majorReleaseDate() {
    assertEquals("20200401", ApplicationInfoProperties.formatMajorReleaseDate("20200401"));
    assertEquals("20200401", ApplicationInfoProperties.formatMajorReleaseDate("202004012323"));
  }

  @Test
  public void majorReleaseDateGenerated() {
    var now = ZonedDateTime.now(ZoneOffset.UTC).format(ApplicationInfoProperties.getMAJOR_RELEASE_DATE_PATTERN());
    assertEquals(now, ApplicationInfoProperties.formatMajorReleaseDate(null));
    assertEquals(now, ApplicationInfoProperties.formatMajorReleaseDate("__BUILD_DATE__"));
  }
}
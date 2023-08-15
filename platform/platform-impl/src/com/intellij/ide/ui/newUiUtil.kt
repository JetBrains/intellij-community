// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.util.PropertiesComponent
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

private const val FIRST_PROMOTION_DATE_PROPERTY = "experimental.ui.first.promotion.localdate";

internal fun getNewUiPromotionDaysCount(): Long {
  val propertyComponent = PropertiesComponent.getInstance();
  val value = propertyComponent.getValue(FIRST_PROMOTION_DATE_PROPERTY);
  val now = LocalDate.now();

  if (value == null) {
    propertyComponent.setValue(FIRST_PROMOTION_DATE_PROPERTY, now.toString());
    return 0
  }

  try {
    val firstDate = LocalDate.parse(value);
    return ChronoUnit.DAYS.between(firstDate, now);
  }
  catch (e: DateTimeParseException) {
    //LOG.warn("Invalid stored date $value");
    propertyComponent.setValue(FIRST_PROMOTION_DATE_PROPERTY, now.toString());
    return 0;
  }
}
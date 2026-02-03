// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.findIconUsingNewImplementation
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.swing.Icon

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

@ApiStatus.Internal
object NotPatchedIconRegistry {
  private val paths = HashSet<Pair<String, ClassLoader?>>()

  fun getData(): List<IconModel> {
    val result = ArrayList<IconModel>(paths.size)
    for ((path, second) in paths) {
      val classLoader = second ?: NotPatchedIconRegistry::class.java.getClassLoader()
      val icon = findIconUsingNewImplementation(path = path, classLoader = classLoader!!, toolTip = null)
      result.add(IconModel(icon, path))
    }
    return result
  }

  fun registerNotPatchedIcon(path: String, classLoader: ClassLoader?) {
    paths.add(Pair(path, classLoader))
  }

  class IconModel(@JvmField var icon: Icon?, @JvmField var originalPath: String) {
    override fun toString(): String = originalPath
  }
}
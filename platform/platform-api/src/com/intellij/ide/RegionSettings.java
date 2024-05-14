// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Set;

public final class RegionSettings {
  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
  /**
   * A Preferences key to access ISO 3166-1 2-digit country code, see also "user.country" system property
   */
  private static final String REGION_CODE_KEY = "JetBrains.regional.country";

  private RegionSettings() {
  }

  /**
   * @return Configured ISO 3166-1 2-digit country code. If not specified, the country code from default locale is returned, see {@link Locale#getCountry()}
   */
  @NotNull
  public static String getCountry() {
    return getCountry(Locale.getDefault().getCountry());
  }

  /**
   * @param def a value to be returned if country is not explicitly configured.
   * @return Configured ISO 3166-1 2-digit country code. If not specified, the specified default value is returned
   */
  @Contract("!null -> !null")
  public static String getCountry(String def) {
    return Prefs.get(REGION_CODE_KEY, def);
  }

  /**
   * @param value a 2-letter ISO country code
   * @return true if the code is successfully set, otherwise false. False value would mean incorrect country value format or an unknown country code
   */
  public static boolean setCountry(@NotNull String value) {
    value = value.toUpperCase(Locale.ENGLISH);
    if (COUNTRIES.contains(value)) {
      Prefs.put(REGION_CODE_KEY, value);
      return true;
    }
    return false;
  }

  /**
   * Clear region country setting
   */
  public static void resetCountry() {
    Prefs.remove(REGION_CODE_KEY);
  }

}

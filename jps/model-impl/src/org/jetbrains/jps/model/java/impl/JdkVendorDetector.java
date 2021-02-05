// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java.impl;


import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;


public final class JdkVendorDetector {

  // @formatter:off
  static final Vendor ORACLE   = new Vendor("oracle",     null,                        false);
  static final Vendor CORRETTO = new Vendor("corretto",   "Amazon Corretto",           true);
  static final Vendor LIBERICA = new Vendor("liberica",   "BellSoft Liberica JDK",     true);
  static final Vendor SAP      = new Vendor("sapmachine", "SAP SapMachine",            true);
  static final Vendor AZUL     = new Vendor("azul",       "Azul Zulu Community\u2122", true);
  static final Vendor JBR      = new Vendor("jbr",        "JetBrains Runtime",         true);
  // @formatter:on

  private static final VendorCase[] VENDOR_CASES = {
    // @formatter:off
    new VendorCase(CORRETTO, new PropertyPattern("IMPLEMENTOR", "^Amazon([\\s.].*)?$")),
    new VendorCase(CORRETTO, new PropertyPattern("IMPLEMENTOR_VERSION", "^Corretto-.*$")),
    new VendorCase(LIBERICA, new PropertyPattern("IMPLEMENTOR", "^.*BellSoft.*$")),
    new VendorCase(SAP,      new PropertyPattern("IMPLEMENTOR", "^SAP(\\s.*)?$")),
    new VendorCase(SAP,      new PropertyPattern("IMPLEMENTOR_VERSION", "^SapMachine.*$")),
    new VendorCase(AZUL,     new PropertyPattern("IMPLEMENTOR", "^Azul(\\s.*)?$")),
    new VendorCase(JBR,      new PropertyPattern("IMPLEMENTOR", "^.*JetBrains.*$")),
    new VendorCase(ORACLE,   new PropertyPattern("SOURCE", "^.*\\shotspot:[0-9A-Fa-f]+.*$")
                         ,   new PropertyPattern("JAVA_VERSION", "^1\\.7\\..*$")),
    new VendorCase(ORACLE,   new PropertyPattern("SOURCE", "^.*\\shotspot:[0-9A-Fa-f]+.*$")
                         ,   new PropertyPattern("BUILD_TYPE", "^.*commercial.*$")),
    // @formatter:on
  };


  @Nullable
  public static Vendor detectJdkVendorByReleaseFile(@NotNull Properties properties) {
    Map<String, String> ps = normalizeProperties(properties);
    for (VendorCase vc : VENDOR_CASES) if (checkMatching(ps, vc.patterns)) return vc.vendor;
    return null;
  }


  private static boolean checkMatching(@NotNull Map<String, String> properties, @NotNull Collection<PropertyPattern> patterns) {
    for (PropertyPattern pp : patterns) {
      String propertyValue = properties.get(pp.name);
      if (propertyValue == null) return false;
      if (!pp.pattern.matcher(propertyValue).matches()) return false;
    }
    return true;
  }


  @NotNull
  private static Map<String,String> normalizeProperties(@NotNull Properties properties) {
    final TreeMap<String,String> ps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (Map.Entry<Object, Object> e : properties.entrySet()) {
      Object key = e.getKey();
      if (key == null) continue;
      Object val = e.getValue();
      if (val == null) continue;
      String name = key.toString();
      String value = StringUtil.unquoteString(val.toString());
      ps.put(name, value);
    }
    return ps;
  }


  static final class Vendor {

    public final @NotNull String code;
    public final @Nullable String displayName;
    public final boolean includeCodeInPrefix;

    Vendor(@NotNull String code, @Nullable String displayName, boolean includeCodeInPrefix) {
      this.code = code;
      this.displayName = displayName;
      this.includeCodeInPrefix = includeCodeInPrefix;
    }

    public @Nullable String getPrefix() {
      return includeCodeInPrefix ? code : null;
    }

    @Override
    public String toString() {
      String s = (includeCodeInPrefix ? '+' : '-') + code;
      if (displayName != null) s = s + ':' + displayName;
      return s;
    }
  }


  private static class VendorCase {
    final @NotNull JdkVendorDetector.Vendor vendor;
    final @NotNull Collection<@NotNull PropertyPattern> patterns;

    VendorCase(@NotNull Vendor vendor, @NotNull PropertyPattern pattern) {
      this.vendor = vendor;
      this.patterns = Collections.singleton(pattern);
    }

    VendorCase(@NotNull Vendor vendor, @NotNull PropertyPattern pattern1, @NotNull PropertyPattern pattern2) {
      this.vendor = vendor;
      this.patterns = Arrays.asList(pattern1, pattern2);
    }
  }

  private static class PropertyPattern {
    final @NotNull String name;
    final @NotNull Pattern pattern;

    private PropertyPattern(@NotNull String name, @NotNull @Language("RegExp") String pattern) {
      this.name = name;
      this.pattern = Pattern.compile(pattern);
    }
  }

}

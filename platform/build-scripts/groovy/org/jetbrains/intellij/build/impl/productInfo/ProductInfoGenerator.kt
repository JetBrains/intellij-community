// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo;

import com.fasterxml.jackson.jr.ob.JSON;
import groovy.lang.GString;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.ApplicationInfoProperties;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.intellij.build.impl.BuiltinModulesFileData;
import org.jetbrains.intellij.build.impl.ProductInfoLaunchData;
import org.jetbrains.intellij.build.impl.SkipTransientPropertiesJrExtension;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Generates product-info.json file containing meta-information about product installation.
 */
public final class ProductInfoGenerator {
  public ProductInfoGenerator(BuildContext context) {
    this.context = context;
  }

  public Byte[] generateMultiPlatformProductJson(@NotNull String relativePathToBin,
                                                 @Nullable BuiltinModulesFileData builtinModules,
                                                 @NotNull List<ProductInfoLaunchData> launch) {
    ApplicationInfoProperties appInfo = context.getApplicationInfo();
    LinkedHashMap<String, GString> map = new LinkedHashMap<String, GString>(12);
    map.put("name", getProperty("appInfo").productName);
    map.put("version", getProperty("appInfo").fullVersion);
    map.put("versionSuffix", getProperty("appInfo").versionSuffix);
    map.put("buildNumber", getProperty("context").buildNumber);
    map.put("productCode", getProperty("appInfo").productCode);
    map.put("dataDirectoryName", getProperty("context").systemSelector);
    map.put("svgIconPath", getProperty("appInfo").svgRelativePath == null
                           ? null
                           : DefaultGroovyMethods.invokeMethod(String.class, "valueOf", new Object[]{getProperty("relativePathToBin")}) +
                             "/" +
                             DefaultGroovyMethods.invokeMethod(String.class, "valueOf",
                                                               new Object[]{getProperty("context").productProperties.baseFileName}) +
                             ".svg");
    map.put("launch", getProperty("launch"));
    map.put("customProperties",
            getProperty("context").productProperties.invokeMethod("generateCustomPropertiesForProductInfo", new Object[0]));
    final Object modules = getProperty("builtinModules");
    map.put("bundledPlugins", (modules == null ? null : modules.bundledPlugins));
    final Object modules1 = getProperty("builtinModules");
    map.put("fileExtensions", (modules1 == null ? null : modules1.fileExtensions));
    final Object modules2 = getProperty("builtinModules");
    map.put("modules", (modules2 == null ? null : modules2.modules));
    ProductInfoData json = new ProductInfoData(map);
    return JSON.builder().enable(JSON.Feature.PRETTY_PRINT_OUTPUT).register(new SkipTransientPropertiesJrExtension()).build().asBytes(json);
  }

  public static final String FILE_NAME = "product-info.json";
  private final BuildContext context;
}

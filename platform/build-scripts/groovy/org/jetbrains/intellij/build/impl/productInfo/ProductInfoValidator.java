// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jr.ob.JSON;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import groovy.lang.Closure;
import groovy.lang.GString;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildContext;
import org.jetbrains.intellij.build.BuildMessages;
import org.jetbrains.intellij.build.OsFamily;
import org.jetbrains.intellij.build.impl.ArchiveUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Validates that paths specified in product-info.json file are correct
 */
public final class ProductInfoValidator {
  public ProductInfoValidator(BuildContext context) {
    this.context = context;
  }

  /**
   * Checks that product-info.json file located in {@code archivePath} archive in {@code pathInArchive} subdirectory is correct
   */
  public static void checkInArchive(BuildContext context, String archivePath, String pathInArchive) {
    checkInArchive(context, Path.of(archivePath), pathInArchive);
  }

  /**
   * Checks that product-info.json file located in {@code archivePath} archive in {@code pathInArchive} subdirectory is correct
   */
  public static void checkInArchive(BuildContext context, Path archiveFile, String pathInArchive) {
    String productJsonPath = joinPaths(pathInArchive, ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME);
    Byte[] entryData = ArchiveUtils.loadEntry(archiveFile, productJsonPath);
    if (entryData == null) {
      context.getMessages()
        .error("Failed to validate product-info.json: cannot find \'" + productJsonPath + "\' in " + String.valueOf(archiveFile));
    }

    validateProductJson(context, entryData, archiveFile, "", Collections.emptyList(),
                        List.of(new Pair<Path, String>(archiveFile, pathInArchive)));
  }

  /**
   * Checks that product-info.json file located in {@code directoryWithProductJson} directory is correct
   *
   * @param installationDirectories directories which will be included into product installation
   * @param installationArchives    archives which will be unpacked and included into product installation (the first part specified path to archive,
   *                                the second part specifies path inside archive)
   */
  public void validateInDirectory(Path directoryWithProductJson,
                                  String relativePathToProductJson,
                                  List<Path> installationDirectories,
                                  List<Pair<Path, String>> installationArchives) {
    Path productJsonFile = directoryWithProductJson.resolve(relativePathToProductJson + ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME);

    Byte[] content;
    try {
      content = Files.readAllBytes(productJsonFile);
    }
    catch (NoSuchFileException ignored) {
      context.getMessages().error("Failed to validate product-info.json: " + String.valueOf(productJsonFile) + " doesn\'t exist");
      return;
    }

    validateProductJson(context, content, productJsonFile, relativePathToProductJson, installationDirectories, installationArchives);
  }

  public void validateInDirectory(Byte[] productJson,
                                  String relativePathToProductJson,
                                  List<Path> installationDirectories,
                                  List<Pair<Path, String>> installationArchives) {
    validateProductJson(context, productJson, null, relativePathToProductJson, installationDirectories, installationArchives);
  }

  private static void validateProductJson(BuildContext context,
                                          Byte[] jsonText,
                                          @Nullable Path productJsonFile,
                                          String relativePathToProductJson,
                                          List<Path> installationDirectories,
                                          List<Pair<Path, String>> installationArchives) {

    Path schemaPath = context.getPaths().getCommunityHomeDir()
      .resolve("platform/build-scripts/groovy/org/jetbrains/intellij/build/product-info.schema.json");
    verifyJsonBySchema(jsonText, schemaPath, context.getMessages());

    ProductInfoData productJson;
    try {
      productJson = JSON.std.beanFrom(ProductInfoData.class, jsonText);
    }
    catch (Exception e) {
      if (productJsonFile == null) {
        context.getMessages().error("Failed to parse product-info.json: " + e.getMessage(), e);
      }
      else {
        context.getMessages().error("Failed to parse product-info.json at " + String.valueOf(productJsonFile) + ": " + e.getMessage(), e);
      }

      return;
    }


    checkFileExists(context, productJson.getSvgIconPath(), "svg icon", relativePathToProductJson, installationDirectories,
                    installationArchives);

    for (ProductInfoLaunchData launch : productJson.getLaunch()) {
      if (DefaultGroovyMethods.find(OsFamily.ALL, new Closure<Boolean>(null, null) {
        public Boolean doCall(OsFamily it) { return (it.osName.equals(launch.getOs())); }

        public Boolean doCall() {
          return doCall(null);
        }
      }) == null) {
        context.getMessages().error("Incorrect os name \'" + launch.getOs() + "\' in " + relativePathToProductJson + "/product-info.json");
      }


      checkFileExists(context, launch.getLauncherPath(), launch.getOs() + " launcher", relativePathToProductJson, installationDirectories,
                      installationArchives);
      checkFileExists(context, launch.getJavaExecutablePath(), launch.getOs() + " java executable", relativePathToProductJson,
                      installationDirectories, installationArchives);
      checkFileExists(context, launch.getVmOptionsFilePath(), launch.getOs() + " VM options file", relativePathToProductJson,
                      installationDirectories, installationArchives);
    }
  }

  private static void verifyJsonBySchema(@NotNull Byte[] jsonData, @NotNull Path jsonSchemaFile, BuildMessages messages) {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    JsonSchema schema = factory.getSchema(Files.readString(jsonSchemaFile));

    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(jsonData);

    Set<ValidationMessage> errors = schema.validate(node);
    if (!errors.isEmpty()) {
      messages.error("Unable to validate JSON agains " +
                     String.valueOf(jsonSchemaFile) +
                     ":\n".plus(DefaultGroovyMethods.join(errors, "\n")).plus("\njson file content:\n")
                       .plus(new String(jsonData, StandardCharsets.UTF_8)));
    }
  }

  private static void checkFileExists(BuildContext context,
                                      String path,
                                      String description,
                                      String relativePathToProductJson,
                                      List<Path> installationDirectories,
                                      final List<Pair<Path, String>> installationArchives) {
    if (path == null) {
      return;
    }


    final String pathFromProductJson = relativePathToProductJson + path;
    if (!DefaultGroovyMethods.any(installationDirectories, new Closure<Boolean>(null, null) {
      public Boolean doCall(Path it) { return Files.exists(it.resolve(pathFromProductJson)); }

      public Boolean doCall() {
        return doCall(null);
      }
    }) && !DefaultGroovyMethods.any(installationArchives, new Closure<Boolean>(null, null) {
      public Boolean doCall(Pair<Path, String> it) {
        return ArchiveUtils.archiveContainsEntry(it.getFirst(), joinPaths(it.getSecond(), pathFromProductJson));
      }

      public Boolean doCall() {
        return doCall(null);
      }
    })) {
      context.getMessages().error("Incorrect path to " +
                                  description +
                                  " \'" +
                                  path +
                                  "\' in " +
                                  relativePathToProductJson +
                                  "/product-info.json: the specified file doesn\'t exist in directories " +
                                  String.valueOf(installationDirectories) +
                                  " ".plus("and archives " +
                                           String.valueOf(
                                             DefaultGroovyMethods.collect(installationArchives, new Closure<GString>(this, this) {
                                               public GString doCall(Pair<Path, String> it) {
                                                 return String.valueOf(it.getFirst()) +
                                                        "/" +
                                                        it.getSecond();
                                               }

                                               public GString doCall() {
                                                 return doCall(null);
                                               }
                                             }))));
    }
  }

  private static String joinPaths(String parent, String child) {
    return StringGroovyMethods.dropWhile(
      FileUtil.toCanonicalPath(parent + "/" + child, StringGroovyMethods.asType("/", (Class<Object>)Character.class)),
      new Closure<Boolean>(null, null) {
        public Boolean doCall(Object it) { return it.equals(StringGroovyMethods.asType("/", (Class<Object>)Character.class)); }

        public Boolean doCall() {
          return doCall(null);
        }
      });
  }

  private final BuildContext context;
}

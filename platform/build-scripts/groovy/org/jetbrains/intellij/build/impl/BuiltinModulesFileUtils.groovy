// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class BuiltinModulesFileUtils {
  private final static ObjectMapper objectMapper = new ObjectMapper()

  static BuiltinModulesFileData readBuiltinModulesFile(Path file) {
    JsonNode root = objectMapper.readTree(file.toFile())

    return new BuiltinModulesFileData(
      getStringArrayJsonValue(file, root, "plugins"),
      getStringArrayJsonValue(file, root, "modules"),
      getStringArrayJsonValue(file, root, "extensions"),
    )
  }

  static void customizeBuiltinModulesAllowOnlySpecified(
    @NotNull BuildContext context,
    @NotNull Path builtinModulesFile,
    @Nullable List<String> moduleNames,
    @Nullable List<String> pluginNames,
    @Nullable List<String> fileExtensions
  ) {
    context.messages.info("File $builtinModulesFile before modification:\n" + Files.readString(builtinModulesFile))

    JsonNode root = objectMapper.readTree(builtinModulesFile.toFile())

    if (moduleNames != null) {
      setArrayNodeElementsInBuiltinModules(context, builtinModulesFile, root, "modules", moduleNames)
    }

    if (pluginNames != null) {
      setArrayNodeElementsInBuiltinModules(context, builtinModulesFile, root, "plugins", pluginNames)
    }

    if (fileExtensions != null) {
      setArrayNodeElementsInBuiltinModules(context, builtinModulesFile, root, "extensions", fileExtensions)
    }

    Files.write(builtinModulesFile, objectMapper.writeValueAsBytes(root))

    context.messages.info("File $builtinModulesFile AFTER modification:\n" + Files.readString(builtinModulesFile))
  }

  private static List<String> getStringArrayJsonValue(@NotNull Path file, @NotNull JsonNode root, @NotNull String sectionName) {
    ArrayNode node = root.get(sectionName) as ArrayNode
    if (node == null) {
      throw new IllegalStateException("'$sectionName' was not found in $file:\n" + Files.readString(file))
    }

    List<String> values = node.toList().collect { it.asText() }
    return values
  }

  private static void setArrayNodeElementsInBuiltinModules(@NotNull BuildContext context,
                                                           @NotNull Path file,
                                                           @NotNull JsonNode root,
                                                           @NotNull String sectionName,
                                                           @NotNull List<String> valuesList) {
    ArrayNode node = root.get(sectionName) as ArrayNode
    if (node == null) {
      context.messages.error("'$sectionName' was not found in $file:\n" + Files.readString(file))
    }

    List<String> existingValues = node.toList().collect { it.asText() }
    node.removeAll()
    for (String value : valuesList) {
      if (!existingValues.contains(value)) {
        context.messages.error("Value '$value' in '$sectionName' was not found across existing values in $file:\n" +
                               Files.readString(file))
      }
      node.add(value)
    }
  }
}

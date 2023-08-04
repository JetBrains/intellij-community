package org.jetbrains.intellij.build.images.sync.dotnet

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import kotlin.io.path.*

object RiderIconsJsonGenerator {

  const val GENERATED_FILE_NAME = "RiderIconMappings.json"
  fun generate(iconsPath: Path) {
    val iconsResourcesPath = iconsPath.resolve("resources")
    val iconMappingsJsonPath = iconsResourcesPath.resolve(GENERATED_FILE_NAME)

    step("Generating $iconMappingsJsonPath..")

    val resourceFiles = iconsResourcesPath.toFile().walkTopDown().toList()
    val expuiFolders = resourceFiles.filter { it.isDirectory && it.name.lowercase() == "expui" }

    data class ExpuiItem(val expuiFile: Path, val originFile: Path)

    val postfixSymbols = listOf('@', '_')
    val expuiItems = expuiFolders.flatMap { folder ->
      folder.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() == "svg" }
        .groupBy { g -> "${g.parentFile.name}/${g.nameWithoutExtension.takeWhile { c -> !postfixSymbols.contains(c) }}" }
        .map { g -> // We want to know if there is file with postfix which does not have main file
          g.value.singleOrNull { "${it.parentFile.name}/${it.nameWithoutExtension}" == g.key }
            ?.let { ExpuiItem(it.toPath(), folder.parentFile.resolve(it.relativeTo(folder)).toPath()) }
          ?: error("Can't find file without postfix with name ${g.key} among files: [${g.value.joinToString(", ") { it.path }}]")
        }
    }

    // Assertion part
    for (item in expuiItems) {
      require(item.expuiFile.name == item.originFile.name) { "Unexpected items (names are not equal):\n" +
                                                             "expui: ${item.expuiFile}\n" +
                                                             "origin: ${item.originFile}"}
      require(item.expuiFile.exists()) { "Can't find file ${item.expuiFile}" }
      require(item.originFile.parent.let { it.exists() && it.isDirectory() }) { "Parent directory of origin file '${item.originFile}' does not exists" }
      require(!item.originFile.toString().contains("expui")) { "Unexpected file with expui part: ${item.originFile}" }
    }

    // Create json structure
    val gson = GsonBuilder().setPrettyPrinting().create()
    val jsonObj = JsonObject()

    val jsonLeafs = expuiItems.groupBy { it.expuiFile.parent }
      .map { g ->
        val obj = JsonObject()
        g.value
          .sortedBy { it.originFile.toString().lowercase() }
          .forEach { obj.addProperty(it.originFile.name, FileUtil.toSystemIndependentName(it.originFile.relativeTo (iconsResourcesPath).toString())) }

        g.key.relativeTo(iconsResourcesPath) to obj
      }
      .sortedBy { it.first }

    val tree = hashMapOf<Path, JsonObject>()

    for (elem in jsonLeafs) {
      tree[elem.first] = elem.second

      var rootPointer: Path = elem.first
      var objToAdd: JsonObject = elem.second
      while (rootPointer.parent != null) {
        val elemInTree = tree.getOrDefault(rootPointer.parent, null)
        if (elemInTree != null) {
          elemInTree.add(rootPointer.name, objToAdd)
          break
        }

        val newObjToAdd = JsonObject()
        newObjToAdd.add(rootPointer.name, objToAdd)
        objToAdd = newObjToAdd
        rootPointer = rootPointer.parent

        tree[rootPointer] = objToAdd
      }
    }

    tree.toList()
      .filter { it.first.parent == null }
      .forEach {
        jsonObj.add(it.first.name, it.second)
      }

    val jsonText = gson.toJson(jsonObj)
    iconMappingsJsonPath.writeText(jsonText, Charsets.UTF_8)

    println("Done")
  }

  private fun step(msg: String) = println("\n** $msg")
}
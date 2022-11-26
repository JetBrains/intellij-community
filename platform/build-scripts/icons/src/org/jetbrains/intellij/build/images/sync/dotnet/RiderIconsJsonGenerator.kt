package org.jetbrains.intellij.build.images.sync.dotnet

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

object RiderIconsJsonGenerator {

  const val GENERATED_FILE_NAME = "RiderIconMappings.json"
  fun generate(iconsPath: Path) {
    val iconsResourcesPath = iconsPath.resolve("resources")
    val iconMappingsJsonPath = iconsResourcesPath.resolve(GENERATED_FILE_NAME)

    step("Generating $iconMappingsJsonPath..")

    val resourceFiles = iconsResourcesPath.toFile().walkTopDown().toList()
    val expuiFolders = resourceFiles.filter { it.isDirectory && it.name.lowercase() == "expui" }

    data class ExpuiItem(val expuiFile: Path, val originFile: Path)

    val expuiItems = expuiFolders.flatMap { folder ->
      folder.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() == "svg" }
        .groupBy { g -> g.name.takeWhile { c -> c.isLetter() } }
        .map { g ->
          g.value.first { it.nameWithoutExtension == g.key }
            .let { ExpuiItem(it.toPath(), folder.parentFile.resolve(it.relativeTo(folder)).toPath()) }
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
          .sortedBy { it.originFile }
          .forEach { obj.addProperty(it.originFile.name, it.originFile.relativeTo(iconsResourcesPath).toString()) }

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
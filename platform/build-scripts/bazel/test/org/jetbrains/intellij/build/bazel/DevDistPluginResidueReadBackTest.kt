// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read-back of the residue attributes of a generated `dev_dist_plugin` call.
 *
 * Two halves, and the round trip that joins them. `residueOptions` writes the attributes and
 * [parseDevDistPluginResidue] answers a [ContentResidueSection], so `a plugin's whole residue survives the round trip`
 * is the case that states the property the whole move rests on.
 *
 * Every refusal has a case of its own, because the reader's value is that it stops rather than answers a default. A
 * reader that lost a field would write the loss out, and a second run would then agree with itself.
 */
internal class DevDistPluginResidueReadBackTest {
  @Test
  fun `a section with no call states nothing`() {
    assertNull(parseDevDistPluginResidue(section = "", plugin = "intellij.example"))
    assertNull(parseDevDistPluginResidue(section = "\n# nothing here\n", plugin = "intellij.example"))
  }

  @Test
  fun `a call with no residue attribute states nothing`() {
    assertNull(
      parseDevDistPluginResidue(
        section = call("""    content_modules = ["//a"],""", """    descriptor_module = ":plugin","""),
        plugin = "intellij.example",
      )
    )
  }

  @Test
  fun `the load statement and a jar target are not the call`() {
    val section = "\n" +
                  "load(\"@community//platform/build-scripts/bazel-rules:dev_dist_plugin.bzl\", \"dev_dist_plugin\")\n" +
                  "\n" +
                  "dev_dist_plugin_jar(\n" +
                  "    modules = [\"//a\"],\n" +
                  "    relative_output_file = \"a.jar\",\n" +
                  ")\n"

    assertNull(parseDevDistPluginResidue(section = section, plugin = "intellij.example"))
  }

  @Test
  fun `each name list field stands alone`() {
    assertEquals(
      listOf("intellij.at.lib.root"),
      parse("""    $RESIDUE_LIB_ROOT_JARS = ["intellij.at.lib.root"],""").libRootJars,
    )
    assertEquals(
      listOf("intellij.raw"),
      parse("""    $RESIDUE_RAW_MEMBERS = ["intellij.raw"],""").rawMembers,
    )
    assertEquals(
      listOf("intellij.vetoed"),
      parse("""    $RESIDUE_VETOED_MEMBERS = ["intellij.vetoed"],""").vetoedMembers,
    )
    assertEquals(
      listOf("intellij.separate"),
      parse("""    $RESIDUE_SEPARATE_JARS = ["intellij.separate"],""").separateJars,
    )
  }

  @Test
  fun `each name list dict field stands alone`() {
    assertEquals(
      mapOf("intellij.member" to listOf("member.jar")),
      parse("""    $RESIDUE_MEMBER_JARS = {"intellij.member": ["member.jar"]},""").memberJars,
    )
    assertEquals(
      mapOf("intellij.member" to listOf("kept")),
      parse("""    $RESIDUE_MERGED_LIBRARIES = {"intellij.member": ["kept"]},""").mergedLibraries,
    )
  }

  @Test
  fun `a jar that merges nothing states an empty list`() {
    assertEquals(
      mapOf("intellij.member" to emptyList<String>()),
      parse("""    $RESIDUE_MERGED_LIBRARIES = {"intellij.member": []},""").mergedLibraries,
    )
  }

  @Test
  fun `a library row states a module or does not`() {
    val section = parse(
      """    $RESIDUE_MODULE_LIBRARIES = {"intellij.owner": ["owned"]},""",
      """    $RESIDUE_PROJECT_LIBRARIES = ["Eclipse"],""",
    )

    // The order the two attributes restate: a project row has no module, so it stands before every module row.
    assertEquals(
      listOf(ResidueLibraryRow(name = "Eclipse"), ResidueLibraryRow(module = "intellij.owner", name = "owned")),
      section.libraries,
    )
  }

  @Test
  fun `both list forms of one field read the same`() {
    val inline = parse("""    $RESIDUE_VETOED_MEMBERS = ["one"],""")
    val perLine = parse(
      "    $RESIDUE_VETOED_MEMBERS = [",
      """        "one",""",
      "    ],",
    )

    assertEquals(inline, perLine)
    assertEquals(listOf("one"), inline.vetoedMembers)
  }

  @Test
  fun `both dict forms of one field read the same`() {
    val inline = parse("""    $RESIDUE_MEMBER_JARS = {"intellij.member": ["a.jar", "b.jar"]},""")
    val perLine = parse(
      "    $RESIDUE_MEMBER_JARS = {",
      """        "intellij.member": [""",
      """            "a.jar",""",
      """            "b.jar",""",
      "        ],",
      "    },",
    )

    assertEquals(inline, perLine)
    assertEquals(mapOf("intellij.member" to listOf("a.jar", "b.jar")), inline.memberJars)
  }

  @Test
  fun `a comment inside a list is not an item`() {
    assertEquals(
      listOf("one", "two"),
      parse(
        "    $RESIDUE_VETOED_MEMBERS = [",
        "        # do not sort,",
        """        "one",""",
        """        "two",""",
        "    ],",
      ).vetoedMembers,
    )
  }

  @Test
  fun `an attribute this file does not own is stepped over`() {
    val section = parse(
      """    descriptors = {"a": "b"},""",
      """    embed_content_modules = False,""",
      """    markers = ["one", "two"],""",
      """    $RESIDUE_VETOED_MEMBERS = ["intellij.vetoed"],""",
      """    version_suffix = "x",""",
    )

    assertEquals(listOf("intellij.vetoed"), section.vetoedMembers)
  }

  @Test
  fun `two calls in one section are refused`() {
    val section = call("""    $RESIDUE_VETOED_MEMBERS = ["one"],""") + call("""    $RESIDUE_VETOED_MEMBERS = ["two"],""")

    refuses(section, "two `dev_dist_plugin` calls")
  }

  @Test
  fun `an unterminated call is refused`() {
    refuses("\ndev_dist_plugin(\n    $RESIDUE_VETOED_MEMBERS = [\"one\",\n", "ends inside a list")
  }

  @Test
  fun `an unterminated string is refused`() {
    refuses("\ndev_dist_plugin(\n    $RESIDUE_VETOED_MEMBERS = [\"one\n", "line break inside a string")
  }

  @Test
  fun `a positional argument is refused`() {
    refuses("\ndev_dist_plugin(\":plugin\")\n", "expects an attribute name")
  }

  @Test
  fun `a duplicate attribute is refused`() {
    refuses(
      call("""    $RESIDUE_VETOED_MEMBERS = ["one"],""", """    $RESIDUE_VETOED_MEMBERS = ["two"],"""),
      "states `$RESIDUE_VETOED_MEMBERS` twice",
    )
  }

  @Test
  fun `a name list that is not a list is refused`() {
    refuses(call("""    $RESIDUE_VETOED_MEMBERS = "one","""), "is not a list")
    refuses(call("""    $RESIDUE_VETOED_MEMBERS = select({"//a": ["one"]}),"""), "is not a list")
  }

  @Test
  fun `a name list holding something else is refused`() {
    refuses(call("""    $RESIDUE_VETOED_MEMBERS = [True],"""), "not a string")
    refuses(call("""    $RESIDUE_VETOED_MEMBERS = [["one"]],"""), "not a string")
  }

  @Test
  fun `an empty name list is refused`() {
    refuses(call("""    $RESIDUE_VETOED_MEMBERS = [],"""), "an empty field is left out")
  }

  @Test
  fun `a dict field that is not a dict is refused`() {
    refuses(call("""    $RESIDUE_MEMBER_JARS = ["intellij.member"],"""), "is not a dict")
  }

  @Test
  fun `an empty dict field is refused`() {
    refuses(call("""    $RESIDUE_MEMBER_JARS = {},"""), "an empty field is left out")
  }

  @Test
  fun `a dict value that is not a list is refused`() {
    refuses(call("""    $RESIDUE_MEMBER_JARS = {"intellij.member": "member.jar"},"""), "not a list")
  }

  @Test
  fun `a dict key that is not a string is refused`() {
    refuses(call("""    $RESIDUE_MEMBER_JARS = {True: ["member.jar"]},"""), "not a string")
  }

  @Test
  fun `a dict stating one key twice is refused`() {
    refuses(
      call("""    $RESIDUE_MEMBER_JARS = {"intellij.member": ["a.jar"], "intellij.member": ["b.jar"]},"""),
      "states one key twice",
    )
  }

  @Test
  fun `a plugin's whole residue survives the round trip`() {
    val residue = ContentResidueSection(
      libRootJars = listOf("intellij.at.lib.root"),
      rawMembers = listOf("intellij.raw.one", "intellij.raw.two"),
      vetoedMembers = listOf("intellij.vetoed"),
      separateJars = listOf("intellij.separate"),
      memberJars = mapOf("intellij.one.jar" to listOf("one.jar"), "intellij.two.jars" to listOf("a.jar", "b.jar")),
      mergedLibraries = mapOf("intellij.merges" to listOf("kept"), "intellij.merges.nothing" to emptyList()),
      libraries = listOf(
        ResidueLibraryRow(name = "Eclipse"),
        ResidueLibraryRow(name = "kotlin-metadata"),
        ResidueLibraryRow(module = "intellij.owner", name = "first"),
        ResidueLibraryRow(module = "intellij.owner", name = "second"),
        ResidueLibraryRow(module = "intellij.second.owner", name = "third"),
      ),
    )

    val rendered = render(residue)

    assertEquals(residue, parseDevDistPluginResidue(section = rendered, plugin = PLUGIN))
    // The same shape a run writes, so the case also states that the writer touches nothing else.
    verifyDevDistPluginResidueReadBack(plugin = PLUGIN, residue = residue, call = rendered)
  }

  @Test
  fun `an empty residue writes no attribute`() {
    assertNull(parseDevDistPluginResidue(section = render(EMPTY_CONTENT_RESIDUE), plugin = PLUGIN))
    verifyDevDistPluginResidueReadBack(plugin = PLUGIN, residue = EMPTY_CONTENT_RESIDUE, call = render(EMPTY_CONTENT_RESIDUE))
  }

  @Test
  fun `every field alone survives the round trip`() {
    for (residue in oneFieldEach()) {
      val rendered = render(residue)
      assertEquals(rendered, residue, parseDevDistPluginResidue(section = rendered, plugin = PLUGIN))
    }
  }

  @Test
  fun `a library list in another order is refused`() {
    val residue = ContentResidueSection(
      libraries = listOf(ResidueLibraryRow(module = "intellij.owner", name = "owned"), ResidueLibraryRow(name = "Eclipse")),
    )

    val failure = assertThrows(IllegalStateException::class.java) { render(residue) }

    assertTrue(failure.message, failure.message!!.contains("in another order than by (module, name)"))
  }

  @Test
  fun `a section of a file is found by the plugin's main module`() {
    val file = "load(\"a.bzl\", \"a\")\n" +
               "\n" +
               "### auto-generated section `dev intellij.other` start\n" +
               "dev_dist_plugin(\n    $RESIDUE_VETOED_MEMBERS = [\"other\"],\n)\n" +
               "### auto-generated section `dev intellij.other` end\n" +
               "\n" +
               "### auto-generated section `dev $PLUGIN` start\n" +
               "dev_dist_plugin(\n    $RESIDUE_VETOED_MEMBERS = [\"own\"],\n)\n" +
               "### auto-generated section `dev $PLUGIN` end\n"

    val section = devDistPluginSectionText(fileContent = file, mainModuleName = PLUGIN)

    assertEquals(listOf("own"), parseDevDistPluginResidue(section = section!!, plugin = PLUGIN)!!.vetoedMembers)
    assertNull(devDistPluginSectionText(fileContent = file, mainModuleName = "intellij.absent"))
  }

  /** One section per field, so that a lost field cannot hide behind another one that reads back. */
  private fun oneFieldEach(): List<ContentResidueSection> {
    return listOf(
      ContentResidueSection(libRootJars = listOf("intellij.at.lib.root")),
      ContentResidueSection(rawMembers = listOf("intellij.raw")),
      ContentResidueSection(vetoedMembers = listOf("intellij.vetoed")),
      ContentResidueSection(separateJars = listOf("intellij.separate")),
      ContentResidueSection(memberJars = mapOf("intellij.member" to listOf("member.jar"))),
      ContentResidueSection(mergedLibraries = mapOf("intellij.member" to listOf("kept"))),
      ContentResidueSection(libraries = listOf(ResidueLibraryRow(name = "Eclipse"))),
      ContentResidueSection(libraries = listOf(ResidueLibraryRow(module = "intellij.owner", name = "owned"))),
    )
  }

  /** [residue] through the writer a run uses, as the whole text of the plugin's call. */
  private fun render(residue: ContentResidueSection): String {
    val call = Target("dev_dist_plugin")
    call.option("descriptor_module", ":plugin")
    call.residueOptions(plugin = PLUGIN, residue = residue)
    return "\n" + call.render()
  }

  private fun parse(vararg attributes: String): ContentResidueSection {
    return parseDevDistPluginResidue(section = call(*attributes), plugin = PLUGIN)!!
  }

  private fun refuses(section: String, message: String) {
    val failure = assertThrows(IllegalStateException::class.java) {
      parseDevDistPluginResidue(section = section, plugin = PLUGIN)
    }

    assertTrue(failure.message, failure.message!!.contains(message))
    assertTrue(failure.message, failure.message!!.contains(PLUGIN))
  }

  private fun call(vararg attributes: String): String {
    return "\ndev_dist_plugin(\n" + attributes.joinToString(separator = "\n", postfix = "\n") + ")\n"
  }
}

private const val PLUGIN: String = "intellij.example.plugin"

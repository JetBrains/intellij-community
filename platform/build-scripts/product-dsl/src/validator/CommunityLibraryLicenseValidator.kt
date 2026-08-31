// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.model.error.MissingLibraryLicenseError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.xml.visitAllModules

/**
 * Community library license validation.
 *
 * Purpose: Make sure a library that a community product ships has an entry in the community license list.
 * Inputs: the `communityLibraryLicenses` config list, and the module sets of the COMMUNITY and CORE labels.
 * Output: `MissingLibraryLicenseError`.
 * Auto-fix: no.
 *
 * `LibraryLicenseValidator` reads `libraryLicenses`, which the ultimate generator fills with the ultimate list. That
 * list holds the community list, so it covers an entry that sits in the ultimate-only part. A community product reads
 * `CommunityLibraryLicenses.LICENSES_LIST` alone, through `ProductProperties.allLibraryLicenses`, so such an entry
 * leaves the community build with no license. `IdeaCommunityBuildTest` then fails in the `third_party_libraries` step.
 *
 * This rule closes that gap. `ultimateGenerator` maps the COMMUNITY and CORE labels to the community generated
 * META-INF directory, and the ULTIMATE label to the `licenseCommon` one. A library of a set with either of the first
 * two labels therefore needs a community entry.
 *
 * This rule applies no Maven descriptor filter, and no filter on a JetBrains own group id, which matches
 * `LibraryLicenseValidator`. A JetBrains own library still needs the entry in the right file.
 *
 * Glossary: docs/validators/README.md.
 * Spec: docs/validators/community-library-license.md.
 */
internal object CommunityLibraryLicenseValidator : PipelineNode {
  override val id get() = NodeIds.COMMUNITY_LIBRARY_LICENSE_VALIDATION

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val licenses = model.config.communityLibraryLicenses
    // an empty license list turns the rule off
    if (licenses.isEmpty()) {
      return
    }

    val moduleNames = collectModuleSetMembers(model.discovery.communityShippedModuleSets)
    val coveredNames = licenses.flatMapTo(HashSet()) { it.getLibraryNames() }
    val (checkedModuleCount, violations) = collectMissingLicenseViolations(
      moduleNames = moduleNames,
      outputProvider = model.outputProvider,
      coveredNames = coveredNames,
    )

    // a community or core module set always holds a module, and a silent pass would hide every misplaced entry
    check(checkedModuleCount != 0) {
      "No module of a community or core module set resolves to a JPS module, and the community license list has " +
      "${licenses.size} entries. A pass here would hide every misplaced license entry."
    }

    if (violations.isNotEmpty()) {
      ctx.emitError(MissingLibraryLicenseError(
        context = "the community and core module sets",
        violations = violations,
        licenseFile = "CommunityLibraryLicenses.kt",
        ruleName = "CommunityLibraryLicenseValidation",
      ))
    }
  }
}

private fun collectModuleSetMembers(moduleSets: List<ModuleSet>): Set<String> {
  val result = LinkedHashSet<String>()
  for (set in moduleSets) {
    visitAllModules(set) { module ->
      result.add(module.moduleId.name)
    }
  }
  return result
}

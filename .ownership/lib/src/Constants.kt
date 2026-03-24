package com.intellij.codeowners

internal object Constants {
  const val OWNERSHIP_DIRECTORY_NAME: String = ".ownership"

  const val OWNERSHIP_FILE_NAME: String = "OWNERSHIP"
  const val OWNERSHIP_SCAN_IGNORE: String = ".ownership.scan.ignore"
  const val OWNERSHIP_MAPPING_FILE_RELATIVE_PATH: String = "$OWNERSHIP_DIRECTORY_NAME/generated/ownership-mapping-generated.yaml"

  const val GROUPS_FILE_RELATIVE_PATH: String = "$OWNERSHIP_DIRECTORY_NAME/groups.yaml"
  val GROUP_NAME_VALIDATION_REGEX: Regex = "^[a-zA-Z0-9_\\- .&]+$".toRegex()

  const val REVIEW_RULES_FILE_RELATIVE_PATH: String = "$OWNERSHIP_DIRECTORY_NAME/review-rules.yaml"
  const val REVIEW_RULES_REQUIRE_OWNER_REVIEW_DEFAULT: Boolean = true

  const val GENERATED_FILES_HEADER: String = "Generated based on OWNERSHIP files, do not edit manually"
}
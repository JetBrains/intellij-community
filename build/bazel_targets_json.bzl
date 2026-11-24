"""The labels of the targets JSON of each half.

Data only: this file loads nothing. A package that needs only such a label loads no target list.
"""

# The label for a package of the community repository.
BAZEL_TARGETS_JSON_COMMUNITY = "@community//build:community_bazel_targets_json"

# The label for a package of the ultimate repository.
BAZEL_TARGETS_JSON_ULTIMATE = "//build:ultimate_bazel_targets_json"

def current_bazel_targets_json():
    repo = native.repository_name()
    module = native.module_name()

    # Standalone `community/bazel.cmd` runs with `repo == "@"` and `module == "community"`.
    if repo == "@" and module == "community":
        return BAZEL_TARGETS_JSON_COMMUNITY
    else:
        # means: "the target //build:ultimate_bazel_targets_json in the canonical main repository"
        return "@@//build:ultimate_bazel_targets_json"

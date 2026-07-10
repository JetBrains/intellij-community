def current_bazel_targets_json():
    repo = native.repository_name()
    module = native.module_name()

    # Standalone `community/bazel.cmd` runs with `repo == "@"` and `module == "community"`.
    if repo == "@" and module == "community":
        return "@community//build:community_bazel_targets_json"
    else:
        # means: "the target //build:ultimate_bazel_targets_json in the canonical main repository"
        return "@@//build:ultimate_bazel_targets_json"

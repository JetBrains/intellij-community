"""Unit tests for helper functions in jps_library_derivation.bzl.

Tests cover: maven_url_to_jar_target (Maven URL → Bazel label), local_path_to_jar_target
(local path → Bazel label), is_snapshot_version, and is_kotlin_dev_version_as_snapshot.

Note: derive_library_targets() requires a repository rule ctx for filesystem access
and is tested via integration (bazel build) rather than unit tests here.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(
    ":jps_library_derivation.bzl",
    "is_kotlin_dev_version_as_snapshot",
    "is_snapshot_version",
    "local_path_to_jar_target",
    "maven_url_to_jar_target",
)

def _maven_url(group_path, artifact, version, filename):
    """Build a $MAVEN_REPOSITORY$ jar URL for testing."""
    return "jar://$MAVEN_REPOSITORY$/%s/%s/%s/%s!/" % (group_path, artifact, version, filename)

# --- maven_url_to_jar_target tests ---

# Test 1: Regular Maven URL (is_snapshot=False) → @lib//:group/file.jar
def _regular_maven_url_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("org/tukaani", "xz", "1.10", "xz-1.10.jar")
    result = maven_url_to_jar_target(url, "@lib")
    asserts.equals(env, "@lib//:org.tukaani/xz-1.10.jar", result)
    return unittest.end(env)

regular_maven_url_test = unittest.make(_regular_maven_url_test_impl)

# Test 2: is_snapshot=True → @lib//snapshots:file.jar
def _snapshot_maven_url_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("org/example", "foo", "1.0-SNAPSHOT", "foo-1.0-SNAPSHOT.jar")
    result = maven_url_to_jar_target(url, "@lib", is_snapshot = True)
    asserts.equals(env, "@lib//snapshots:foo-1.0-SNAPSHOT.jar", result)
    return unittest.end(env)

snapshot_maven_url_test = unittest.make(_snapshot_maven_url_test_impl)

# Test 3: is_snapshot=True with non-SNAPSHOT filename → still routes to //snapshots:
def _snapshot_flag_non_snapshot_filename_test_impl(ctx):
    env = unittest.begin(ctx)
    # A non-SNAPSHOT filename, but the library was determined to be snapshot at the library level
    url = _maven_url("io/github/pdvrieze/xmlutil", "core-jvm", "0.86.2", "core-jvm-0.86.2.jar")
    result = maven_url_to_jar_target(url, "@lib", is_snapshot = True)
    asserts.equals(env, "@lib//snapshots:core-jvm-0.86.2.jar", result)
    return unittest.end(env)

snapshot_flag_non_snapshot_filename_test = unittest.make(_snapshot_flag_non_snapshot_filename_test_impl)

# Test 4: is_snapshot=False (default) → regular path even for dev version filenames
def _no_snapshot_flag_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar")
    result = maven_url_to_jar_target(url, "@lib")
    asserts.equals(env, "@lib//:org.jetbrains.kotlin/kotlin-stdlib-2.1.20-dev-1234.jar", result)
    return unittest.end(env)

no_snapshot_flag_test = unittest.make(_no_snapshot_flag_test_impl)

# Test 5: Deep group ID (multi-component, e.g. io.github.pdvrieze.xmlutil)
def _deep_group_id_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("io/github/pdvrieze/xmlutil", "core-jvm", "0.86.2", "core-jvm-0.86.2.jar")
    result = maven_url_to_jar_target(url, "@lib")
    asserts.equals(env, "@lib//:io.github.pdvrieze.xmlutil/core-jvm-0.86.2.jar", result)
    return unittest.end(env)

deep_group_id_test = unittest.make(_deep_group_id_test_impl)

# Test 6: @ultimate_lib repo routing
def _ultimate_lib_repo_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("com/example", "bar", "2.0", "bar-2.0.jar")
    result = maven_url_to_jar_target(url, "@ultimate_lib")
    asserts.equals(env, "@ultimate_lib//:com.example/bar-2.0.jar", result)
    return unittest.end(env)

ultimate_lib_repo_test = unittest.make(_ultimate_lib_repo_test_impl)

# Test 7: SNAPSHOT + @ultimate_lib → @ultimate_lib//snapshots:
def _snapshot_ultimate_lib_test_impl(ctx):
    env = unittest.begin(ctx)
    url = _maven_url("com/example", "bar", "3.0-SNAPSHOT", "bar-3.0-SNAPSHOT.jar")
    result = maven_url_to_jar_target(url, "@ultimate_lib", is_snapshot = True)
    asserts.equals(env, "@ultimate_lib//snapshots:bar-3.0-SNAPSHOT.jar", result)
    return unittest.end(env)

snapshot_ultimate_lib_test = unittest.make(_snapshot_ultimate_lib_test_impl)

# Test 8: Mixed-snapshot library — caller determines is_snapshot at the library level
# Tests the detection→routing chain: is_snapshot_version() detects ANY snapshot jar,
# then maven_url_to_jar_target() routes ALL jars to //snapshots: (mirrors dependency.kt:115-129)
def _mixed_snapshot_library_test_impl(ctx):
    env = unittest.begin(ctx)

    snapshot_url = _maven_url(
        "io/github/pdvrieze/xmlutil", "serialization-jvm",
        "0.90.0-SNAPSHOT", "serialization-jvm-0.90.0-SNAPSHOT.jar",
    )
    non_snapshot_url = _maven_url(
        "io/github/pdvrieze/xmlutil", "core-jvm",
        "0.86.2", "core-jvm-0.86.2.jar",
    )
    urls = [snapshot_url, non_snapshot_url]

    # Step 1: is_snapshot_version detects the SNAPSHOT jar in the mixed list
    is_snapshot = is_snapshot_version(urls)
    asserts.true(env, is_snapshot, "is_snapshot_version should detect -SNAPSHOT jar in mixed list")

    # Step 2: ALL jars route to //snapshots: when is_snapshot=True (library-level decision)
    snapshot_result = maven_url_to_jar_target(snapshot_url, "@ultimate_lib", is_snapshot = is_snapshot)
    asserts.equals(env, "@ultimate_lib//snapshots:serialization-jvm-0.90.0-SNAPSHOT.jar", snapshot_result)

    non_snapshot_result = maven_url_to_jar_target(non_snapshot_url, "@ultimate_lib", is_snapshot = is_snapshot)
    asserts.equals(
        env,
        "@ultimate_lib//snapshots:core-jvm-0.86.2.jar",
        non_snapshot_result,
        "non-SNAPSHOT jar in mixed library must also route to //snapshots:",
    )

    return unittest.end(env)

mixed_snapshot_library_test = unittest.make(_mixed_snapshot_library_test_impl)

# --- local_path_to_jar_target tests ---

# Test: community mode, path under lib/ → @lib//<parent>:<file>
def _local_path_community_lib_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("lib/ant/lib/ant.jar", True, "")
    asserts.equals(env, "@lib//ant/lib:ant.jar", result)
    return unittest.end(env)

local_path_community_lib_test = unittest.make(_local_path_community_lib_test_impl)

# Test: community mode, path NOT under lib/ → //<parent>:<file>
def _local_path_community_nonlib_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("plugins/foo/bar.jar", True, "")
    asserts.equals(env, "//plugins/foo:bar.jar", result)
    return unittest.end(env)

local_path_community_nonlib_test = unittest.make(_local_path_community_nonlib_test_impl)

# Test: ultimate mode, path under community/lib/ → @lib//<parent>:<file>
def _local_path_ultimate_community_lib_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("community/lib/ant/lib/ant.jar", False, "community")
    asserts.equals(env, "@lib//ant/lib:ant.jar", result)
    return unittest.end(env)

local_path_ultimate_community_lib_test = unittest.make(_local_path_ultimate_community_lib_test_impl)

# Test: ultimate mode, path under lib/ (not community/lib/) → @ultimate_lib//<parent>:<file>
def _local_path_ultimate_lib_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("lib/ultimate-stuff/foo.jar", False, "community")
    asserts.equals(env, "@ultimate_lib//ultimate-stuff:foo.jar", result)
    return unittest.end(env)

local_path_ultimate_lib_test = unittest.make(_local_path_ultimate_lib_test_impl)

# Test: ultimate mode, path under community/ but not community/lib/ → @community//<parent>:<file>
def _local_path_ultimate_community_nonlib_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("community/plugins/foo/bar.jar", False, "community")
    asserts.equals(env, "@community//plugins/foo:bar.jar", result)
    return unittest.end(env)

local_path_ultimate_community_nonlib_test = unittest.make(_local_path_ultimate_community_nonlib_test_impl)

# Test: ultimate mode, path not under lib/ or community/ → //<parent>:<file>
def _local_path_ultimate_project_root_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("some/path/thing.jar", False, "community")
    asserts.equals(env, "//some/path:thing.jar", result)
    return unittest.end(env)

local_path_ultimate_project_root_test = unittest.make(_local_path_ultimate_project_root_test_impl)

# Test: _path_to_label edge case — file with no parent directory → //:<file>
def _local_path_no_parent_test_impl(ctx):
    env = unittest.begin(ctx)
    result = local_path_to_jar_target("foo.jar", True, "")
    asserts.equals(env, "//:foo.jar", result)
    return unittest.end(env)

local_path_no_parent_test = unittest.make(_local_path_no_parent_test_impl)

# --- is_snapshot_version tests ---

# Test 9: is_snapshot_version detects -SNAPSHOT.jar among mixed URLs
def _is_snapshot_version_true_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("com/example", "foo", "1.0", "foo-1.0.jar"),
        _maven_url("com/example", "bar", "2.0-SNAPSHOT", "bar-2.0-SNAPSHOT.jar"),
    ]
    asserts.true(env, is_snapshot_version(urls))
    return unittest.end(env)

is_snapshot_version_true_test = unittest.make(_is_snapshot_version_true_test_impl)

# Test 10: is_snapshot_version returns False when no snapshot jars
def _is_snapshot_version_false_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("com/example", "foo", "1.0", "foo-1.0.jar"),
        _maven_url("com/example", "bar", "2.0", "bar-2.0.jar"),
    ]
    asserts.false(env, is_snapshot_version(urls))
    return unittest.end(env)

is_snapshot_version_false_test = unittest.make(_is_snapshot_version_false_test_impl)

# --- is_kotlin_dev_version_as_snapshot tests ---

# Test 11: Kotlin dev version detected as snapshot when env var matches
def _kotlin_dev_version_snapshot_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar"),
    ]
    asserts.true(env, is_kotlin_dev_version_as_snapshot(urls, "2.1.20-dev-1234"))
    return unittest.end(env)

kotlin_dev_version_snapshot_test = unittest.make(_kotlin_dev_version_snapshot_test_impl)

# Test 12: Kotlin dev version NOT detected when env var is None
def _kotlin_dev_version_no_env_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar"),
    ]
    asserts.false(env, is_kotlin_dev_version_as_snapshot(urls, None))
    return unittest.end(env)

kotlin_dev_version_no_env_test = unittest.make(_kotlin_dev_version_no_env_test_impl)

# Test: Kotlin dev version NOT detected when env var suffix doesn't match
def _kotlin_dev_version_mismatch_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar"),
    ]
    asserts.false(env, is_kotlin_dev_version_as_snapshot(urls, "2.1.20-dev-9999"))
    return unittest.end(env)

kotlin_dev_version_mismatch_test = unittest.make(_kotlin_dev_version_mismatch_test_impl)

# Test: Non-Kotlin group with matching dev version suffix → True in Starlark
# Note: dependency.kt:126 additionally guards on org.jetbrains.kotlin path prefix,
# but jps_library_derivation.bzl L34-35 deliberately skips that check since the
# version string (e.g. 2.4.0-dev-6760) is specific enough.
def _kotlin_dev_version_non_kotlin_lib_test_impl(ctx):
    env = unittest.begin(ctx)
    urls = [
        _maven_url("com/example", "some-lib", "2.1.20-dev-1234", "some-lib-2.1.20-dev-1234.jar"),
    ]
    asserts.true(env, is_kotlin_dev_version_as_snapshot(urls, "2.1.20-dev-1234"))
    return unittest.end(env)

kotlin_dev_version_non_kotlin_lib_test = unittest.make(_kotlin_dev_version_non_kotlin_lib_test_impl)

def jps_library_derivation_test_suite(name):
    """Test suite for jps_library_derivation.bzl helper functions."""
    unittest.suite(
        name,
        # maven_url_to_jar_target tests
        regular_maven_url_test,
        snapshot_maven_url_test,
        snapshot_flag_non_snapshot_filename_test,
        no_snapshot_flag_test,
        deep_group_id_test,
        ultimate_lib_repo_test,
        snapshot_ultimate_lib_test,
        mixed_snapshot_library_test,
        # local_path_to_jar_target tests
        local_path_community_lib_test,
        local_path_community_nonlib_test,
        local_path_ultimate_community_lib_test,
        local_path_ultimate_lib_test,
        local_path_ultimate_community_nonlib_test,
        local_path_ultimate_project_root_test,
        local_path_no_parent_test,
        # is_snapshot_version tests
        is_snapshot_version_true_test,
        is_snapshot_version_false_test,
        # is_kotlin_dev_version_as_snapshot tests
        kotlin_dev_version_snapshot_test,
        kotlin_dev_version_no_env_test,
        kotlin_dev_version_mismatch_test,
        kotlin_dev_version_non_kotlin_lib_test,
    )

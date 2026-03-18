"""End-to-end pipeline tests for derive_library_targets().

Tests call derive_library_targets() directly with real XML parsing through
parse_iml() and _parse_library_element(), using minimal fakes for ctx and
project_root.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":jps_library_derivation.bzl", "derive_library_targets")
load(":jps_target_derivation.bzl", "parse_iml")

# --- Harness helpers ---

def _fake_ctx(env = {}):
    return struct(getenv = lambda name: env.get(name))

def _fake_path(rel, entries_by_dir):
    return struct(
        get_child = lambda child: _fake_path(
            (rel + "/" + child) if rel else child,
            entries_by_dir,
        ),
        readdir = lambda: entries_by_dir.get(rel, []),
    )

def _fake_root(entries_by_dir = {}):
    return _fake_path("", entries_by_dir)

# --- XML builders ---

def _maven_root(group_path, artifact, version, filename):
    return "jar://$MAVEN_REPOSITORY$/%s/%s/%s/%s!/" % (group_path, artifact, version, filename)

def _project_library_xml(name, maven_roots = [], local_roots = [], jar_directories = []):
    """Build a .idea/libraries/*.xml string."""
    roots = ""
    for url in maven_roots:
        roots += '      <root url="%s" />\n' % url
    for url in local_roots:
        roots += '      <root url="%s" />\n' % url
    for url in jar_directories:
        roots += '      <root url="%s" />\n' % url

    jar_dirs = ""
    for url in jar_directories:
        jar_dirs += '    <jarDirectory url="%s" recursive="false" />\n' % url

    return (
        '<component name="libraryTable">\n'
        + '  <library name="%s">\n' % name
        + "    <CLASSES>\n"
        + roots
        + "    </CLASSES>\n"
        + jar_dirs
        + "  </library>\n"
        + "</component>"
    )

def _library_xml_struct(name, xml_content):
    return struct(xml_content = xml_content, xml_rel_path = ".idea/libraries/" + name + ".xml")

def _iml_xml(project_libraries = [], module_library_roots = []):
    """Build a minimal .iml XML string.

    Args:
        project_libraries: list of project-level library names
        module_library_roots: list of lists of jar URLs (each inner list = one module-library)
    """
    entries = '    <content url="file://$MODULE_DIR$" />\n'
    for lib_name in project_libraries:
        entries += '    <orderEntry type="library" name="%s" level="project" />\n' % lib_name
    for jar_urls in module_library_roots:
        roots = ""
        for url in jar_urls:
            roots += '          <root url="%s" />\n' % url
        entries += (
            '    <orderEntry type="module-library">\n'
            + "      <library>\n"
            + "        <CLASSES>\n"
            + roots
            + "        </CLASSES>\n"
            + "      </library>\n"
            + "    </orderEntry>\n"
        )
    return (
        '<module type="JAVA_MODULE" version="4">\n'
        + '  <component name="NewModuleRootManager">\n'
        + entries
        + "  </component>\n"
        + "</module>"
    )

def _iml_data(module_name, iml_rel_path, iml_xml_str, is_community = True):
    iml_dir_rel = iml_rel_path.rsplit("/", 1)[0] if "/" in iml_rel_path else ""
    return struct(
        module_name = module_name,
        iml_dir_rel = iml_dir_rel,
        iml_rel_path = iml_rel_path,
        is_community = is_community,
        parsed_iml = parse_iml(iml_xml_str, iml_rel_path),
    )

def _derive(library_xmls = [], iml_data_list = [], env = {}, entries_by_dir = {},
            is_community_only = True, community_root_rel = ""):
    return derive_library_targets(
        ctx = _fake_ctx(env),
        project_root = _fake_root(entries_by_dir),
        library_xmls = library_xmls,
        iml_data_list = iml_data_list,
        is_community_only = is_community_only,
        community_root_rel = community_root_rel,
    )

# --- Test 1: project_libraries_only_include_referenced_test ---

def _project_libraries_only_include_referenced_test_impl(ctx):
    env = unittest.begin(ctx)

    used_xml = _project_library_xml("used-lib", maven_roots = [
        _maven_root("com/example", "used", "1.0", "used-1.0.jar"),
    ])
    unused_xml = _project_library_xml("unused-lib", maven_roots = [
        _maven_root("com/example", "unused", "2.0", "unused-2.0.jar"),
    ])

    iml = _iml_xml(project_libraries = ["used-lib"])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        library_xmls = [
            _library_xml_struct("used_lib", used_xml),
            _library_xml_struct("unused_lib", unused_xml),
        ],
        iml_data_list = [iml_data],
    )

    asserts.equals(env, ["@lib//:com.example/used-1.0.jar"], result)
    return unittest.end(env)

project_libraries_only_include_referenced_test = unittest.make(_project_libraries_only_include_referenced_test_impl)

# --- Test 2: project_library_repo_selection_test ---

def _project_library_repo_selection_test_impl(ctx):
    env = unittest.begin(ctx)

    shared_xml = _project_library_xml("shared-lib", maven_roots = [
        _maven_root("com/shared", "core", "1.0", "core-1.0.jar"),
    ])
    ultimate_xml = _project_library_xml("ultimate-lib", maven_roots = [
        _maven_root("com/ultimate", "ext", "2.0", "ext-2.0.jar"),
    ])

    community_iml = _iml_xml(project_libraries = ["shared-lib"])
    community_data = _iml_data(
        "intellij.community.mod",
        "community/plugins/foo/intellij.community.mod.iml",
        community_iml,
        is_community = True,
    )

    ultimate_iml = _iml_xml(project_libraries = ["ultimate-lib"])
    ultimate_data = _iml_data(
        "intellij.ultimate.mod",
        "plugins/bar/intellij.ultimate.mod.iml",
        ultimate_iml,
        is_community = False,
    )

    result = _derive(
        library_xmls = [
            _library_xml_struct("shared_lib", shared_xml),
            _library_xml_struct("ultimate_lib", ultimate_xml),
        ],
        iml_data_list = [community_data, ultimate_data],
        is_community_only = False,
        community_root_rel = "community",
    )

    asserts.equals(env, [
        "@lib//:com.shared/core-1.0.jar",
        "@ultimate_lib//:com.ultimate/ext-2.0.jar",
    ], result)
    return unittest.end(env)

project_library_repo_selection_test = unittest.make(_project_library_repo_selection_test_impl)

# --- Test 3: project_library_mixed_snapshot_test ---

def _project_library_mixed_snapshot_test_impl(ctx):
    env = unittest.begin(ctx)

    lib_xml = _project_library_xml("mixed-snap", maven_roots = [
        _maven_root("com/example", "foo", "1.0-SNAPSHOT", "foo-1.0-SNAPSHOT.jar"),
        _maven_root("com/example", "foo-util", "1.0", "foo-util-1.0.jar"),
    ])

    iml = _iml_xml(project_libraries = ["mixed-snap"])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        library_xmls = [_library_xml_struct("mixed_snap", lib_xml)],
        iml_data_list = [iml_data],
    )

    asserts.equals(env, [
        "@lib//snapshots:foo-1.0-SNAPSHOT.jar",
        "@lib//snapshots:foo-util-1.0.jar",
    ], result)
    return unittest.end(env)

project_library_mixed_snapshot_test = unittest.make(_project_library_mixed_snapshot_test_impl)

# --- Test 4: project_library_kotlin_dev_snapshot_env_test ---

def _project_library_kotlin_dev_snapshot_env_test_impl(ctx):
    env = unittest.begin(ctx)

    lib_xml = _project_library_xml("kotlin-stdlib", maven_roots = [
        _maven_root("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar"),
    ])

    iml = _iml_xml(project_libraries = ["kotlin-stdlib"])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        library_xmls = [_library_xml_struct("kotlin_stdlib", lib_xml)],
        iml_data_list = [iml_data],
        env = {"JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT": "2.1.20-dev-1234"},
    )

    asserts.equals(env, ["@lib//snapshots:kotlin-stdlib-2.1.20-dev-1234.jar"], result)
    return unittest.end(env)

project_library_kotlin_dev_snapshot_env_test = unittest.make(_project_library_kotlin_dev_snapshot_env_test_impl)

# --- Test 5: module_library_ignores_kotlin_dev_snapshot_env_test ---

def _module_library_ignores_kotlin_dev_snapshot_env_test_impl(ctx):
    env = unittest.begin(ctx)

    iml = _iml_xml(module_library_roots = [
        [_maven_root("org/jetbrains/kotlin", "kotlin-stdlib", "2.1.20-dev-1234", "kotlin-stdlib-2.1.20-dev-1234.jar")],
    ])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        iml_data_list = [iml_data],
        env = {"JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT": "2.1.20-dev-1234"},
    )

    asserts.equals(env, ["@lib//:org.jetbrains.kotlin/kotlin-stdlib-2.1.20-dev-1234.jar"], result)
    return unittest.end(env)

module_library_ignores_kotlin_dev_snapshot_env_test = unittest.make(_module_library_ignores_kotlin_dev_snapshot_env_test_impl)

# --- Test 6: project_library_local_and_jar_directory_test ---

def _project_library_local_and_jar_directory_test_impl(ctx):
    env = unittest.begin(ctx)

    lib_xml = _project_library_xml(
        "local-jars",
        local_roots = ["jar://$PROJECT_DIR$/lib/direct.jar!/"],
        jar_directories = ["file://$PROJECT_DIR$/lib/jars"],
    )

    iml = _iml_xml(project_libraries = ["local-jars"])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        library_xmls = [_library_xml_struct("local_jars", lib_xml)],
        iml_data_list = [iml_data],
        entries_by_dir = {
            "lib/jars": ["lib/jars/a.jar", "lib/jars/readme.txt", "lib/jars/b.jar"],
        },
    )

    asserts.equals(env, [
        "@lib//:direct.jar",
        "@lib//jars:a.jar",
        "@lib//jars:b.jar",
    ], result)
    return unittest.end(env)

project_library_local_and_jar_directory_test = unittest.make(_project_library_local_and_jar_directory_test_impl)

# --- Test 7: module_library_project_and_module_dir_resolution_test ---

def _module_library_project_and_module_dir_resolution_test_impl(ctx):
    env = unittest.begin(ctx)

    iml = _iml_xml(module_library_roots = [
        [
            "jar://$PROJECT_DIR$/shared/lib/a.jar!/",
            "jar://$MODULE_DIR$/../lib/b.jar!/",
        ],
    ])
    iml_data = _iml_data("intellij.foo", "plugins/foo/intellij.foo.iml", iml)

    result = _derive(iml_data_list = [iml_data])

    asserts.equals(env, [
        "//plugins/lib:b.jar",
        "//shared/lib:a.jar",
    ], result)
    return unittest.end(env)

module_library_project_and_module_dir_resolution_test = unittest.make(_module_library_project_and_module_dir_resolution_test_impl)

# --- Test 8: module_library_mixed_snapshot_test ---

def _module_library_mixed_snapshot_test_impl(ctx):
    env = unittest.begin(ctx)

    iml = _iml_xml(module_library_roots = [
        [
            _maven_root("com/example", "foo", "1.0-SNAPSHOT", "foo-1.0-SNAPSHOT.jar"),
            _maven_root("com/example", "foo-util", "1.0", "foo-util-1.0.jar"),
        ],
    ])
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(iml_data_list = [iml_data])

    asserts.equals(env, [
        "@lib//snapshots:foo-1.0-SNAPSHOT.jar",
        "@lib//snapshots:foo-util-1.0.jar",
    ], result)
    return unittest.end(env)

module_library_mixed_snapshot_test = unittest.make(_module_library_mixed_snapshot_test_impl)

# --- Test 9: dedup_and_sorting_test ---

def _dedup_and_sorting_test_impl(ctx):
    env = unittest.begin(ctx)

    # Project library referencing a local jar
    lib_xml = _project_library_xml("z-lib", local_roots = [
        "jar://$PROJECT_DIR$/lib/shared.jar!/",
    ])

    # Module library referencing the same jar via $PROJECT_DIR$, plus another jar
    # that sorts before the shared one to verify sorting
    iml = _iml_xml(
        project_libraries = ["z-lib"],
        module_library_roots = [
            ["jar://$PROJECT_DIR$/lib/shared.jar!/"],
            ["jar://$PROJECT_DIR$/lib/alpha.jar!/"],
        ],
    )
    iml_data = _iml_data("intellij.test", "plugins/test/intellij.test.iml", iml)

    result = _derive(
        library_xmls = [_library_xml_struct("z_lib", lib_xml)],
        iml_data_list = [iml_data],
    )

    # No duplicates, sorted lexicographically
    asserts.equals(env, [
        "@lib//:alpha.jar",
        "@lib//:shared.jar",
    ], result)
    return unittest.end(env)

dedup_and_sorting_test = unittest.make(_dedup_and_sorting_test_impl)

# --- Test 10: module_library_ultimate_repo_test ---

def _module_library_ultimate_repo_test_impl(ctx):
    env = unittest.begin(ctx)

    # Community module with a Maven module-library → repo should be @lib
    community_iml = _iml_xml(module_library_roots = [
        [_maven_root("com/comm", "core", "1.0", "core-1.0.jar")],
    ])
    community_data = _iml_data(
        "intellij.comm.mod",
        "community/plugins/comm/intellij.comm.mod.iml",
        community_iml,
        is_community = True,
    )

    # Ultimate module with a Maven module-library → repo should be @ultimate_lib
    ultimate_iml = _iml_xml(module_library_roots = [
        [_maven_root("com/ult", "ext", "2.0", "ext-2.0.jar")],
    ])
    ultimate_data = _iml_data(
        "intellij.ult.mod",
        "plugins/ult/intellij.ult.mod.iml",
        ultimate_iml,
        is_community = False,
    )

    result = _derive(
        iml_data_list = [community_data, ultimate_data],
        is_community_only = False,
        community_root_rel = "community",
    )

    asserts.equals(env, [
        "@lib//:com.comm/core-1.0.jar",
        "@ultimate_lib//:com.ult/ext-2.0.jar",
    ], result)
    return unittest.end(env)

module_library_ultimate_repo_test = unittest.make(_module_library_ultimate_repo_test_impl)

# --- Test 11: ultimate_mode_local_path_routing_test ---

def _ultimate_mode_local_path_routing_test_impl(ctx):
    env = unittest.begin(ctx)

    # Project library with local roots hitting community/lib/ and lib/ branches,
    # plus a jarDirectory under community/lib/jars
    lib_xml = _project_library_xml(
        "multi-local",
        local_roots = [
            "jar://$PROJECT_DIR$/community/lib/comm.jar!/",
            "jar://$PROJECT_DIR$/lib/ult.jar!/",
        ],
        jar_directories = ["file://$PROJECT_DIR$/community/lib/jars"],
    )

    # Ultimate module with module-library local roots hitting community/... and other/
    ultimate_iml = _iml_xml(
        project_libraries = ["multi-local"],
        module_library_roots = [
            [
                "jar://$PROJECT_DIR$/community/plugins/foo/bar.jar!/",
                "jar://$PROJECT_DIR$/other/stuff.jar!/",
            ],
        ],
    )
    ultimate_data = _iml_data(
        "intellij.ult.mod",
        "plugins/ult/intellij.ult.mod.iml",
        ultimate_iml,
        is_community = False,
    )

    result = _derive(
        library_xmls = [_library_xml_struct("multi_local", lib_xml)],
        iml_data_list = [ultimate_data],
        is_community_only = False,
        community_root_rel = "community",
        entries_by_dir = {
            "community/lib/jars": ["community/lib/jars/x.jar"],
        },
    )

    asserts.equals(env, [
        "//other:stuff.jar",
        "@community//plugins/foo:bar.jar",
        "@lib//:comm.jar",
        "@lib//jars:x.jar",
        "@ultimate_lib//:ult.jar",
    ], result)
    return unittest.end(env)

ultimate_mode_local_path_routing_test = unittest.make(_ultimate_mode_local_path_routing_test_impl)

# --- Suite ---

def jps_library_derivation_pipeline_test_suite(name):
    """Test suite for derive_library_targets() end-to-end pipeline."""
    unittest.suite(
        name,
        project_libraries_only_include_referenced_test,
        project_library_repo_selection_test,
        project_library_mixed_snapshot_test,
        project_library_kotlin_dev_snapshot_env_test,
        module_library_ignores_kotlin_dev_snapshot_env_test,
        project_library_local_and_jar_directory_test,
        module_library_project_and_module_dir_resolution_test,
        module_library_mixed_snapshot_test,
        dedup_and_sorting_test,
        module_library_ultimate_repo_test,
        ultimate_mode_local_path_routing_test,
    )

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive", "http_file")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "get_auth")

_RESOLUTION_FACTS_VERSION = "resolution.v38"

_RESOLVER_VERSION = "0.2.0"
_RESOLVER_BINARY_URL_PREFIX = (
    "https://cache-redirector.jetbrains.com/github.com/JetBrains/bazel-kmp-resolver/releases/download/%s" % _RESOLVER_VERSION
)
_RESOLVER_BINARIES = {
    "linux_arm64": struct(
        filename = "bazel-kmp-resolver-linux-arm64",
        sha256 = "1e90598162f08e710f835a0804a7e221eca6380667dc70d31ef56e562d24b9b5",
    ),
    "linux_x64": struct(
        filename = "bazel-kmp-resolver-linux-x64",
        sha256 = "85e1c04d9392304272b0f07fb3c233d43a550dba253b7f369306b81198672d97",
    ),
    "macos_arm64": struct(
        filename = "bazel-kmp-resolver-macos-arm64",
        sha256 = "070fcda61d29f48561d46b5777ddf0c8e63b06b379d6e588020ec58c94164ee3",
    ),
    "macos_x64": struct(
        filename = "bazel-kmp-resolver-macos-x64",
        sha256 = "46980f9168a8651ed2d34f3ad7f8976433f441317b0d8d8d22af4d634bf65016",
    ),
    "windows_x64": struct(
        filename = "bazel-kmp-resolver-windows-x64.exe",
        sha256 = "27e8a62bfcd5cf3fa6558cf9f4d3a67fa7869a564686e94e158104e18dc26137",
    ),
    "windows_arm64": struct(
        # TODO: GraalVM does not support ARM64 Windows yet, so use the x64 binary for now.
        filename = "bazel-kmp-resolver-windows-x64.exe",
        sha256 = "27e8a62bfcd5cf3fa6558cf9f4d3a67fa7869a564686e94e158104e18dc26137",
    ),
}

# Hermetic node/npm handed to the resolver so it can resolve the NPM dependencies declared by the
# `package.json` embedded in klibs. The repositories are created by the `node` extension of
# `rules_nodejs` (see `use_repo` in MODULE.bazel); npm is always run as `node npm-cli.js ...`
# because the `npm`/`npm.cmd` wrapper scripts are not portably executable.
_NODEJS_REPOSITORIES = {
    "linux_arm64": struct(
        repository = "nodejs_linux_arm64",
        node = "bin/nodejs/bin/node",
        npm_cli_js = "bin/nodejs/lib/node_modules/npm/bin/npm-cli.js",
    ),
    "linux_x64": struct(
        repository = "nodejs_linux_amd64",
        node = "bin/nodejs/bin/node",
        npm_cli_js = "bin/nodejs/lib/node_modules/npm/bin/npm-cli.js",
    ),
    "macos_arm64": struct(
        repository = "nodejs_darwin_arm64",
        node = "bin/nodejs/bin/node",
        npm_cli_js = "bin/nodejs/lib/node_modules/npm/bin/npm-cli.js",
    ),
    "macos_x64": struct(
        repository = "nodejs_darwin_amd64",
        node = "bin/nodejs/bin/node",
        npm_cli_js = "bin/nodejs/lib/node_modules/npm/bin/npm-cli.js",
    ),
    "windows_x64": struct(
        repository = "nodejs_windows_amd64",
        node = "bin/nodejs/node.exe",
        npm_cli_js = "bin/nodejs/node_modules/npm/bin/npm-cli.js",
    ),
    "windows_arm64": struct(
        repository = "nodejs_windows_arm64",
        node = "bin/nodejs/node.exe",
        npm_cli_js = "bin/nodejs/node_modules/npm/bin/npm-cli.js",
    ),
}

def _node_tooling(module_ctx):
    host_key = _host_key(module_ctx)
    nodejs = _NODEJS_REPOSITORIES.get(host_key)
    if nodejs == None:
        fail("No node/npm repository available for host %s (%s/%s)" % (
            host_key,
            module_ctx.os.name,
            module_ctx.os.arch,
        ))
    return struct(
        node = module_ctx.path(Label("@%s//:%s" % (nodejs.repository, nodejs.node))),
        npm_cli_js = module_ctx.path(Label("@%s//:%s" % (nodejs.repository, nodejs.npm_cli_js))),
    )

# See https://github.com/JetBrains/bazel-kmp-resolver/tree/main/testResources for example of production JSONs that could be returned by the resolver
_EMPTY_RESOLUTION_JSON = json.encode({
    "askedCoordinates": [],
    "askedRepositories": [],
    "libraries": {},
})

# Platforms the resolver can be asked for (`kmp.configure(platforms = ...)`), in the order their repositories are generated.
# Each platform gets its own `kmp_deps_<platform>` repository, with the same target names across platforms: a library is
# `@kmp_deps_wasmjs//:org_jetbrains_kotlin_kotlin_stdlib` for wasmJs and `@kmp_deps_jvm//:org_jetbrains_kotlin_kotlin_stdlib` for the JVM.
_PLATFORMS = ["wasmjs", "jvm"]

def _deps_repository_name(platform):
    return "kmp_deps_%s" % platform

def _kmp_deps_repository_impl(repository_ctx):
    repository_ctx.file("BUILD.bazel", repository_ctx.attr.build_file_content)

_kmp_deps_repository = repository_rule(
    implementation = _kmp_deps_repository_impl,
    attrs = {
        "build_file_content": attr.string(
            mandatory = True,
            doc = "Generated BUILD file content.",
        ),
    },
)

def _materialize_resolution(resolution, platform):
    libraries = _manifest_libraries(resolution)
    target_names = _library_target_names(libraries, platform)
    target_names_by_module = _target_names_by_module(target_names)

    materialized_targets = []
    materialized_target_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _platform_variant(library, platform)
        if variant == None:
            continue

        variant_id = variant["variantId"]
        target_name = _build_target_name(variant_id)
        if target_name in materialized_target_names:
            continue
        materialized_target_names[target_name] = True

        source_jar = variant.get("sourceJar")
        target = {
            "coordinate": library_id,
            "name": target_name,
            "source_jar": None if source_jar == None else _artifact_label(source_jar),
            "deps": _dependency_labels(library_id, variant, "dependencies", target_names, target_names_by_module),
            "exported_deps": _dependency_labels(library_id, variant, "exportedDependencies", target_names, target_names_by_module),
        }
        if platform == "wasmjs":
            target["klib"] = _artifact_label(variant["klib"])
            target["npm_packages"] = {
                package["name"]: "@%s//:package" % _npm_repository_name(package)
                for package in variant.get("npmPackages", [])
            }
        elif platform == "jvm":
            target["jar"] = _artifact_label(variant["jar"])
        else:
            fail("Unsupported platform: %s" % platform)
        materialized_targets.append(target)

    return struct(
        aliases = _library_aliases(libraries, platform),
        targets = materialized_targets,
    )

def _platform_variant(library, platform):
    """The variant of [library] for [platform], or None when the library is not resolved for that platform."""
    for variant in library["variants"]:
        if variant.get("type") == platform:
            return variant
    return None

def _manifest_libraries(resolution):
    return resolution["libraries"]

def _library_target_names(libraries, platform):
    target_names = {}
    used_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _platform_variant(library, platform)
        if variant == None:
            # A library may resolve to different versions on different platforms: only the libraries of the
            # platform at hand compete for a versionless target name.
            continue
        target_name = _versionless_target_name(library_id)
        used_library = used_names.get(target_name)
        if used_library != None and used_library != library_id:
            fail("Resolver produced multiple libraries for versionless KMP target '%s': %s and %s" % (
                target_name,
                used_library,
                library_id,
            ))
        used_names[target_name] = library_id
        target_names[library_id] = target_name
        target_names[variant["variantId"]] = target_name
    return target_names

def _target_names_by_module(target_names):
    target_names_by_module = {}
    for coordinate in sorted(target_names.keys()):
        target_name = target_names[coordinate]
        module_id = _maven_module_id(coordinate)
        existing = target_names_by_module.get(module_id)
        if existing != None and existing != target_name:
            fail("Multiple KMP targets found for Maven module '%s': %s and %s" % (
                module_id,
                existing,
                target_name,
            ))
        target_names_by_module[module_id] = target_name
    return target_names_by_module

def _library_aliases(libraries, platform):
    real_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _platform_variant(library, platform)
        if variant != None:
            target_name = _build_target_name(variant["variantId"])
            real_names[target_name] = True
            if platform == "jvm":
                real_names[_jvm_import_name(target_name)] = True

    aliases = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _platform_variant(library, platform)
        if variant == None:
            continue
        target_name = _build_target_name(variant["variantId"])

        # A library is aliased by its own versionless name and by the versionless name of its platform-specific variant
        # (`org_jetbrains_kotlin_kotlin_stdlib` and `org_jetbrains_kotlin_kotlin_stdlib_wasm_js` for the same target):
        # consumers naming the Maven artifact they use, as JPS libraries do, need no knowledge of the KMP umbrella.
        for alias_name in [_versionless_target_name(library_id), _versionless_target_name(variant["variantId"])]:
            if alias_name == target_name:
                continue
            if alias_name in real_names:
                fail("Alias target name for %s collides with a real generated target: %s" % (library_id, alias_name))

            if alias_name in aliases:
                existing = aliases[alias_name]
                if existing != target_name:
                    aliases[alias_name] = None
                continue
            aliases[alias_name] = target_name

    return [
        {
            "actual": ":%s" % aliases[name],
            "name": name,
        }
        for name in sorted(aliases.keys())
        if aliases[name] != None
    ]

def _versionless_target_name(coordinate):
    return _build_target_name(_maven_module_id(coordinate))

def _maven_coordinate_parts(coordinate):
    parts = coordinate.split(":")
    if len(parts) != 3 or not parts[0] or not parts[1] or not parts[2]:
        fail("Expected Maven coordinate group:artifact:version, got: %s" % coordinate)
    return parts

def _maven_module_id(maven_id):
    parts = maven_id.split(":")
    if len(parts) != 2 and len(parts) != 3:
        fail("Expected Maven module group:artifact or coordinate group:artifact:version, got: %s" % maven_id)
    if not parts[0] or not parts[1]:
        fail("Expected Maven module group:artifact or coordinate group:artifact:version, got: %s" % maven_id)
    if len(parts) == 3 and not parts[2]:
        fail("Expected Maven coordinate group:artifact:version, got: %s" % maven_id)
    return "%s:%s" % (parts[0], parts[1])

def _validate_maven_module_id(module_id):
    parts = module_id.split(":")
    if len(parts) != 2 or not parts[0] or not parts[1]:
        fail("Expected Maven module group:artifact, got: %s" % module_id)

def _dependency_labels(library_id, variant, field, target_names, target_names_by_module):
    dependencies = variant.get(field, [])
    labels = {}
    for dependency_id in dependencies:
        dependency_module_id = _maven_module_id(dependency_id)
        target_name = target_names.get(dependency_id)
        if target_name == None:
            target_name = target_names_by_module.get(dependency_module_id)
        if target_name == None:
            fail("Library %s references unknown %s dependency: %s" % (
                library_id,
                field,
                dependency_id,
            ))
        label = ":%s" % target_name
        if label in labels:
            continue
        labels[label] = True
    return sorted(labels.keys())

def _artifact_label(artifact):
    return "@%s//file" % _artifact_repository_name(artifact)

def _artifact_key(artifact):
    return json.encode([
        artifact["groupId"],
        artifact["artifactId"],
        artifact["version"],
        _artifact_basename(artifact),
    ])

def _artifact_basename(artifact):
    return _basename_from_url(artifact["urls"][0])

def _basename_from_url(url):
    stripped = url.split("?", 1)[0].split("#", 1)[0]
    return stripped.rsplit("/", 1)[-1]

def _collect_artifacts(resolution):
    """Maven artifacts of all variants of all libraries; one `http_file` per artifact is shared by the platform repositories."""
    artifacts = {}
    libraries = _manifest_libraries(resolution)
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        for variant in library["variants"]:
            variant_type = variant.get("type")
            if variant_type == "wasmjs":
                _add_artifact(artifacts, variant["klib"])
            elif variant_type == "jvm":
                _add_artifact(artifacts, variant["jar"])
            else:
                fail("Library %s has a variant of unsupported type '%s', supported types: %s" % (library_id, variant_type, _PLATFORMS))
            source_jar = variant.get("sourceJar")
            if source_jar != None:
                _add_artifact(artifacts, source_jar)
    return artifacts

def _add_artifact(artifacts, artifact):
    artifact_key = _artifact_key(artifact)
    existing = artifacts.get(artifact_key)
    if existing != None:
        if existing["integrity"] != artifact["integrity"]:
            fail("Artifact checksum collision for %s" % artifact_key)
        return
    artifacts[artifact_key] = artifact

def _collect_npm_packages(resolution):
    packages = {}
    libraries = _manifest_libraries(resolution)
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _platform_variant(library, "wasmjs")  # NPM packages are only relevant to the wasmJs platform
        if variant == None:
            continue

        for package in variant.get("npmPackages", []):
            package_key = json.encode([package["name"], package["version"]])
            existing = packages.get(package_key)
            if existing != None:
                if existing["integrity"] != package["integrity"] or existing["url"] != package["url"]:
                    fail("NPM package resolution collision for %s" % package_key)
                continue
            packages[package_key] = package
    return packages

def _npm_repository_name(package):
    return "npm-%s-%s_http" % (
        _repository_name_part(package["name"]),
        _repository_name_part(package["version"]),
    )

def _artifact_repository_name(artifact):
    artifact_id = artifact["artifactId"]
    version = artifact["version"]
    classifier = _artifact_classifier(artifact, artifact_id, version)
    classifier_suffix = "" if not classifier else "-%s" % _repository_name_part(classifier)
    return "%s-%s-%s%s_http" % (
        _repository_name_part(artifact["groupId"]),
        _repository_name_part(artifact_id),
        _repository_name_part(version),
        classifier_suffix,
    )

def _artifact_classifier(artifact, artifact_id, version):
    stem = _strip_file_extension(_artifact_basename(artifact))
    prefix = "%s-%s" % (artifact_id, version)
    if stem == prefix:
        return ""
    if stem.startswith(prefix + "-"):
        return stem[len(prefix) + 1:]
    return stem

def _strip_file_extension(basename):
    if "." not in basename:
        return basename
    return basename.rsplit(".", 1)[0]

def _sanitize_name_part(value, allowed, lowercase = False, force_separator_chars = ""):
    chars = []
    previous_was_separator = False
    for i in range(len(value)):
        c = value[i]
        if c in allowed:
            chars.append(c.lower() if lowercase else c)
            previous_was_separator = False
        elif c in force_separator_chars or not previous_was_separator:
            chars.append("_")
            previous_was_separator = True
    return "".join(chars).strip("_")

def _repository_name_part(value):
    return _sanitize_name_part(
        value,
        allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-",
        force_separator_chars = ".",
    )

def _render_build_file(materialized, platform):
    return "\n".join([
        _render_load_statements(platform),
        "",
        "package(default_visibility = [\"//visibility:public\"])",
        "",
        _render_targets_block(materialized.targets, platform),
        "",
        _render_aliases_block(materialized.aliases),
        "",
    ])

def _render_load_statements(platform):
    if platform == "wasmjs":
        return "load(\"@rules_jvm//:wasmjs.bzl\", \"wasmjs_import\")"
    if platform == "jvm":
        return "\n".join([
            "load(\"@rules_java//java:defs.bzl\", \"java_library\")",
            "load(\"@rules_jvm//:jvm.bzl\", \"jvm_import\")",
        ])
    fail("Unsupported platform: %s" % platform)

def _render_targets_block(targets, platform):
    blocks = []
    for target in targets:
        lines = [
            "# %s" % target["coordinate"],
        ]
        if platform == "wasmjs":
            lines.extend(_render_wasmjs_import(target))
        elif platform == "jvm":
            lines.extend(_render_jvm_import(target))
        else:
            fail("Unsupported platform: %s" % platform)
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks)

def _render_aliases_block(aliases):
    blocks = []
    for alias in aliases:
        blocks.append("\n".join([
            "alias(",
            "    name = %s," % _quote(alias["name"]),
            "    actual = %s," % _quote(alias["actual"]),
            ")",
        ]))
    return "\n\n".join(blocks)

def _render_wasmjs_import(target):
    """
    Renders `wasmjs_import` target from a `bazel-kmp-resolver` Bazel manifest entry.

    Example:

        wasmjs_import(
          name = "org_jetbrains_kotlinx_kotlinx_datetime_wasm_js_0_7_1_0_6_x_compat",
          klib = "@@community++kmp+org_jetbrains_kotlinx-kotlinx-datetime-wasm-js-0_7_1-0_6_x-compat_http//file:file",
          source_jar = "@@community++kmp+org_jetbrains_kotlinx-kotlinx-datetime-wasm-js-0_7_1-0_6_x-compat-sources_http//file:file",
          exported_deps = [":org_jetbrains_kotlin_kotlin_stdlib_wasm_js_2_4_0", ":org_jetbrains_kotlinx_kotlinx_serialization_core_wasm_js_1_11_0"],
          npm_packages = {"@js-joda/core": "@@community++kmp+npm-js-joda_core-3_2_0_http//:package"},
        )
    """

    lines = [
        "wasmjs_import(",
        "    name = %s," % _quote(target["name"]),
        "    klib = %s," % _quote(target["klib"]),
    ]
    if target["source_jar"] != None:
        lines.append("    source_jar = %s," % _quote(target["source_jar"]))
    lines.extend(_render_label_list_attr("deps", target["deps"]))
    lines.extend(_render_label_list_attr("exported_deps", target["exported_deps"]))
    lines.extend(_render_string_dict_attr("npm_packages", target["npm_packages"]))
    lines.append(")")
    return lines

def _render_jvm_import(target):
    """Renders the `jvm_import` of a library's jar and the `java_library` exposing it with its dependencies.

    Both come from a `bazel-kmp-resolver` Bazel manifest entry. `jvm_import` has no `exports`, hence the wrapper: the
    manifest's `exportedDependencies` go to `exports` (compile and runtime class paths of dependents, transitively) and
    its `dependencies` to `runtime_deps` (runtime class path only). Aliases and dependency labels point at the `java_library`.

    Example:

        jvm_import(
            name = "io_ktor_ktor_client_core_jvm_3_6_0_import",
            jar = "@@community++kmp+io_ktor-ktor-client-core-jvm-3_6_0_http//file:file",
            source_jar = "@@community++kmp+io_ktor-ktor-client-core-jvm-3_6_0-sources_http//file:file",
        )

        java_library(
            name = "io_ktor_ktor_client_core_jvm_3_6_0",
            exports = [
                ":io_ktor_ktor_client_core_jvm_3_6_0_import",
                ":io_ktor_ktor_http_jvm_3_6_0",
                ":org_jetbrains_kotlin_kotlin_stdlib_2_4_21_rc",
            ],
            runtime_deps = [
                ":org_slf4j_slf4j_api_2_0_17",
            ],
        )
    """

    import_name = _jvm_import_name(target["name"])
    lines = [
        "jvm_import(",
        "    name = %s," % _quote(import_name),
        "    jar = %s," % _quote(target["jar"]),
    ]
    if target["source_jar"] != None:
        lines.append("    source_jar = %s," % _quote(target["source_jar"]))
    lines.extend([
        ")",
        "",
        "java_library(",
        "    name = %s," % _quote(target["name"]),
    ])
    lines.extend(_render_label_list_attr("exports", [":%s" % import_name] + target["exported_deps"]))
    lines.extend(_render_label_list_attr("runtime_deps", target["deps"]))
    lines.append(")")
    return lines

def _jvm_import_name(target_name):
    return "%s_import" % target_name

def _render_string_dict_attr(name, values):
    if not values:
        return []
    lines = [
        "    %s = {" % name,
    ]
    for key in sorted(values.keys()):
        lines.append("        %s: %s," % (_quote(key), _quote(values[key])))
    lines.append(
        "    },",
    )
    return lines

def _render_label_list_attr(name, values):
    if not values:
        return []
    lines = [
        "    %s = [" % name,
    ]
    for value in values:
        lines.append("        %s," % _quote(value))
    lines.append(
        "    ],",
    )
    return lines

def _quote(value):
    return "\"%s\"" % value.replace("\\", "\\\\").replace("\"", "\\\"")

def _build_target_name(value):
    return _sanitize_name_part(
        value,
        allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_",
        lowercase = True,
    )

def _read_configure_tag(module_ctx):
    root_tags = []
    non_root_tags = []
    for mod in module_ctx.modules:
        if mod.is_root:
            root_tags.extend(mod.tags.configure)
        else:
            non_root_tags.extend(mod.tags.configure)

    if len(root_tags) > 1:
        fail("Only one kmp.configure(...) tag is supported in the root module.")
    if root_tags:
        return _validated_configure_tag(root_tags[0])
    if non_root_tags:
        return _validated_configure_tag(non_root_tags[0])
    return struct(
        platforms = [],
        deps = [],
        repositories = [],
        substitutions = {},
        npm_package_version_overrides = {},
    )

def _validated_configure_tag(tag):
    if not tag.platforms:
        fail("kmp.configure(platforms = [...]) must list at least one platform among %s." % _PLATFORMS)
    seen = {}
    for platform in tag.platforms:
        if platform not in _PLATFORMS:
            fail("kmp.configure(platforms = ...) got unsupported platform '%s', supported platforms: %s" % (platform, _PLATFORMS))
        if platform in seen:
            fail("kmp.configure(platforms = ...) lists platform '%s' twice." % platform)
        seen[platform] = True
    return tag

def _resolve_with_facts(module_ctx, config):
    if not config.deps:
        return _EMPTY_RESOLUTION_JSON

    fact_key = _resolution_fact_key(config)
    if fact_key in module_ctx.facts:
        return module_ctx.facts[fact_key]

    return _resolve_fresh(module_ctx, config)

def _resolver_binary(module_ctx):
    host_key = _host_key(module_ctx)
    resolver_binary = _RESOLVER_BINARIES.get(host_key)
    if resolver_binary == None:
        fail("No bazel-kmp-resolver binary available for host %s (%s/%s)" % (
            host_key,
            module_ctx.os.name,
            module_ctx.os.arch,
        ))
    return resolver_binary

def _host_key(module_ctx):
    os_name = module_ctx.os.name.lower()
    arch = module_ctx.os.arch.lower()

    if "linux" in os_name:
        os_key = "linux"
    elif "mac" in os_name or "darwin" in os_name:
        os_key = "macos"
    elif "windows" in os_name:
        os_key = "windows"
    else:
        fail("Unsupported bazel-kmp-resolver host OS: %s" % module_ctx.os.name)

    if arch in ["amd64", "x86_64", "x64"]:
        arch_key = "x64"
    elif arch in ["arm64", "aarch64"]:
        arch_key = "arm64"
    else:
        fail("Unsupported bazel-kmp-resolver host architecture: %s" % module_ctx.os.arch)

    return "%s_%s" % (os_key, arch_key)

def _resolve_fresh(module_ctx, config):
    resolver_binary = _resolver_binary(module_ctx)

    # The extension's working directory persists across evaluations (the resolver's cache lives there). Overwriting an
    # executable that already ran in place makes macOS kill the new one with "Code Signature Invalid" (the kernel
    # caches the signature per file), so the binary is named by version: a new version lands in a new file.
    resolver_binary_path = "%s-%s" % (_RESOLVER_VERSION, resolver_binary.filename)
    module_ctx.download(
        url = ["%s/%s" % (_RESOLVER_BINARY_URL_PREFIX, resolver_binary.filename)],
        output = resolver_binary_path,
        sha256 = resolver_binary.sha256,
        executable = True,
    )

    node_tooling = _node_tooling(module_ctx)
    resolution_path = "resolution.json"
    module_ctx.file(resolution_path, "")
    args = [
        module_ctx.path(resolver_binary_path),
        "--output-manifest-file",
        module_ctx.path(resolution_path),
        "--node-executable",
        node_tooling.node,
        "--npm-cli-js",
        node_tooling.npm_cli_js,
    ]
    for platform in sorted(config.platforms):
        args.extend(["--platform", platform])
    for dep in config.deps:
        args.extend(["--coordinate", dep])
    for repository in config.repositories:
        args.extend(["--repository", repository])
    for source_module_id in sorted(config.substitutions.keys()):
        target_coordinate = config.substitutions[source_module_id]
        _validate_maven_module_id(source_module_id)
        _maven_coordinate_parts(target_coordinate)
        if _maven_module_id(target_coordinate) == source_module_id:
            fail("kmp.configure(substitutions = ...) substitutes %s by another version of itself (%s): versions are picked by the resolver's conflict resolution, declare the wanted version in deps instead." % (
                source_module_id,
                target_coordinate,
            ))
        args.extend(["--substitution", "%s=%s" % (source_module_id, target_coordinate)])
    for package_name in sorted(config.npm_package_version_overrides.keys()):
        args.extend(["--npm-package-version", "%s=%s" % (package_name, config.npm_package_version_overrides[package_name])])

    netrc = module_ctx.os.environ.get("NETRC", "")  # Read NETRC without taking into account as an input of the repository_rule, authentication does not matter in the reproducibility of the resolution
    repository_credentials = _repository_credentials(module_ctx, config.repositories, netrc)
    if repository_credentials:
        credentials_file = "repository-credentials.json"
        module_ctx.file(credentials_file, json.encode(repository_credentials), executable = False)
        args.extend(["--repository-credentials-file", module_ctx.path(credentials_file)])

    # Bazel credential helpers for the resolver, `[scope=]path` entries separated by `;` with `%workspace%` standing for
    # the workspace root, as Bazel's own `--credential_helper`. Read like NETRC above, without being an input of the
    # resolution. A host a helper applies to is authenticated by the helper alone, the credentials file serves the
    # other hosts, as in Bazel.
    # Bazel does not expose the value of `--credential_helper` to repository rules yet, so we will have to set the following
    # manually when we introduce the credential helper in the codebase:
    #
    # #common.bazelrc
    # common --repo_env=KMP_RESOLVER_CREDENTIAL_HELPER=%workspace%/the_helper_path
    #
    for helper in [h for h in module_ctx.os.environ.get("KMP_RESOLVER_CREDENTIAL_HELPER", "").split(";") if h]:
        args.extend(["--credential-helper", helper.replace("%workspace%", str(_workspace_root(module_ctx)))])

    # Counterparts of Bazel's `--credential_helper_timeout` (10s) and `--credential_helper_cache_duration` (30m), with
    # the same duration syntax; the resolver applies the same defaults when they are unset.
    for env_name, flag in [
        ("KMP_RESOLVER_CREDENTIAL_HELPER_TIMEOUT", "--credential-helper-timeout-ms"),
        ("KMP_RESOLVER_CREDENTIAL_HELPER_CACHE_DURATION", "--credential-helper-cache-duration-ms"),
    ]:
        duration = module_ctx.os.environ.get(env_name, "")
        if duration:
            args.extend([flag, str(_duration_to_millis(duration, env_name))])

    result = module_ctx.execute(
        args,
        quiet = True,
        timeout = 3600,
    )
    if result.return_code:
        fail("KMP resolver failed with exit code %s.\nstdout:\n%s\nstderr:\n%s" % (
            result.return_code,
            result.stdout,
            result.stderr,
        ))

    return module_ctx.read(resolution_path)

def _workspace_root(module_ctx):
    return module_ctx.path(Label("@@//:MODULE.bazel")).dirname

_MILLIS_PER_UNIT = {"ms": 1, "s": 1000, "m": 60 * 1000, "h": 60 * 60 * 1000, "d": 24 * 60 * 60 * 1000}

def _duration_to_millis(duration, what):
    """Converts a duration written as Bazel's duration flags expect it (`500ms`, `10s`, `30m`, `1h`, `1d`, or `0`)."""
    if duration == "0":
        return 0
    digits = duration.rstrip("dhms")
    unit = duration[len(digits):]
    if not digits.isdigit() or unit not in _MILLIS_PER_UNIT:
        fail("%s must be a duration such as 10s, 30m, 1h or 500ms, got %r" % (what, duration))
    return int(digits) * _MILLIS_PER_UNIT[unit]

def _repository_credentials(module_ctx, repositories, netrc):
    auth = get_auth(_auth_context(module_ctx, netrc), repositories)
    credentials = []
    for repository in repositories:
        repository_auth = auth.get(repository)
        if repository_auth == None:
            continue

        auth_type = repository_auth.get("type")
        login = repository_auth.get("login")
        password = repository_auth.get("password")
        if auth_type != "basic" or not login or not password:
            fail("KMP resolver supports only basic repository auth for %s, but get_auth returned %s auth." % (
                repository,
                auth_type,
            ))

        credentials.append({
            "repositoryUrl": repository,
            "username": login,
            "password": password,
        })
    return credentials

def _auth_context(module_ctx, netrc):
    return struct(
        attr = struct(
            auth_patterns = {},
            netrc = netrc,
        ),
        os = module_ctx.os,
        path = module_ctx.path,
        read = module_ctx.read,
    )

def _resolution_fact_key(config):
    return json.encode([
        _RESOLUTION_FACTS_VERSION,
        sorted(config.platforms),
        config.deps,
        config.repositories,
        _sorted_dict_items(config.substitutions),
        _sorted_dict_items(config.npm_package_version_overrides),
    ])

def _sorted_dict_items(values):
    return [[key, values[key]] for key in sorted(values.keys())]

def _register_artifact_repositories(resolution):
    artifacts = _collect_artifacts(resolution)

    used_names = {}
    for artifact_key in sorted(artifacts.keys()):
        artifact = artifacts[artifact_key]
        urls = artifact["urls"]
        repo_name = _artifact_repository_name(artifact)
        if repo_name in used_names and used_names[repo_name] != artifact_key:
            fail("Artifact repository name collision for '%s' and '%s': %s" % (
                used_names[repo_name],
                artifact_key,
                repo_name,
            ))
        used_names[repo_name] = artifact_key
        http_file(
            name = repo_name,
            downloaded_file_path = _basename_from_url(urls[0]),
            integrity = artifact["integrity"],
            urls = urls,
        )

    packages = _collect_npm_packages(resolution)
    for package_key in sorted(packages.keys()):
        package = packages[package_key]
        repo_name = _npm_repository_name(package)
        if repo_name in used_names and used_names[repo_name] != package_key:
            fail("Artifact repository name collision for '%s' and '%s': %s" % (
                used_names[repo_name],
                package_key,
                repo_name,
            ))
        used_names[repo_name] = package_key
        http_archive(
            name = repo_name,
            url = package["url"],
            integrity = package["integrity"],
            # npm tarballs place the package content under a `package/` root directory
            strip_prefix = "package",
            build_file = Label(":npm.BUILD.bazel"),
        )

def _kmp_extension_impl(module_ctx):
    config = _read_configure_tag(module_ctx)
    resolution_json = _resolve_with_facts(module_ctx, config)
    resolution = json.decode(resolution_json)
    _register_artifact_repositories(resolution)

    # Without any `kmp.configure(...)` tag, every platform gets an empty repository so that no `use_repo` dangles.
    platforms = [platform for platform in _PLATFORMS if platform in config.platforms] if config.platforms else _PLATFORMS
    repository_names = []
    for platform in platforms:
        repository_name = _deps_repository_name(platform)
        repository_names.append(repository_name)
        _kmp_deps_repository(
            name = repository_name,
            build_file_content = _render_build_file(_materialize_resolution(resolution, platform), platform),
        )

    facts = {_resolution_fact_key(config): resolution_json} if config.deps else {}
    if module_ctx.root_module_has_non_dev_dependency:
        return module_ctx.extension_metadata(
            root_module_direct_deps = repository_names,
            root_module_direct_dev_deps = [],
            facts = facts,
        )
    else:
        return module_ctx.extension_metadata(
            root_module_direct_deps = [],
            root_module_direct_dev_deps = repository_names,
            facts = facts,
        )

kmp = module_extension(
    implementation = _kmp_extension_impl,
    tag_classes = {
        "configure": tag_class(attrs = {
            "platforms": attr.string_list(
                mandatory = True,
                doc = """Platforms to resolve [deps] for, among `wasmjs` and `jvm`. One `kmp_deps_<platform>` repository is
                generated per platform, with the same target names in each, so that a library is addressed by the same name
                whatever the platform: `@kmp_deps_wasmjs//:org_jetbrains_kotlin_kotlin_stdlib`, `@kmp_deps_jvm//:org_jetbrains_kotlin_kotlin_stdlib`.""",
            ),
            "deps": attr.string_list(
                doc = "List of `group:artifact:version` dependencies to resolve.",
            ),
            "repositories": attr.string_list(
                doc = "Maven repository URLs forwarded to the resolver.",
            ),
            "substitutions": attr.string_dict(
                doc = """Maven module substitutions, keyed by group:artifact and resolved to group:artifact:version: the substitute is
                resolved in place of the substituted library, with its own artifacts and dependencies. Substituting a KMP library
                covers its platform-specific variants (`*-jvm`, `*-wasm-js`), which need no key of their own. A library cannot be
                substituted by another version of itself.""",
            ),
            "npm_package_version_overrides": attr.string_dict(
                doc = """Overrides of NPM package versions, keyed by package name.
                Escape hatch to resolve manually the version conflicts between the NPM dependencies declared by different klibs.""",
            ),
        }),
    },
    doc = """
      Kotlin Multiplatform dependency extension.

      Analogous to `bazel-contrib/rules_jvm_external`'s `maven.install` extension, except that it handles Kotlin Multiplatform dependencies.
      It resolves the specified [deps] against the specified [repositories] for each of the specified [platforms],
      substituting dependencies using specified [substitutions] if any.

      Supported Kotlin Multiplatform targets (more will be added later):
      - WasmJS (`wasmjs`)
      - JVM (`jvm`), including JVM-only libraries

      It generates one `kmp_deps_<platform>` repository per platform, with:
      - (WasmJS target) `wasmjs_import` rules (backed by `http_file` rules resolving source jars and klibs), carrying
        the NPM packages declared by the `package.json` embedded in their klib (backed by `http_archive` rules
        resolving npm registry tarballs) in their `npm_packages`
      - (JVM target) `jvm_import` rules (backed by `http_file` rules resolving source jars and jars) wrapped in a
        `java_library` carrying the exported (`exports`) and runtime-only (`runtime_deps`) dependencies of the library
      - `alias` rules to avoid specifying versions in user-space `BUILD.bazel` files, named after the library and after
        its platform-specific variant (`org_jetbrains_kotlin_kotlin_stdlib`, `org_jetbrains_kotlin_kotlin_stdlib_wasm_js`)

      Resolution against private repository will wire `NETRC` and Bazel authentication to allow the Kotlin Multiplatform resolver to query
      these repositories.

      A Bazel credential helper can authenticate the resolver's requests instead, through the `KMP_RESOLVER_CREDENTIAL_HELPER`
      environment variable: `[scope=]path` entries separated by `;`, `%workspace%` standing for the workspace root, with the
      syntax and precedence of Bazel's own `--credential_helper`, for instance in a bazelrc:

          common --repo_env=KMP_RESOLVER_CREDENTIAL_HELPER=%workspace%/tools/credential-helper

      `KMP_RESOLVER_CREDENTIAL_HELPER_TIMEOUT` and `KMP_RESOLVER_CREDENTIAL_HELPER_CACHE_DURATION` are the counterparts
      of Bazel's `--credential_helper_timeout` and `--credential_helper_cache_duration`, with the same duration syntax
      (defaults: `10s` and `30m`).
    """,
)

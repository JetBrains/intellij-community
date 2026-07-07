load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "get_auth")

_RESOLUTION_FACTS_VERSION = "resolution.v27"

_RESOLVER_JAR_VERSION = "0.0.18"
_RESOLVER_JAR_SHA256 = "3c2e02642e69993ad3820b911079ea068ff506079b8434f66d4b838e1430a16d"
_RESOLVER_JAR_URLS = [
    "https://cache-redirector.jetbrains.com/github.com/JetBrains/bazel-kmp-resolver/releases/download/%s/bazel-kmp-resolver-jvm-executable.jar" % _RESOLVER_JAR_VERSION,
]

# See https://github.com/JetBrains/bazel-kmp-resolver/tree/main/testResources for example of production JSONs that could be returned by the resolver
_EMPTY_RESOLUTION_JSON = json.encode({
    "askedCoordinates": [],
    "askedRepositories": [],
    "libraries": {},
})

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

def _materialize_resolution(resolution):
    libraries = _manifest_libraries(resolution)
    target_names = _library_target_names(libraries)
    target_names_by_module = _target_names_by_module(target_names)

    materialized_targets = []
    materialized_target_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _wasmjs_variant(library)
        if variant == None:
            continue

        variant_id = variant["variantId"]
        target_name = _build_target_name(variant_id)
        if target_name in materialized_target_names:
            continue
        materialized_target_names[target_name] = True

        klib = variant["klib"]
        source_jar = variant.get("sourceJar")
        materialized_targets.append({
            "coordinate": library_id,
            "name": target_name,
            "klib": _artifact_label(klib),
            "source_jar": None if source_jar == None else _artifact_label(source_jar),
            "deps": _dependency_labels(library_id, variant, "dependencies", target_names, target_names_by_module),
            "exported_deps": _dependency_labels(library_id, variant, "exportedDependencies", target_names, target_names_by_module),
        })

    return struct(
        aliases = _library_aliases(libraries),
        targets = materialized_targets,
    )

def _wasmjs_variant(library):
    for variant in library["variants"]:
        if variant.get("type") == "wasmjs":
            return variant
    return None

def _manifest_libraries(resolution):
    return resolution["libraries"]

def _library_target_names(libraries):
    target_names = {}
    used_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
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

        variant = _wasmjs_variant(library)
        if variant != None:
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

def _library_aliases(libraries):
    real_names = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _wasmjs_variant(library)
        if variant != None:
            real_names[_build_target_name(variant["variantId"])] = True

    aliases = {}
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _wasmjs_variant(library)
        if variant == None:
            continue
        alias_name = _versionless_target_name(library_id)
        target_name = _build_target_name(variant["variantId"])
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
    artifacts = {}
    libraries = _manifest_libraries(resolution)
    for library_id in sorted(libraries.keys()):
        library = libraries[library_id]
        variant = _wasmjs_variant(library)
        if variant == None:
            continue

        _add_artifact(artifacts, variant["klib"])
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

def _render_build_file(materialized):
    return "\n".join([
        "load(\"@rules_jvm//:wasmjs.bzl\", \"wasmjs_import\")",
        "",
        "package(default_visibility = [\"//visibility:public\"])",
        "",
        _render_targets_block(materialized.targets),
        "",
        _render_aliases_block(materialized.aliases),
        "",
    ])

def _render_targets_block(targets):
    blocks = []
    for target in targets:
        lines = [
            "# %s" % target["coordinate"],
        ]
        lines.extend(_render_wasmjs_import(target))
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
    lines = [
        "wasmjs_import(",
        "    name = %s," % _quote(target["name"]),
        "    klib = %s," % _quote(target["klib"]),
    ]
    if target["source_jar"] != None:
        lines.append("    source_jar = %s," % _quote(target["source_jar"]))
    lines.extend(_render_label_list_attr("deps", target["deps"]))
    lines.extend(_render_label_list_attr("exported_deps", target["exported_deps"]))
    lines.append(")")
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
        return root_tags[0]
    if non_root_tags:
        return non_root_tags[0]
    return struct(
        deps = [],
        repositories = [],
        substitutions = {},
    )

def _resolve_with_facts(module_ctx, config, repository_credentials):
    if not config.deps:
        return _EMPTY_RESOLUTION_JSON

    fact_key = _resolution_fact_key(config)
    if fact_key in module_ctx.facts:
        return module_ctx.facts[fact_key]

    return _resolve_fresh(module_ctx, config, repository_credentials)

def _resolve_fresh(module_ctx, config, repository_credentials):
    resolver_jar = "bazel-kmp-resolver.jar"
    module_ctx.download(
        url = _RESOLVER_JAR_URLS,
        output = resolver_jar,
        sha256 = _RESOLVER_JAR_SHA256,
    )
    java = module_ctx.path(Label("//build:java.cmd"))  # TODO: replace that by making the resolver a standalone binary instead

    resolution_path = "resolution.json"
    module_ctx.file(resolution_path, "")
    args = [
        java,
        "-jar",
        resolver_jar,
        "--output-manifest-file",
        module_ctx.path(resolution_path),
        "--allowed-concurrent-connections=15",
        "--request-timeout-ms=30000",
        "--connect-timeout-ms=30000",
    ]
    for dep in config.deps:
        args.extend(["--coordinate", dep])
    for repository in config.repositories:
        args.extend(["--repository", repository])
    for source_module_id in sorted(config.substitutions.keys()):
        target_coordinate = config.substitutions[source_module_id]
        _validate_maven_module_id(source_module_id)
        _maven_coordinate_parts(target_coordinate)
        args.extend(["--substitution", "%s=%s" % (source_module_id, target_coordinate)])

    if repository_credentials:
        credentials_file = "repository-credentials.json"
        module_ctx.file(credentials_file, json.encode(repository_credentials), executable = False)
        args.extend(["--repository-credentials-file", module_ctx.path(credentials_file)])

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
        config.deps,
        config.repositories,
        _sorted_dict_items(config.substitutions),
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

def _kmp_extension_impl(module_ctx):
    config = _read_configure_tag(module_ctx)
    repository_credentials = []
    if config.deps:
        netrc = module_ctx.getenv("NETRC") or ""
        repository_credentials = _repository_credentials(module_ctx, config.repositories, netrc)
    resolution_json = _resolve_with_facts(module_ctx, config, repository_credentials)
    resolution = json.decode(resolution_json)
    _register_artifact_repositories(resolution)

    repository_name = "kmp_deps"
    _kmp_deps_repository(
        name = repository_name,
        build_file_content = _render_build_file(_materialize_resolution(resolution)),
    )

    facts = {_resolution_fact_key(config): resolution_json} if config.deps else {}
    if module_ctx.root_module_has_non_dev_dependency:
        return module_ctx.extension_metadata(
            root_module_direct_deps = [repository_name],
            root_module_direct_dev_deps = [],
            facts = facts,
        )
    else:
        return module_ctx.extension_metadata(
            root_module_direct_deps = [],
            root_module_direct_dev_deps = [repository_name],
            facts = facts,
        )

kmp = module_extension(
    implementation = _kmp_extension_impl,
    tag_classes = {
        "configure": tag_class(attrs = {
            "deps": attr.string_list(
                doc = "List of `group:artifact:version` dependencies to resolve.",
            ),
            "repositories": attr.string_list(
                doc = "Maven repository URLs forwarded to the resolver.",
            ),
            "substitutions": attr.string_dict(
                doc = "Maven module substitutions, keyed by group:artifact and resolved to group:artifact:version.",
            ),
        }),
    },
    doc = """
      Kotlin Multiplatform dependency extension.

      Analogous to `bazel-contrib/rules_jvm_external`'s `maven.install` extension, except that it handles Kotlin Multiplatform dependencies.
      It resolves the specified [deps] against the specified [repositories], substituting dependencies using specified [substitutions] if any.

      Supported Kotlin Multiplatform targets (more will be added later):
      - WasmJS

      It generates a `kmp_deps` repository with:
      - (WasmJS target) `wasmjs_import` rules (backed by `http_file` rules resolving source jars and klibs)
      - `alias` rules to avoid specifying versions in user-space `BUILD.bazel` files

      Resolution against private repository will wire `NETRC` and Bazel authentication to allow the Kotlin Multiplatform resolver to query
      these repositories.
    """,
)

load("//:rules/binary-wasmjs.bzl", "KtWasmJsBinaryInfo", "wasmjs_binary")
load("//:rules/impl/compile-wasmjs.bzl", "KtWasmJsInfo")

visibility("private")

def wasmjs_test(
        name,
        module,
        module_name,
        kotlinc_opts = None,
        testdata = [],
        configuration_scripts = [],
        npm_packages = {},
        module_runtime_files = [],
        awaited_imports = {},
        test_completion_grace_period_ms = 0,
        size = "medium",
        tags = [],
        visibility = None,
        **kwargs):
    """Runs the kotlin-test suites of a wasmjs_library in a hermetic headless browser.

    Links `module` with wasmjs_binary, assembles a static web root out of symlinks (the
    linked module, `testdata`, `configuration_scripts`, npm packages, `module_runtime_files`),
    and runs it with the //wasmjs-test-harness runner: it generates the index.html test page,
    serves the root over a static server, and drives a chromium headless-shell over the
    DevTools protocol, translating kotlin-test's TeamCity service messages into the Bazel
    JUnit XML report.

    Args:
      name: name of the test target.
      module: label providing KtWasmJsInfo; its klib is the `-Xinclude` linking entry point
        and must contain (or depend on) the kotlin-test tests to run.
      module_name: name of the linked module, used for the emitted file names; cannot be
        derived from KtWasmJsInfo (the provider carries klibs only).
      kotlinc_opts: link-time kotlinc options; should match the options the module was
        compiled with (same contract as wasmjs_binary).
      testdata: files of this package served next to the page: tests fetch them with
        package-relative paths (e.g. `testdata/sample.txt`) and cannot reach outside the
        package.
      configuration_scripts: scripts loaded as classic `<script>` tags in `<head>`, in
        order, before the test module.
      npm_packages: bare import specifier -> npm package files (an http_archive exposing
        the `//:package` filegroup of BUILD.npm.bazel); served under `/node_modules/` and
        resolved through a generated import map. The npm packages carried by the module's
        transitive `KtWasmJsInfo.npm_packages` are served automatically; this attribute is
        only for packages the test page needs beyond them.
      module_runtime_files: files the linked module expects next to its entrypoint (e.g. the
        skiko `./skiko.mjs` glue and its `skiko.wasm`). Served under `/_runtime/` — the linked
        module directory is a single tree artifact that cannot gain siblings — with the page's
        import map remapping the module-adjacent URL of every `.mjs`/`.js` file there, so
        relative imports like `./skiko.mjs` resolve. Basenames must be unique.
      awaited_imports: module_runtime_files basename -> exported member the page awaits, in
        order, before the test entrypoint. For runtime that loads asynchronously but is used
        synchronously by tests: e.g. skiko exposes its skia bindings as lazy stubs and an
        `awaitSkiko` readiness promise; without the await, tests race its wasm instantiation.
      test_completion_grace_period_ms: how long, in milliseconds, the harness still waits for output before
        it calls the run finished. kotlin-test never announces the end of a run, so the harness infers it: the
        grace period is measured from the last console line the page printed, and only counts once no test and
        no suite is still open. Any further output resets it. Raise it for tests whose async gap between two
        suites outlasts it, or the run is declared over early and reports without the suites that never came
        — while still exiting 0. 0 (the default) keeps the harness default of 3000. This is the only tunable
        harness phase: browser setup keeps its harness default, and page load needs no knob because it is
        bounded by the harness deadline derived from Bazel's own `timeout`/`size` (which bounds the run
        overall, and is reserved enough teardown grace to still write the report).
      size: standard test size attribute.
      tags: standard tags attribute.
      visibility: standard visibility attribute.
      **kwargs: any other standard test attributes (timeout, flaky, env, ...).
    """
    test_bin_name = "%s_bin" % name
    binary_kwargs = {} if kotlinc_opts == None else {"kotlinc_opts": kotlinc_opts}
    wasmjs_binary(
        name = test_bin_name,
        module = module,
        module_name = module_name,
        ir_output_name = module_name,
        # Test binaries never pay for wasm-opt (matches the Kotlin Gradle plugin, which
        # builds wasmJs test binaries in development mode).
        optimize = "off",
        tags = tags + ["manual"],
        testonly = True,
        visibility = ["//visibility:private"],
        **binary_kwargs
    )
    _wasmjs_browser_test(
        name = name,
        test_module_entrypoint = test_bin_name,
        module_name = module_name,
        test_data = testdata,
        configuration_scripts = configuration_scripts,
        npm_packages = npm_packages,
        module_runtime_files = module_runtime_files,
        awaited_imports = awaited_imports,
        test_completion_grace_period_ms = test_completion_grace_period_ms,
        size = size,
        tags = tags,
        visibility = visibility,
        **kwargs
    )

def _wasmjs_browser_test_impl(ctx):
    static_prefix = "%s_static" % ctx.label.name
    module_name = ctx.attr.module_name

    # module_name lands in the flagfile (`=`-split option values) and in the generated page.
    _validate_page_path(ctx, module_name)

    dist_link = _linked_module(ctx, static_prefix, module_name)
    config_links, config_paths = _configuration_scripts(ctx, static_prefix)
    testdata_links = _testdata(ctx, static_prefix, module_name)
    npm_links, npm_specifiers = _npm_packages(ctx, static_prefix)
    runtime_links, runtime_basenames = _module_runtime_files(ctx, static_prefix)
    _validate_awaited_imports(ctx, runtime_basenames)

    browser_files = ctx.files._browser

    # The browser command line (see _PLAYWRIGHT_CHROMIUM_FLAGS), one argument per line, read
    # by the runner at test runtime.
    browser_flagfile = ctx.actions.declare_file("%s.browser.flagfile" % ctx.label.name)
    ctx.actions.write(output = browser_flagfile, content = "\n".join(_PLAYWRIGHT_CHROMIUM_FLAGS) + "\n")

    # The runner invocation: a rule cannot inject argv into its own test execution, so the
    # arguments are materialized as `<name>.flagfile` NEXT TO the `<name>` executable — the
    # runner derives that path from the standard TEST_BINARY env var when started bare.
    # Paths inside are rlocation-style (runfiles-root-relative, repo-mapped); everything the
    # runner needs to generate and serve the test page at runtime is passed here.
    flagfile = ctx.actions.declare_file("%s.flagfile" % ctx.label.name)
    flag_args = ctx.actions.args()
    flag_args.set_param_file_format("multiline")
    flag_args.add("--browser-binary=%s" % _rlocation_path(ctx, _browser_executable_path(ctx, browser_files)))
    flag_args.add("--browser-flagfile=%s" % _rlocation_path(ctx, browser_flagfile.short_path))
    flag_args.add("--browser-profile-dir=%s" % _BROWSER_PROFILE_DIR)
    flag_args.add("--static-content-dir=%s" % _rlocation_path(ctx, dist_link.short_path.removesuffix("/%s-js" % module_name)))
    flag_args.add("--entrypoint=%s-js/%s.mjs" % (module_name, module_name))
    flag_args.add_all(config_paths, format_each = "--configuration-script=%s")
    flag_args.add_all(npm_specifiers, format_each = "--npm-package=%s")

    grace_period_ms = ctx.attr.test_completion_grace_period_ms
    if grace_period_ms < 0:
        fail("wasmjs_test test_completion_grace_period_ms expects a positive number of milliseconds, got: %d" % grace_period_ms)
    if grace_period_ms > 0:
        flag_args.add("--test-completion-grace-period-ms=%d" % grace_period_ms)

    # The linked module directory is a single tree artifact, so runtime files cannot sit inside
    # it; they are served under _runtime/ and the page's import map redirects module-adjacent
    # imports (e.g. the linked module's `./skiko.mjs`) there.
    flag_args.add_all([
        "--import-remap=%s-js/%s=_runtime/%s" % (module_name, basename, basename)
        for basename in runtime_basenames
        if basename.endswith(".mjs") or basename.endswith(".js")
    ])
    flag_args.add_all([
        "--awaited-import=_runtime/%s=%s" % (basename, member)
        for basename, member in ctx.attr.awaited_imports.items()
    ])
    ctx.actions.write(output = flagfile, content = flag_args)

    executable = _runner_executable(ctx)
    runfiles = ctx.runfiles(
        files = [flagfile, browser_flagfile, dist_link] +
                config_links + testdata_links + npm_links + runtime_links + browser_files,
    ).merge(ctx.attr._runner[DefaultInfo].default_runfiles).merge_all([
        target[DefaultInfo].default_runfiles
        for target in ctx.attr.test_data + ctx.attr.configuration_scripts + [ctx.attr._browser]
    ])
    return [DefaultInfo(executable = executable, runfiles = runfiles)]

def _linked_module(ctx, static_prefix, module_name):
    # The whole linker output is served verbatim: a single directory symlink.
    dist_link = ctx.actions.declare_directory("%s/%s-js" % (static_prefix, module_name))
    ctx.actions.symlink(output = dist_link, target_file = ctx.attr.test_module_entrypoint[KtWasmJsBinaryInfo].dist_directory)
    return dist_link

def _configuration_scripts(ctx, static_prefix):
    links = []
    paths = []
    seen = {}
    for f in ctx.files.configuration_scripts:
        _validate_page_path(ctx, f.basename)
        if f.basename in seen:
            fail("wasmjs_test configuration_scripts basenames must be unique, got %s twice" % f.basename)
        seen[f.basename] = True
        link = ctx.actions.declare_file("%s/_config/%s" % (static_prefix, f.basename))
        ctx.actions.symlink(output = link, target_file = f)
        links.append(link)
        paths.append("_config/%s" % f.basename)
    return links, paths

def _testdata(ctx, static_prefix, module_name):
    if ctx.label.package == "":
        fail("wasmjs_test targets must not live in a repository root package (the testdata path convention is package-relative)")
    marker = ctx.label.package + "/"
    reserved = ["index.html", "_config", "_runtime", "node_modules", "%s-js" % module_name, "%s.flagfile" % ctx.label.name, "%s.browser.flagfile" % ctx.label.name]
    links = []
    for f in ctx.files.test_data:
        owner = f.owner
        index = f.short_path.find(marker)
        if owner.repo_name != ctx.label.repo_name or owner.package != ctx.label.package or index == -1:
            fail("wasmjs_test testdata must live under the test's package //%s, got %s" % (ctx.label.package, f.short_path))
        relative = f.short_path[index + len(marker):]
        if relative.split("/")[0] in reserved:
            fail("wasmjs_test testdata path %s collides with the reserved static root entry %s" % (relative, relative.split("/")[0]))
        link = ctx.actions.declare_file("%s/%s" % (static_prefix, relative))
        ctx.actions.symlink(output = link, target_file = f)
        links.append(link)
    return links

def _npm_packages(ctx, static_prefix):
    # The npm packages of the module's transitive runtime closure (declared by wasmjs_import/wasmjs_library)
    # are served automatically; the npm_packages attribute only adds packages beyond them.
    transitive_npm_packages = ctx.attr.test_module_entrypoint[KtWasmJsBinaryInfo].npm_packages
    npm_packages = {specifier: entry.files for specifier, entry in transitive_npm_packages.items()}
    for specifier, target in ctx.attr.npm_packages.items():
        transitive_entry = transitive_npm_packages.get(specifier)
        if transitive_entry != None and transitive_entry.label != target.label:
            fail("wasmjs_test npm_packages entry '%s' (%s) conflicts with the npm package of the module's runtime closure (%s)" % (
                specifier,
                target.label,
                transitive_entry.label,
            ))
        npm_packages[specifier] = target[DefaultInfo].files

    links = []
    specifiers = []
    for specifier in sorted(npm_packages.keys()):
        files = npm_packages[specifier]
        _validate_page_path(ctx, specifier)
        if specifier.startswith("/") or ".." in specifier:
            fail("wasmjs_test npm_packages specifier is not a bare specifier: %s" % specifier)
        has_package_json = False
        for f in files.to_list():
            relative = _external_repo_relative_path(f)
            link = ctx.actions.declare_file("%s/node_modules/%s/%s" % (static_prefix, specifier, relative))
            ctx.actions.symlink(output = link, target_file = f)
            links.append(link)
            if relative == "package.json":
                has_package_json = True
        if not has_package_json:
            fail("wasmjs_test npm_packages entry %s has no package.json at its root (expose the package with BUILD.npm.bazel's `package` filegroup)" % specifier)
        specifiers.append(specifier)
    return links, specifiers

def _module_runtime_files(ctx, static_prefix):
    links = []
    basenames = []
    for f in ctx.files.module_runtime_files:
        _validate_page_path(ctx, f.basename)
        if f.basename in basenames:
            fail("wasmjs_test module_runtime_files basenames must be unique, got %s twice" % f.basename)
        basenames.append(f.basename)
        link = ctx.actions.declare_file("%s/_runtime/%s" % (static_prefix, f.basename))
        ctx.actions.symlink(output = link, target_file = f)
        links.append(link)
    return links, basenames

def _validate_awaited_imports(ctx, runtime_basenames):
    for basename, member in ctx.attr.awaited_imports.items():
        if basename not in runtime_basenames:
            fail("wasmjs_test awaited_imports key %s is not a module_runtime_files basename" % basename)

        # The harness enforces the strict JS-identifier shape; this only keeps the flagfile sane.
        _validate_page_path(ctx, member)

def _external_repo_relative_path(f):
    if f.short_path.startswith("../"):
        return f.short_path[len("../"):].split("/", 1)[1]
    return f.short_path

# Equivalent of a `rlocationpath` call but that works on a String (`rlocationpath` only can work on Label)
def _rlocation_path(ctx, short_path):
    """Turns a runfiles `short_path` into an rlocation path (rooted at the runfiles root)."""
    if short_path.startswith("../"):
        return short_path[len("../"):]
    return "%s/%s" % (ctx.workspace_name, short_path)

# Playwright chromium-headless-shell helpers: the browser tree artifact is opaque at analysis
# time (the archive is unzipped by a build action), but the playwright archive layout is fixed
# per OS, so the executable path inside the tree is known here — the runner receives ready
# values through the flagfile and carries no browser knowledge.
_PLAYWRIGHT_CHROMIUM_EXECUTABLES = {
    "mac": "chrome-mac/headless_shell",
    "linux": "chrome-linux/headless_shell",
    "windows": "chrome-win/headless_shell.exe",
}

# The browser command line, one argument per line of `<name>.browser.flagfile`;
# ${BROWSER_PROFILE_DIR} is substituted by the runner with the resolved --browser-profile-dir.
_PLAYWRIGHT_CHROMIUM_FLAGS = [
    # headless_shell is headless-only, but drivers conventionally still pass the flag
    "--headless",
    "--remote-debugging-port=0",
    "--user-data-dir=${BROWSER_PROFILE_DIR}",
    "--no-sandbox",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--no-first-run",
    "--no-default-browser-check",
    "--mute-audio",
    "--hide-scrollbars",
    "--disable-background-timer-throttling",
    "--disable-backgrounding-occluded-windows",
    "--disable-renderer-backgrounding",
    # Pin a valid locale so navigator.language(s) is never empty. On a CI host with no locale
    # configured (LANG unset) headless chromium reports an empty language, and Compose's
    # JsLocale.current then calls `new Intl.Locale("")`, which throws
    # `RangeError: Incorrect locale information provided`. --lang drives navigator.language,
    # --accept-lang drives navigator.languages.
    "--lang=en-US",
    "--accept-lang=en-US",
    "about:blank",
]

# Relative: the runner resolves it under TEST_TMPDIR at test runtime.
_BROWSER_PROFILE_DIR = "browser-profile"

def _browser_executable_path(ctx, browser_files):
    if len(browser_files) != 1:
        fail("expected the browser target to provide a single tree artifact, got %d files" % len(browser_files))
    return "%s/%s" % (browser_files[0].short_path, _PLAYWRIGHT_CHROMIUM_EXECUTABLES[_target_os(ctx)])

def _target_os(ctx):
    for os, attr in {"mac": ctx.attr._macos_constraint, "linux": ctx.attr._linux_constraint, "windows": ctx.attr._windows_constraint}.items():
        if ctx.target_platform_has_constraint(attr[platform_common.ConstraintValueInfo]):
            return os
    fail("wasmjs_test supports macOS, Linux and Windows target platforms only")

def _runner_executable(ctx):
    windows_constraint = ctx.attr._windows_constraint[platform_common.ConstraintValueInfo]
    extension = ".exe" if ctx.target_platform_has_constraint(windows_constraint) else ""
    executable = ctx.actions.declare_file(ctx.label.name + extension)

    # The runner java_binary launcher relocates fine when symlinked: at test runtime it
    # locates its runfiles through $TEST_SRCDIR/$RUNFILES_DIR, which Bazel always sets.
    ctx.actions.symlink(
        output = executable,
        target_file = ctx.attr._runner[DefaultInfo].files_to_run.executable,
        is_executable = True,
    )
    return executable

def _validate_page_path(ctx, value):
    for forbidden in ['"', "'", "<", ">", "\n", "\r", " ", "="]:
        if forbidden in value:
            fail("%s: not safe to embed in the test page: %r" % (ctx.label, value))

_wasmjs_browser_test = rule(
    doc = """Test rule running a linked WasmJS test module in a headless browser via the
      //wasmjs-test-harness runner. The runner is started without arguments and locates its
      flagfile as `$TEST_BINARY.flagfile` (written next to the test executable by this rule).""",
    attrs = {
        "test_module_entrypoint": attr.label(
            mandatory = True,
            providers = [[KtWasmJsBinaryInfo]],
            doc = "The wasmjs_binary whose dist directory contains the test module entrypoint .mjs.",
        ),
        "module_name": attr.string(
            mandatory = True,
            doc = "Name of the linked module: the entrypoint is <module_name>-js/<module_name>.mjs.",
        ),
        "test_data": attr.label_list(
            allow_files = True,
            doc = "Files of this package served with package-relative paths next to the page.",
        ),
        "configuration_scripts": attr.label_list(
            allow_files = True,
            doc = "Scripts loaded as <script> tags in <head>, in order, before the test module.",
        ),
        "npm_packages": attr.string_keyed_label_dict(
            allow_files = True,
            doc = """Bare import specifier -> npm package files served under /node_modules/ and mapped in the
              generated import map, in addition to the npm packages of the module's transitive runtime closure.""",
        ),
        "module_runtime_files": attr.label_list(
            allow_files = True,
            doc = "Files the linked module expects next to its entrypoint (e.g. skiko.mjs/skiko.wasm); served under /_runtime/ with import-map remapping of the module-adjacent URLs.",
        ),
        "awaited_imports": attr.string_dict(
            doc = "module_runtime_files basename -> exported member the page awaits, in order, before the test entrypoint (e.g. skiko.mjs -> awaitSkiko).",
        ),
        "test_completion_grace_period_ms": attr.int(
            doc = """How long, in milliseconds, the harness still waits for output before calling the run
              finished: measured from the last console line, and only once no test and no suite is still open.
              0 leaves the phase at the harness default. The only tunable harness phase: browser setup keeps
              its harness default, and page load is bounded by the deadline derived from TEST_TIMEOUT.""",
        ),
        "_runner": attr.label(
            default = Label("//wasmjs-test-harness"),
            executable = True,
            # The runner IS the test executable: it runs on the test host (target
            # configuration), not in the execution configuration of build actions.
            cfg = "target",
        ),
        "_browser": attr.label(
            default = Label("//wasmjs-test-harness:chromium-headless-shell"),
            allow_files = True,
        ),
        "_windows_constraint": attr.label(
            default = Label("@platforms//os:windows"),
        ),
        "_macos_constraint": attr.label(
            default = Label("@platforms//os:osx"),
        ),
        "_linux_constraint": attr.label(
            default = Label("@platforms//os:linux"),
        ),
    },
    implementation = _wasmjs_browser_test_impl,
    test = True,
)

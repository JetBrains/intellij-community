"""Checks the product content rows, the derived descriptor rows, and the writer arguments of the product descriptor rules.

The walk cases state the rows that `buildContentBlocksAndChainMapping` gives for the same sets. A failure case runs the
helper in a probe rule, because only an analysis test can expect a failure.
"""

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts", "unittest")
load("@bazel_skylib//rules:write_file.bzl", "write_file")
load(":dev_dist_embedded_product_descriptor.bzl", "dev_dist_embedded_product_descriptor", "dev_dist_embedded_product_descriptor_target_name")
load(":dev_dist_product_content.bzl", "derived_descriptor_rows", "product_content_attributes", "product_content_rows")
load(":dev_dist_product_descriptor.bzl", "dev_dist_product_descriptor")

_A = "intellij.moduleSets.a"
_B = "intellij.moduleSets.b"
_C = "intellij.moduleSets.c"
_D = "intellij.moduleSets.d"
_E = "intellij.moduleSets.e"
_G = "intellij.moduleSets.g"
_DECLARED = "intellij.moduleSets.declared"
_EMPTY = "intellij.moduleSets.empty"

# `a` nests `b` and `c`, and `b` nests `d`. `e` nests `b` too. `empty` has no own member. `declared` states a loading
# rule of its own, and `g` nests it.
_TABLE = {
    _A: struct(
        modules = ["a1", "a2"],
        nested = [_B, _C],
        alias = "com.intellij.modules.a",
        required_if_available = {"a2": "a1"},
    ),
    _B: struct(modules = ["b1", "b2"], nested = [_D]),
    _C: struct(modules = ["c1"], nested = []),
    _D: struct(modules = ["d1"], nested = [], alias = "com.intellij.modules.d"),
    _E: struct(modules = ["e1"], nested = [_B]),
    _EMPTY: struct(modules = [], nested = [_C], alias = "com.intellij.modules.empty"),
    _DECLARED: struct(modules = ["f1", "f2"], nested = [], loading = {"f2": "embedded"}),
    _G: struct(modules = ["g1"], nested = [_DECLARED]),
}

def _rows(module_sets, **kwargs):
    return product_content_rows(module_sets = module_sets, module_set_table = _TABLE, **kwargs)

def _walk_order_test_impl(ctx):
    env = unittest.begin(ctx)
    content = _rows([_A])
    asserts.equals(env, ["a1", "a2;required-if-available=a1", "b1", "b2", "d1", "c1"], content.content_rows)
    asserts.equals(env, ["a1", "a2", "b1", "b2", "d1", "c1"], content.members)
    asserts.equals(env, [], content.additional_rows)

    # A set without an own member yields no row, and its nested sets still do.
    asserts.equals(env, ["c1"], _rows([_EMPTY]).content_rows)
    return unittest.end(env)

_walk_order_test = unittest.make(_walk_order_test_impl)

def _set_once_test_impl(ctx):
    env = unittest.begin(ctx)

    # `a` and `e` both nest `b`. The walk processes `b` and its nested `d` once, under `a`, so the alias of `d` is there once.
    content = _rows([_A, _E])
    asserts.equals(env, ["a1", "a2", "b1", "b2", "d1", "c1", "e1"], content.members)
    asserts.equals(env, ["com.intellij.modules.a", "com.intellij.modules.d"], content.aliases)
    return unittest.end(env)

_set_once_test = unittest.make(_set_once_test_impl)

def _top_level_overrides_test_impl(ctx):
    env = unittest.begin(ctx)

    # `c` is a top-level set first, so its override applies. Nesting under `a` later changes nothing.
    content = _rows([_C, _A], loading_overrides = {"a1": "required", "c1": "embedded"})
    asserts.equals(
        env,
        ["c1;loading=embedded", "a1;loading=required", "a2;required-if-available=a1", "b1", "b2", "d1"],
        content.content_rows,
    )

    # The default loading gets no `loading` part, also as an override of a declared rule.
    asserts.equals(env, ["f1", "f2"], _rows([_DECLARED], loading_overrides = {"f2": "optional"}).content_rows)
    return unittest.end(env)

_top_level_overrides_test = unittest.make(_top_level_overrides_test_impl)

def _quirk_test_impl(ctx):
    env = unittest.begin(ctx)

    # `b` is reached by nesting first. At top level, its override updates the earlier row in place.
    asserts.equals(
        env,
        ["a1", "a2;required-if-available=a1", "b1", "b2;loading=required", "d1", "c1"],
        _rows([_A, _B], loading_overrides = {"b2": "required"}).content_rows,
    )

    # An earlier row of `declared` carries a non-default loading, so the override does not apply.
    asserts.equals(
        env,
        ["g1", "f1", "f2;loading=embedded"],
        _rows([_G, _DECLARED], loading_overrides = {"f1": "required"}).content_rows,
    )
    return unittest.end(env)

_quirk_test = unittest.make(_quirk_test_impl)

def _aliases_test_impl(ctx):
    env = unittest.begin(ctx)
    content = _rows([_A, _EMPTY], aliases = ["com.intellij.modules.zeta", "com.intellij.modules.b"])
    asserts.equals(
        env,
        [
            "com.intellij.modules.a",
            "com.intellij.modules.b",
            "com.intellij.modules.d",
            "com.intellij.modules.empty",
            "com.intellij.modules.zeta",
        ],
        content.aliases,
    )
    return unittest.end(env)

_aliases_test = unittest.make(_aliases_test_impl)

def _additional_rows_test_impl(ctx):
    env = unittest.begin(ctx)
    content = _rows(
        [],
        content_modules = ["x", "y", "z", "w"],
        private_content_modules = ["y"],
        content_module_loading = {"x": "required", "y": "embedded", "w": "optional"},
        content_module_required_if_available = {"z": "x"},
    )
    asserts.equals(env, [], content.content_rows)
    asserts.equals(env, ["x;loading=required", "y;private;loading=embedded", "z;required-if-available=x", "w"], content.additional_rows)
    asserts.equals(env, ["x", "y", "z", "w"], content.additional)
    return unittest.end(env)

_additional_rows_test = unittest.make(_additional_rows_test_impl)

def _derived_descriptors_test_impl(ctx):
    env = unittest.begin(ctx)
    index = {
        "a1": "//a:a1.xml",
        "a2": "//a:a2.xml",
        "refused": "//r:refused.xml",
        "x": "//x:x.xml",
    }
    explicit = {
        "//a:a1.xml": "a1.xml",
        "//other:a2.xml": "a2.xml",
        "//m:META-INF/include.xml": "META-INF/include.xml",
    }

    # `a1` wins by label and `a2` by load path. `b1` is not in the index, and `refused` is refused.
    asserts.equals(
        env,
        {
            "//a:a1.xml": "a1.xml",
            "//m:META-INF/include.xml": "META-INF/include.xml",
            "//other:a2.xml": "a2.xml",
            "//x:x.xml": "x.xml",
        },
        derived_descriptor_rows(["a1", "a2", "b1", "refused", "x"], index, explicit, ["refused"]),
    )
    return unittest.end(env)

_derived_descriptors_test = unittest.make(_derived_descriptors_test_impl)

def _attributes(**kwargs):
    arguments = {
        "caller": "probe",
        "source": None,
        "aliases": [],
        "includes": {},
        "module_sets": [],
        "module_set_table": {},
        "loading_overrides": {},
        "content_modules": [],
        "private_content_modules": [],
        "content_module_loading": {},
        "content_module_required_if_available": {},
        "descriptor_index": {},
        "descriptors": {},
    }
    arguments.update(kwargs)
    return product_content_attributes(**arguments)

def _attributes_test_impl(ctx):
    env = unittest.begin(ctx)

    # The `source` form passes `descriptors` as it is, in its order.
    explicit = {"//z:z.xml": "z.xml", "//a:a.xml": "a.xml"}
    attributes = _attributes(source = "product.xml", descriptors = explicit, descriptor_index = {"x": "//x:x.xml"})
    asserts.equals(env, {"source": "product.xml", "descriptors": explicit}, attributes)
    asserts.equals(env, ["//z:z.xml", "//a:a.xml"], list(attributes["descriptors"]))

    attributes = _attributes(
        includes = {"/META-INF/optional.xml": "optional", "/META-INF/required.xml": "required"},
        module_sets = [_C],
        module_set_table = _TABLE,
        content_modules = ["x"],
        descriptor_index = {"c1": "//c:c1.xml", "x": "//x:x.xml"},
    )
    asserts.equals(
        env,
        {
            "aliases": [],
            "includes": ["optional=/META-INF/optional.xml", "required=/META-INF/required.xml"],
            "content_rows": ["c1"],
            "additional_rows": ["x"],
            "descriptors": {"//c:c1.xml": "c1.xml", "//x:x.xml": "x.xml"},
        },
        attributes,
    )
    return unittest.end(env)

_attributes_test = unittest.make(_attributes_test_impl)

# The failure cases. Each one calls the helper with data that it must refuse.
_BROKEN_TABLE = {_A: struct(modules = ["a1"], nested = ["intellij.moduleSets.missing"])}

# `c` and `shared` both name `c1`, and `d` and `alias` both declare the alias of `d`.
_DUPLICATE_TABLE = _TABLE | {
    "intellij.moduleSets.alias": struct(modules = ["h1"], nested = [], alias = "com.intellij.modules.d"),
    "intellij.moduleSets.shared": struct(modules = ["c1"], nested = []),
}

def _content_failure_probe_impl(ctx):
    case = ctx.attr.case
    if case == "unknown_top_level_set":
        _rows(["intellij.moduleSets.missing"])
    elif case == "unknown_nested_set":
        product_content_rows(module_sets = [_A], module_set_table = _BROKEN_TABLE)
    elif case == "product_alias_of_set":
        _rows([_A], aliases = ["com.intellij.modules.a"])
    elif case == "alias_of_two_sets":
        product_content_rows(module_sets = [_D, "intellij.moduleSets.alias"], module_set_table = _DUPLICATE_TABLE)
    elif case == "module_of_two_sets":
        product_content_rows(module_sets = [_A, "intellij.moduleSets.shared"], module_set_table = _DUPLICATE_TABLE)
    elif case == "set_member_as_additional_module":
        _rows([_C], content_modules = ["x", "c1"])
    elif case == "additional_module_twice":
        _rows([], content_modules = ["x", "x"])
    elif case == "override_of_nested_member":
        _rows([_A], loading_overrides = {"b1": "required"})
    elif case == "both_forms":
        _attributes(source = "product.xml", module_sets = [_C], module_set_table = _TABLE)
    elif case == "no_form":
        _attributes()
    elif case == "include_kind":
        _attributes(includes = {"META-INF/a.xml": "fallback"})
    else:
        fail("unknown probe case '%s'" % case)
    return []

_content_failure_probe = rule(
    implementation = _content_failure_probe_impl,
    attrs = {"case": attr.string(mandatory = True)},
)

def _content_failure_test_impl(ctx):
    env = analysistest.begin(ctx)
    asserts.expect_failure(env, ctx.attr.message)
    return analysistest.end(env)

_content_failure_test = analysistest.make(
    _content_failure_test_impl,
    expect_failure = True,
    attrs = {"message": attr.string(mandatory = True)},
)

_FAILURES = {
    "additional_module_twice": "the module 'x' of content_modules is already in content_modules",
    "alias_of_two_sets": "the alias 'com.intellij.modules.d' of the module set 'intellij.moduleSets.alias' is already declared by the module set 'intellij.moduleSets.d'",
    "module_of_two_sets": "the module 'c1' of the module set 'intellij.moduleSets.shared' is already in the module set 'intellij.moduleSets.c'",
    "product_alias_of_set": "the alias 'com.intellij.modules.a' of the module set 'intellij.moduleSets.a' is already declared by the product",
    "set_member_as_additional_module": "the module 'c1' of content_modules is already in the module set 'intellij.moduleSets.c'",
    "both_forms": "probe requires exactly one of `source` and the content attributes",
    "include_kind": "probe: the include 'META-INF/a.xml' has the kind 'fallback'",
    "no_form": "probe requires exactly one of `source` and the content attributes",
    "override_of_nested_member": "loading_overrides names 'b1', which is not an own member of a set in module_sets",
    "unknown_nested_set": "the module set 'intellij.moduleSets.a' names the module set 'intellij.moduleSets.missing', which the module-set table does not have",
    "unknown_top_level_set": "module_sets names the module set 'intellij.moduleSets.missing', which the module-set table does not have",
}

def _writer_arguments(env, mnemonic):
    actions = [action for action in analysistest.target_actions(env) if action.mnemonic == mnemonic]
    if len(actions) != 1:
        fail("expected one %s action, got %d" % (mnemonic, len(actions)))
    return actions[0].argv[1:]

def _load_paths(arguments, flag):
    """The load paths of the `--descriptor=<load path>=<file>` arguments, in order."""
    return [argument.removeprefix(flag).partition("=")[0] for argument in arguments if argument.startswith(flag)]

def _stripped(arguments):
    """The arguments without the ones that name a file path, which depends on the configuration."""
    return [
        argument
        for argument in arguments
        if not argument.startswith(("--out=", "--source=", "--descriptor=", "--plugin-classpath-prefix=", "--classpath-descriptor="))
    ]

def _product_content_form_test_impl(ctx):
    env = analysistest.begin(ctx)
    arguments = _writer_arguments(env, "DevDistProductDescriptor")
    asserts.equals(env, "--product-descriptor", arguments[0])
    asserts.true(env, arguments[1].startswith("--out="), arguments[1])
    asserts.equals(env, [
        "--product-descriptor",
        "--main-module=intellij.fixture.product",
        "--alias=com.intellij.modules.a",
        "--alias=com.intellij.modules.d",
        "--alias=com.intellij.modules.fixture",
        "--include=optional=/META-INF/optional.xml",
        "--include=required=/META-INF/required.xml",
        "--content-module=a1",
        "--content-module=a2;required-if-available=a1",
        "--content-module=b1",
        "--content-module=b2;loading=required",
        "--content-module=d1",
        "--content-module=c1",
        "--additional-module=intellij.fixture.extra;loading=embedded",
        "--additional-module=intellij.fixture.private;private",
        "--additional-module=intellij.fixture.refused",
        "--refused-content-module=intellij.fixture.refused",
    ], _stripped(arguments))

    # The index answers `a1`, `c1` and two additional modules. The explicit row wins for `a1`, and `refused` gets no row.
    # The rows are sorted by label.
    asserts.equals(
        env,
        ["c1.xml", "a1.xml", "intellij.fixture.extra.xml", "META-INF/required.xml"],
        _load_paths(arguments, "--descriptor="),
    )
    asserts.false(env, [argument for argument in arguments if argument.startswith("--source=")])
    return analysistest.end(env)

_product_content_form_test = analysistest.make(_product_content_form_test_impl)

def _product_source_form_test_impl(ctx):
    env = analysistest.begin(ctx)
    arguments = _writer_arguments(env, "DevDistProductDescriptor")
    asserts.equals(env, "--product-descriptor", arguments[0])
    asserts.true(env, arguments[1].startswith("--out="), arguments[1])
    asserts.true(env, arguments[2].startswith("--source=") and arguments[2].endswith("_source.xml"), arguments[2])
    asserts.equals(env, "--main-module=intellij.fixture.product", arguments[3])
    asserts.equals(env, ["b.xml", "a.xml"], _load_paths(arguments, "--descriptor="))
    asserts.equals(env, ["--product-descriptor", "--main-module=intellij.fixture.product"], _stripped(arguments))
    return analysistest.end(env)

_product_source_form_test = analysistest.make(_product_source_form_test_impl)

def _embedded_content_form_test_impl(ctx):
    env = analysistest.begin(ctx)
    arguments = _writer_arguments(env, "DevDistEmbeddedProductDescriptor")
    asserts.equals(env, "--embedded-product", arguments[0])
    asserts.true(env, arguments[1].startswith("--out="), arguments[1])
    asserts.equals(env, [
        "--embedded-product",
        "--alias=com.intellij.modules.fixture",
        "--include=required=/META-INF/required.xml",
        "--content-module=c1",
        "--additional-module=intellij.fixture.extra;loading=embedded",
        "--separate-jar=c1",
    ], _stripped(arguments))
    asserts.equals(env, ["c1.xml", "META-INF/required.xml"], _load_paths(arguments, "--descriptor="))
    return analysistest.end(env)

_embedded_content_form_test = analysistest.make(_embedded_content_form_test_impl)

def _fixture_descriptor(name):
    write_file(
        name = name,
        out = name + ".xml",
        content = ["<idea-plugin/>", ""],
        testonly = True,
        tags = ["manual"],
    )
    return ":" + name

def _rule_tests(name):
    tests = []
    explicit_a1 = _fixture_descriptor(name + "_explicit_a1")
    required_include = _fixture_descriptor(name + "_required_include")
    index = {
        "a1": _fixture_descriptor(name + "_a1"),
        "c1": _fixture_descriptor(name + "_c1"),
        "intellij.fixture.extra": _fixture_descriptor(name + "_extra"),
        "intellij.fixture.refused": _fixture_descriptor(name + "_refused"),
    }

    content_form = name + "_content_form"
    dev_dist_product_descriptor(
        name = content_form,
        testonly = True,
        main_module = "intellij.fixture.product",
        aliases = ["com.intellij.modules.fixture"],
        includes = {"/META-INF/optional.xml": "optional", "/META-INF/required.xml": "required"},
        module_sets = [_A, _B],
        module_set_table = _TABLE,
        loading_overrides = {"b2": "required"},
        content_modules = ["intellij.fixture.extra", "intellij.fixture.private", "intellij.fixture.refused"],
        private_content_modules = ["intellij.fixture.private"],
        content_module_loading = {"intellij.fixture.extra": "embedded"},
        descriptor_index = index,
        descriptors = {explicit_a1: "a1.xml", required_include: "META-INF/required.xml"},
        refused_content_modules = ["intellij.fixture.refused"],
    )
    tests.append(content_form + "_test")
    _product_content_form_test(name = tests[-1], target_under_test = ":" + content_form)

    source_form = name + "_source_form"
    dev_dist_product_descriptor(
        name = source_form,
        testonly = True,
        main_module = "intellij.fixture.product",
        source = _fixture_descriptor(name + "_source"),
        descriptor_index = index,
        descriptors = {_fixture_descriptor(name + "_b"): "b.xml", _fixture_descriptor(name + "_a"): "a.xml"},
    )
    tests.append(source_form + "_test")
    _product_source_form_test(name = tests[-1], target_under_test = ":" + source_form)

    embedded_main_module = name + "_embedded"
    dev_dist_embedded_product_descriptor(
        testonly = True,
        main_module = embedded_main_module,
        aliases = ["com.intellij.modules.fixture"],
        includes = {"/META-INF/required.xml": "required"},
        module_sets = [_C],
        module_set_table = _TABLE,
        content_modules = ["intellij.fixture.extra"],
        content_module_loading = {"intellij.fixture.extra": "embedded"},
        descriptor_index = {"c1": index["c1"]},
        descriptors = {required_include: "META-INF/required.xml"},
        separate_jar = ["c1"],
    )
    tests.append(embedded_main_module + "_test")
    _embedded_content_form_test(
        name = tests[-1],
        target_under_test = ":" + dev_dist_embedded_product_descriptor_target_name(embedded_main_module),
    )
    return tests

def dev_dist_product_content_test_suite(name):
    """Declares the product content tests.

    Args:
        name: the name of the test suite.
    """
    unittest.suite(
        name + "_rows",
        _walk_order_test,
        _set_once_test,
        _top_level_overrides_test,
        _quirk_test,
        _aliases_test,
        _additional_rows_test,
        _derived_descriptors_test,
        _attributes_test,
    )
    tests = [name + "_rows"]
    for case, message in _FAILURES.items():
        probe = name + "_" + case
        _content_failure_probe(name = probe, case = case, tags = ["manual"])
        tests.append(probe + "_test")
        _content_failure_test(name = tests[-1], target_under_test = ":" + probe, message = message)
    tests.extend(_rule_tests(name))
    native.test_suite(name = name, tests = tests)

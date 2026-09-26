load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load(":intellij_add_opens.bzl", "generate_intellij_add_opens_bzl", "parse_intellij_add_opens")

def _parse_test_impl(ctx):
    env = unittest.begin(ctx)
    content = "--add-opens=java.base/java.lang=ALL-UNNAMED\n--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
    asserts.equals(env, ["java.base/java.lang", "jdk.jdi/com.sun.tools.jdi"], parse_intellij_add_opens(content))
    return unittest.end(env)

parse_test = unittest.make(_parse_test_impl)

def _generate_test_impl(ctx):
    env = unittest.begin(ctx)
    content = generate_intellij_add_opens_bzl(["java.base/java.lang", "jdk.jdi/com.sun.tools.jdi"])
    asserts.true(env, content.endswith('INTELLIJ_ADD_OPENS = [\n    "java.base/java.lang",\n    "jdk.jdi/com.sun.tools.jdi",\n]\n'))
    return unittest.end(env)

generate_test = unittest.make(_generate_test_impl)

def intellij_add_opens_test_suite(name):
    unittest.suite(name, parse_test, generate_test)

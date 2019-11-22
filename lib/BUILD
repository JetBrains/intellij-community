# managed by go/iml_to_build
java_import(
    name = "jsr305",
    jars = ["jsr305.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "eawtstub",
    jars = ["eawtstub.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "microba",
    jars = ["microba.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "ecj-4.10",
    jars = ["ecj-4.10.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "commons-httpclient-3.1-patched",
    jars = ["commons-httpclient-3.1-patched.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "markdownj-core-0.4.2-SNAPSHOT",
    jars = ["markdownj-core-0.4.2-SNAPSHOT.jar"],
    visibility = ["//visibility:public"],
)

exports_files(["src/trove4j_src.jar"])

# TODO: When using ant to package all the libraries are included, and if they are not present
# there is no error. We will need to revisit both, and acually make sure we only bundle what
# we need.
filegroup(
    name = "all-libs",
    srcs = ["//tools/idea/lib/ant:ant-all"] + glob(["**"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "wadl-core",
    jars = ["wadl-core.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "jshell-frontend",
    jars = ["jshell-frontend.jar"],
    visibility = ["//visibility:public"],
)

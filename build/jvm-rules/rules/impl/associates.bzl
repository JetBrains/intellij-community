# Copyright 2020 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
load("@rules_java//java:defs.bzl", "JavaInfo")
load("@rules_kotlin//kotlin/internal:defs.bzl", "KtJvmInfo")

visibility("private")

def get_associates(ctx):
    """Creates a struct of associates meta data"""

    associates = ctx.attr.associates
    if len(associates) == 0:
        return struct(
            targets = [],
            module_name = _derive_module_name(ctx),
            jars = [],
        )
    elif ctx.attr.module_name:
        fail("if associates have been set then module_name cannot be provided")
    else:
        jars = [depset([it], transitive = it[KtJvmInfo].module_jars) for it in associates]
        # use dictionary for deduplication
        module_names = {it[KtJvmInfo].module_name: None for it in associates}
        if len(module_names) > 1:
            fail("Dependencies from several different kotlin modules cannot be associated. " +
                 "Associates can see each other's \"internal\" members, and so must only be " +
                 "used with other targets in the same module: \n%s" % module_names.keys())
        if len(module_names) < 1:
            # This should be impossible
            fail("Error in rules - a KtJvmInfo was found which did not have a module_name")
        return struct(
            targets = associates,
            jars = jars,
            module_name = module_names.keys()[0],
        )

def _derive_module_name(ctx):
    """Gets the `module_name` attribute if it's set in the ctx, otherwise derive a unique module name using the elements
    found in the label."""
    module_name = ctx.attr.module_name
    if module_name == "":
        return (ctx.label.package.lstrip("/").replace("/", "_") + "-" + ctx.label.name.replace("/", "_"))
    return module_name

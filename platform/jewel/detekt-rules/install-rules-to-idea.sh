#!/bin/bash

set -e -u -o pipefail

DETEKT_PLUGIN_TARGETS="
//platform/jewel/detekt-plugin:detekt-plugin-binary_deploy.jar
//libraries/detekt-compose-rules:detekt-compose-rules-binary_deploy.jar
"

dir="$(cd "$(dirname "$0")"; pwd)"

echo "### Building targets and installing to $dir"
./bazel.cmd build $DETEKT_PLUGIN_TARGETS
for target in $DETEKT_PLUGIN_TARGETS; do
    install -v -m 0644 "out/$(./bazel.cmd --quiet cquery "$target" --output files)" "$dir/"
done

echo
echo "### Installing settings file for IntelliJ IDEA to .idea/detekt.xml"
cp -v "$dir/detekt.xml" ".idea/detekt.xml"

#!/bin/bash

set -eu -o pipefail

root="$1"
build_target_name="$2"
shift
shift

# See java_stub_template.txt on how bazel java wrapper works
args=()
for arg in "$@"; do
  if [ "$arg" == "--debug" ]; then
    args+=("--debug")
  else
    args+=("--jvm_flag=$arg")
  fi
done

cd "$root"
"$root/bazel.cmd" build //... --disk_cache=
#"$root/bazel.cmd" build //... # populates the disk cache and incremental compilation caches
#"$root/bazel.cmd" clean # cleaning current exec-root to wipe incremental compilation caches out
#"$root/bazel.cmd" build //... # reuses previously built artifacts from the disk cache
#rm -rf "$HOME/.cache/JetBrains/monorepo-bazel-cache"
if [ ${#args[@]} -eq 0 ]; then
  exec /bin/bash "$root/bazel.cmd" run "$build_target_name" --
else
  exec /bin/bash "$root/bazel.cmd" run "$build_target_name" -- "${args[@]}"
fi

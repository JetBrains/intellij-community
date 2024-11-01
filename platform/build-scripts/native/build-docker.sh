#!/bin/bash

work_dir=$(mktemp -d --suffix=_intellij_tools)
if [[ ! "$work_dir" || ! -d "$work_dir" ]]; then
  echo "Could not create temp dir"
  exit 1
fi

function cleanup {
  rm -rf "$work_dir"
  echo "Deleted temp working directory $work_dir"
}

trap cleanup EXIT

script_dir=$(realpath $(dirname "$0"))
top=$(realpath "$(dirname "$0")/../../../../..")

cp $script_dir/Dockerfile $work_dir/.
mkdir -p $work_dir/cargo/XPlatLauncher
cp $top/tools/idea/native/XPlatLauncher/Cargo.toml $work_dir/cargo/XPlatLauncher/
cp $top/tools/idea/native/XPlatLauncher/Cargo.lock $work_dir/cargo/XPlatLauncher/

mkdir -p $work_dir/cargo/restarter
cp $top/tools/idea/native/restarter/Cargo.toml $work_dir/cargo/restarter/
cp $top/tools/idea/native/restarter/Cargo.lock $work_dir/cargo/restarter/

(
  cd $work_dir
  gcloud builds submit \
  --project google.com:android-studio-alphasource \
  --tag gcr.io/google.com/android-studio-alphasource/intellij_native
)

#!/bin/bash
set -eu

work_dir=$(mktemp -d --suffix=_intellij_tools)
if [[ ! "$work_dir" || ! -d "$work_dir" ]]; then
  echo "Could not create temp dir"
  exit 1
fi

function cleanup {
  sudo chown -R $(id -u):$(id -g) $work_dir
  rm -rf "$work_dir"
  echo "Deleted temp working directory $work_dir"
}

trap cleanup EXIT

script_dir=$(realpath $(dirname "$0"))
top=$(realpath "$(dirname "$0")/../../../../..")

cp $script_dir/Dockerfile $work_dir/.
cp $top/tools/idea/native/XPlatLauncher/Cargo.toml $work_dir/.
cp $top/tools/idea/native/XPlatLauncher/Cargo.lock $work_dir/.

cd $work_dir
mkdir out && chmod 777 out
mkdir dist && chmod 777 dist

sudo docker build -t linux_tools:dev .
sudo docker run -it --rm \
  -v $top/tools/idea/native:/home/builder/tools/idea/native \
  -v $script_dir:/home/builder/tools/idea/platform/build-scripts/native \
  -v $work_dir/out:/home/builder/out \
  -v $work_dir/dist:/home/builder/dist \
  linux_tools:dev \
  bash -c "./tools/idea/platform/build-scripts/native/linux_tools.sh out dist 999 --quiet"

sudo chown -R $(id -u):$(id -g) $work_dir

mkdir -p "$script_dir/dist" && rm -rf "$script_dir/dist/*"
cp $work_dir/dist/* "$script_dir/dist"

echo "Tools artifacts are copied to $script_dir/dist "
ls -lha "$script_dir/dist"
#!/bin/bash

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && cd "$1" && pwd
}

declare -r top=$(pwd)
declare -r out_dir=$(make_target_dir "$1")
declare -r dist_dir=$(make_target_dir "$2")
declare -r build_number="$3"

# Converts version string to comparable number `12.3` -> 012003000000. Works for at most 4 fields
function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

# Check that file exists and is compiled for both: x86_64 and arm64
declare -r MAX_GLIBC_VERSION="2.31"
function verify_glibc() {
  glibc_version="$(objdump -x "$1" | grep -o "GLIBC_.*" | sort | uniq | cut -d _ -f 2 | sort -V | tail -n 1)"
  if [ $(ver ${glibc_version:-0}) -gt $(ver $MAX_GLIBC_VERSION) ]; then
    echo "ERROR: $1 uses glibc version $glibc_version which is newer than $MAX_GLIBC_VERSION"
    exit 2
  fi
}

function verify_statically_linked() {
  if [[ $(ldd $1) != *"not a dynamic executable"* ]]; then
    echo "ERROR: $1 have dynamically linked libraries. Abort."
    exit 3
  fi
}

# Build fsNotifier
(
    mkdir $out_dir/fsNotifier && cd $out_dir/fsNotifier
    cp -r $top/tools/idea/native/fsNotifier/linux/. ./
    ./make.sh

    verify_glibc fsnotifier
    cp fsnotifier $dist_dir/.
    chmod +x $dist_dir/fsnotifier
)

# Build WslTools
(
    mkdir $out_dir/WslTools && cd $out_dir/WslTools
    cp -r $top/tools/idea/native/WslTools/. ./

    # Prepare musl
    cp /home/builder/musl-1.2.2.tar.gz ./
    tar xfvz musl-1.2.2.tar.gz && mv musl-1.2.2 musl

    make

    verify_statically_linked wslhash
    verify_statically_linked wslproxy
    verify_statically_linked ttyfix

    cp wslhash $dist_dir/.
    chmod +x $dist_dir/wslhash

    cp wslproxy $dist_dir/.
    chmod +x $dist_dir/wslproxy

    cp ttyfix $dist_dir/.
    chmod +x $dist_dir/ttyfix
)

echo "Done Building IntelliJ Linux Tools!"
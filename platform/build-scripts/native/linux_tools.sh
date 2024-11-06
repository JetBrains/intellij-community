#!/bin/bash
set -eu

function realpath() {
   cd $1 && pwd
}

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && cd "$1" && pwd
}

declare -r top=$(realpath "$(dirname "$0")/../../../../..")
declare -r out_dir=$(make_target_dir "$1")
declare -r dist_dir=$(make_target_dir "$2")
declare -r build_number="$3"

if [[ "$*" == *"--quiet"* ]]
then
  verbose=
  quiet=1
else
  verbose=1
  quiet=
fi

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
  ldd_output=$(ldd "$1" 2>&1)

  if [[ $ldd_output == *"No such file"* ]]; then
  echo "ERROR: $1 Not found. Abort."
  exit 4
  fi

  if [[ $ldd_output != *"statically linked"* ]] && [[ $ldd_output != *"not a dynamic executable"* ]]; then
    echo "ERROR: $1 have dynamically linked libraries. Abort."
    echo $ldd_output
    exit 3
  fi
}

declare failed_count=0
declare failed_builds=""

(
    echo "---------------------------"
    echo " Building fsNotifier"
    echo "---------------------------"

    mkdir $out_dir/fsNotifier && cd $out_dir/fsNotifier
    cp -r $top/tools/idea/native/fsNotifier/linux/. ./
    ./make.sh

    verify_glibc fsnotifier
    cp fsnotifier $dist_dir/.
    chmod +x $dist_dir/fsnotifier
    ls -lha $dist_dir/fsnotifier
) || { failed_builds+=" fsnotifier" ;  failed_count="$((failed_count+1))" ; }


(
    echo "---------------------------"
    echo " Building WslTools"
    echo "---------------------------"
    mkdir $out_dir/WslTools && cd $out_dir/WslTools
    cp -r $top/tools/idea/native/WslTools/. ./

    # Prepare musl
    cp /home/builder/musl-1.2.2.tar.gz ./
    tar xfz${verbose:+v} musl-1.2.2.tar.gz && mv musl-1.2.2 musl

    # ubuntu:14.04 have too old GCC version that use -std=c90 by default
    # and don't like `{0}` style initialization: https://stackoverflow.com/questions/1538943
    #
    # Update compiler flags, so that code compiles
    sed -i -e 's/CFLAGS =/CFLAGS = -std=c99 -Wno-missing-field-initializers -Wno-missing-braces /g' ./Makefile

    make LOG=${quiet:+warn} ${quiet:+-s}

    verify_statically_linked wslhash
    verify_statically_linked wslproxy
    verify_statically_linked ttyfix

    cp wslhash $dist_dir/.
    chmod +x $dist_dir/wslhash

    cp wslproxy $dist_dir/.
    chmod +x $dist_dir/wslproxy

    cp ttyfix $dist_dir/.
    chmod +x $dist_dir/ttyfix

    ls -lha $dist_dir/wslhash
    ls -lha $dist_dir/wslproxy
    ls -lha $dist_dir/ttyfix
) || { failed_builds+=" WslTools" ;  failed_count="$((failed_count+1))" ; }

(
  echo "---------------------------"
  echo " Building Restarter"
  echo "---------------------------"
  mkdir $out_dir/restarter && cd $out_dir/restarter
  cp -r $top/tools/idea/native/restarter/. ./

  export PATH="/home/builder/.cargo/bin:$PATH"
  export CARGO_HOME=/home/builder/.cargo
  export RUSTUP_HOME=/home/builder/.rustup

  cargo build ${verbose:+-v} --release --target x86_64-unknown-linux-musl --offline --target-dir "$out_dir/restarter"
  cargo build ${verbose:+-v} --release --target x86_64-pc-windows-gnu --offline --target-dir "$out_dir/restarter"

  verify_statically_linked  "$out_dir/restarter/x86_64-unknown-linux-musl/release/restarter"

  cp "$out_dir/restarter/x86_64-unknown-linux-musl/release/restarter" $dist_dir/.
  cp "$out_dir/restarter/x86_64-pc-windows-gnu/release/restarter.exe" $dist_dir/.
  chmod +x $dist_dir/restarter
  ls -lha $dist_dir/restarter
  ls -lha $dist_dir/restarter.exe
) || { failed_builds+=" restarter" ;  failed_count="$((failed_count+1))" ; }

(
  echo "---------------------------"
  echo " Building Launcher"
  echo "---------------------------"

  mkdir $out_dir/launcher && cd $out_dir/launcher
  cp -r $top/tools/idea/native/XPlatLauncher/. ./

  export PATH="/home/builder/.cargo/bin:$PATH"
  export CARGO_HOME=/home/builder/.cargo
  export RUSTUP_HOME=/home/builder/.rustup

  cargo build ${verbose:+-v} --release --target x86_64-unknown-linux-gnu --target-dir "$out_dir/launcher"
  cargo build ${verbose:+-v} --no-default-features --release --target x86_64-pc-windows-gnu --target-dir "$out_dir/launcher"
  cargo about generate about.hbs > $out_dir/launcher/launcher-licenses.html

  verify_glibc "$out_dir/launcher/x86_64-unknown-linux-gnu/release/xplat-launcher"
  cp "$out_dir/launcher/x86_64-unknown-linux-gnu/release/xplat-launcher" $dist_dir/launcher
  cp "$out_dir/launcher/x86_64-pc-windows-gnu/release/xplat-launcher.exe" $dist_dir/launcher.exe
  cp $out_dir/launcher/launcher-licenses.html $dist_dir/.
  chmod +x $dist_dir/launcher
  ls -lha $dist_dir/launcher
  ls -lha $dist_dir/launcher.exe
) || { failed_builds+=" launcher" ;  failed_count="$((failed_count+1))" ; }

echo "=========================="
ls -lha $dist_dir
echo "=========================="

if [ $failed_count -gt 0 ]; then
  echo "Failed to build: $failed_builds"
  exit 4
else
  echo "Done Building IntelliJ Linux Tools!"
  exit 0
fi
#!/bin/sh
set -eu

if [ $(uname) != "Darwin" ]; then
    echo "macOS only"
    exit 1
fi

function realpath() {
   cd $1 && pwd
}

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && realpath "$1"
}

# Check that file exists and is compiled for both: x86_64 and arm64
function verifyArchs() {
  declare -r binary=$1
 if [ ! -f "$binary" ]; then
    echo "Failed to build $binary"
    exit 2
  fi

  declare -r archs=$(lipo -archs $binary)
  echo "$binary archs: $archs"
  if [[ ! "$archs" =~ "x86_64" ]]; then
    echo "$binary doesn't have x86_64 architecture "
    exit 3
  fi
  if [[ ! "$archs" =~ "arm64" ]]; then
    echo "$binary doesn't have arm64 architecture "
    exit 4
  fi
}


while getopts 'd:o:' opt; do
  case $opt in
    o) out_dir_option=$OPTARG;;
    d) dist_dir_option=$OPTARG;;
    *) usage ;;
  esac
done
shift $(($OPTIND-1))
(($#==0)) || usage

declare -r script_dir=$(realpath "$(dirname "$0")")
declare -r top=$(realpath "$(dirname "$0")/../../..")
declare -r cmake_bin="$top/prebuilts/studio/sdk/darwin/cmake/3.22.1/bin/cmake"
declare -x -r JDK_11_x64="$top/prebuilts/studio/jdk/jdk11/mac/Contents/Home"

declare -r rust_version="1.77.1"
declare -r rust_dir="$top/prebuilts/rust/darwin-x86/$rust_version"

# Create directories
declare -r out_dir=$(make_target_dir "${out_dir_option:-"out"}")
if [[ -n "${dist_dir_option:-}" ]]; then
  dist_dir="$(make_target_dir "${dist_dir_option}")"
fi

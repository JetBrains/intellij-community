# Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# Usage: . "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"
PROTOC_VERSION=${PROTOC_VERSION:-3.19.4}

PROTOC_BIN_DIR="${PROTOC_BIN_DIR:-$(git rev-parse --show-toplevel)/build/protobuf/bin}"
PROTOC_CACHE_DIR="${PROTOC_CACHE_DIR:-$(dirname "$PROTOC_BIN_DIR")/cache}"

mkdir -p "$PROTOC_BIN_DIR"
mkdir -p "$PROTOC_CACHE_DIR"

case "$(uname -s)" in
  Darwin)  PROTOC_OS_NAME="osx-$(uname -m)" ;;
  Linux)   PROTOC_OS_NAME="linux-$(uname -m)" ;;
  *)       echo "unrecognized operating system"; exit 1 ;;
esac

getprotoc() {
  _protoc_version="${1:-"$PROTOC_VERSION"}"
  _protoc_zip_name="protoc-$_protoc_version-$PROTOC_OS_NAME.zip"
  test -f "$PROTOC_CACHE_DIR/$_protoc_zip_name" || \
  wget -O "$PROTOC_CACHE_DIR/$_protoc_zip_name" \
    "https://github.com/protocolbuffers/protobuf/releases/download/v$_protoc_version/$_protoc_zip_name"

  _protoc_exe="$PROTOC_BIN_DIR/protoc"
  rm -f "$_protoc_exe.tmp"
  test -f "$_protoc_exe" && mv -f "$_protoc_exe" "$_protoc_exe.tmp"

  unzip -j "$PROTOC_CACHE_DIR/$_protoc_zip_name" -d "$PROTOC_BIN_DIR" bin/protoc || (
    test -f "$_protoc_exe.tmp" && mv -f "$_protoc_exe.tmp" "$_protoc_exe"
    rm -f "$PROTOC_CACHE_DIR/$_protoc_zip_name"
    exit 1
  )

  if [ "$1" = "" ] ; then
    rm -f "$_protoc_exe.tmp"
  else
    test -f "$_protoc_exe" && mv -f "$_protoc_exe" "$PROTOC_BIN_DIR/protoc-$_protoc_version"
    test -f "$_protoc_exe.tmp" && mv -f "$_protoc_exe.tmp" "$_protoc_exe"
  fi
}

getprotoc 3.5.1
mv -f "$PROTOC_BIN_DIR/protoc-3.5.1" "$PROTOC_BIN_DIR/protoc-java6"
getprotoc

export PATH="$PROTOC_BIN_DIR:$PATH"

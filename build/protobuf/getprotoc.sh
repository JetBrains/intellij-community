# Usage: . "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"
PROTOC_VERSION=${PROTOC_VERSION:-3.5.1}

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
  _protoc_zip_name="protoc-$PROTOC_VERSION-$PROTOC_OS_NAME.zip"
  test -f "$PROTOC_CACHE_DIR/$_protoc_zip_name" || \
  wget -O "$PROTOC_CACHE_DIR/$_protoc_zip_name" \
    "https://github.com/protocolbuffers/protobuf/releases/download/v$_protoc_version/$_protoc_zip_name"

  _protoc_exe="$PROTOC_BIN_DIR/protoc"
  rm -f "$_protoc_exe.tmp"
  test -f "$_protoc_exe" && mv -f "$_protoc_exe" "$_protoc_exe.tmp"

  tar --strip-components 1 -xf "$PROTOC_CACHE_DIR/$_protoc_zip_name" -C "$PROTOC_BIN_DIR" bin/protoc || (
    test -f "$_protoc_exe.tmp" && mv -f "$_protoc_exe.tmp" "$_protoc_exe"
    rm -f "$PROTOC_CACHE_DIR/$_protoc_zip_name"
    exit 1
  )

  rm -f "$_protoc_exe.tmp"
}

getprotoc

export PATH="$PROTOC_BIN_DIR:$PATH"

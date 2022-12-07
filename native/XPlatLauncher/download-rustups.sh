#!/bin/bash

set -euxo pipefail

mkdir -p rustup-init
cd rustup-init

RUSTUP_UPDATE_ROOT="https://static.rust-lang.org/rustup/dist"

curl -fsSL "$RUSTUP_UPDATE_ROOT/x86_64-unknown-linux-gnu/rustup-init" -o "rustup-init-x86_64-unknown-linux-gnu"
chmod +x "rustup-init-x86_64-unknown-linux-gnu"

# rustup ./rustup-init-x86_64-unknown-linux-gnu --version
# rustup-init 1.25.1 (bb60b1e89 2022-07-12)
RUSTUP_VERSION=$(./rustup-init-x86_64-unknown-linux-gnu --version | awk '{ print $2; }')
echo "RUSTUP_VERSION=$RUSTUP_VERSION"

VERSION_REGEX="^[0-9]+\.[0-9]+\.[0-9]+$"
! [[ "$RUSTUP_VERSION" =~ $VERSION_REGEX ]] && echo "Rustup version '$RUSTUP_VERSION' does not match version regex '$VERSION_REGEX'" && exit 1

mv "rustup-init-x86_64-unknown-linux-gnu" "rustup-init-x86_64-unknown-linux-gnu-$RUSTUP_VERSION"

curl -fsSL "$RUSTUP_UPDATE_ROOT/aarch64-unknown-linux-gnu/rustup-init" -o "rustup-init-aarch64-unknown-linux-gnu-$RUSTUP_VERSION"

curl -fsSL "$RUSTUP_UPDATE_ROOT/x86_64-apple-darwin/rustup-init"       -o "rustup-init-x86_64-apple-darwin-$RUSTUP_VERSION"
curl -fsSL "$RUSTUP_UPDATE_ROOT/aarch64-apple-darwin/rustup-init"      -o "rustup-init-aarch64-apple-darwin-$RUSTUP_VERSION"

curl -fsSL "$RUSTUP_UPDATE_ROOT/x86_64-pc-windows-msvc/rustup-init.exe" -o "rustup-init-x86_64-pc-windows-msvc-$RUSTUP_VERSION.exe"

# there is no native rustup for windows ARM yet,
# so let's pretend that there is one for the sake of consistency on the downloading side:)
cp "rustup-init-x86_64-pc-windows-msvc-$RUSTUP_VERSION.exe" "rustup-init-aarch64-pc-windows-msvc-$RUSTUP_VERSION.exe"

chmod +x rustup-init*
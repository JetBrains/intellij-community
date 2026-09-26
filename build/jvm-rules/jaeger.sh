#!/bin/bash

# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
TARBALL_NAME="jaeger-2.2.0-darwin-amd64.tar.gz"
JAERGER_URL="https://github.com/jaegertracing/jaeger/releases/download/v1.65.0/$TARBALL_NAME"
EXTRACT_DIR="$HOME/Applications/jaeger"

handle_error() {
  echo "An error occurred. Exiting..."
  exit 1
}

curl -L -o "$TARBALL_NAME" "$JAERGER_URL" || handle_error

if [ ! -d "$EXTRACT_DIR" ]; then
  mkdir -p "$EXTRACT_DIR" || handle_error
fi

tar -xzf "$TARBALL_NAME" -C "$EXTRACT_DIR" || handle_error
rm "$TARBALL_NAME" || handle_error

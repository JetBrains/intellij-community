#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

set -eu

JPS_BOOTSTRAP_DIR="$(cd "$(dirname "$0")"; pwd)"
JBS_COMMUNITY_HOME="$(cd "$JPS_BOOTSTRAP_DIR/../.."; pwd)"
JPS_BOOTSTRAP_WORK_DIR=${JPS_BOOTSTRAP_WORK_DIR:-$JBS_COMMUNITY_HOME/out/jps-bootstrap}

SCRIPT_VERSION=jps-bootstrap-cmd-v1

warn () {
    echo "$@"
}

die () {
    echo 2>&1
    echo "$@"  2>&1
    echo  2>&1
    exit 1
}

darwin=false
case "$(uname)" in
  Darwin* )
    darwin=true
    ;;
esac

if [ "$darwin" = "true" ]; then
    case $(uname -m) in
      x86_64)
        JVM_URL=https://corretto.aws/downloads/resources/11.0.9.12.1/amazon-corretto-11.0.9.12.1-macosx-x64.tar.gz
        JVM_TARGET_DIR="$JPS_BOOTSTRAP_WORK_DIR/jvm/amazon-corretto-11.0.9.12.1-macosx-x64-$SCRIPT_VERSION"
        ;;
      arm64)
        JVM_URL=https://cdn.azul.com/zulu/bin/zulu11.45.27-ca-jdk11.0.10-macosx_aarch64.tar.gz
        JVM_TARGET_DIR="$JPS_BOOTSTRAP_WORK_DIR/jvm/zulu-11.0.10-macosx-arm64-$SCRIPT_VERSION"
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
else
    case $(uname -m) in
      x86_64)
        JVM_URL=https://corretto.aws/downloads/resources/11.0.9.12.1/amazon-corretto-11.0.9.12.1-linux-x64.tar.gz
        JVM_TARGET_DIR="$JPS_BOOTSTRAP_WORK_DIR/jvm/amazon-corretto-11.0.9.12.1-linux-x64-$SCRIPT_VERSION"
        ;;
      aarch64)
        JVM_URL=https://corretto.aws/downloads/resources/11.0.9.12.1/amazon-corretto-11.0.9.12.1-linux-aarch64.tar.gz
        JVM_TARGET_DIR="$JPS_BOOTSTRAP_WORK_DIR/jvm/amazon-corretto-11.0.9.12.1-linux-aarch64-$SCRIPT_VERSION"
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
fi

mkdir -p "$JPS_BOOTSTRAP_WORK_DIR/jvm"

if [ -e "$JVM_TARGET_DIR/.flag" ] && [ -n "$(ls "$JVM_TARGET_DIR")" ] && [ "x$(cat "$JVM_TARGET_DIR/.flag")" = "x${JVM_URL}" ]; then
    # Everything is up-to-date in $JVM_TARGET_DIR, do nothing
    true
else
  JVM_TEMP_FILE=$(mktemp "$JPS_BOOTSTRAP_WORK_DIR/jvm.tar.gz.XXXXXXXXX")
  trap 'echo "Removing $JVM_TEMP_FILE"; rm -f "$JVM_TEMP_FILE"; trap - EXIT' EXIT INT HUP

  warn "Downloading $JVM_URL to $JVM_TEMP_FILE"

  if command -v curl >/dev/null 2>&1; then
      if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
      curl $CURL_PROGRESS --output "${JVM_TEMP_FILE}" "$JVM_URL"
  elif command -v wget >/dev/null 2>&1; then
      if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
      wget $WGET_PROGRESS -O "${JVM_TEMP_FILE}" "$JVM_URL"
  else
      die "ERROR: Please install wget or curl"
  fi

  warn "Extracting $JVM_TEMP_FILE to $JVM_TARGET_DIR"
  rm -rf "$JVM_TARGET_DIR"
  mkdir -p "$JVM_TARGET_DIR"

  tar -x -f "$JVM_TEMP_FILE" -C "$JVM_TARGET_DIR"
  rm -f "$JVM_TEMP_FILE"

  echo "$JVM_URL" >"$JVM_TARGET_DIR/.flag"
fi

JAVA_HOME=
for d in "$JVM_TARGET_DIR" "$JVM_TARGET_DIR"/* "$JVM_TARGET_DIR/Contents/Home" "$JVM_TARGET_DIR/"*/Contents/Home; do
  if [ -e "$d/bin/java" ]; then
    JAVA_HOME="$d"
  fi
done

if [ ! -e "$JAVA_HOME/bin/java" ]; then
  die "Unable to find bin/java under $JVM_TARGET_DIR"
fi

set -x

"$JAVA_HOME/bin/java" -jar "$JBS_COMMUNITY_HOME/lib/ant/lib/ant-launcher.jar" -f "$JPS_BOOTSTRAP_DIR/jps-bootstrap-classpath.xml"

export JBS_COMMUNITY_HOME
exec "$JAVA_HOME/bin/java" -classpath "$JPS_BOOTSTRAP_WORK_DIR/jps-bootstrap.out.lib/*" JpsBootstrapMain "$@"

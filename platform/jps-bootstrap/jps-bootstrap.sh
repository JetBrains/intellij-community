#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

set -eu

JPS_BOOTSTRAP_DIR="$(cd "$(dirname "$0")"; pwd)"
JPS_BOOTSTRAP_COMMUNITY_HOME="$(cd "$JPS_BOOTSTRAP_DIR/../.."; pwd)"
JPS_BOOTSTRAP_WORK_DIR="$JPS_BOOTSTRAP_COMMUNITY_HOME/out/jps-bootstrap"

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

ZULU_BASE=https://cache-redirector.jetbrains.com/cdn.azul.com/zulu/bin
ZULU_PREFIX=zulu11.50.19-ca-jdk11.0.12

if [ "$darwin" = "true" ]; then
    case $(uname -m) in
      x86_64)
        ZULU_ARCH=macosx_x64
        ;;
      arm64)
        ZULU_ARCH=macosx_aarch64
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
else
    case $(uname -m) in
      x86_64)
        ZULU_ARCH=linux_x64
        ;;
      aarch64)
        ZULU_ARCH=linux_aarch64
        ZULU_BASE="https://cache-redirector.jetbrains.com/cdn.azul.com/zulu-embedded/bin"
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
fi

JVM_URL="$ZULU_BASE/$ZULU_PREFIX-$ZULU_ARCH.tar.gz"
JVM_TARGET_DIR="$JPS_BOOTSTRAP_WORK_DIR/jvm/$ZULU_PREFIX-$ZULU_ARCH-$SCRIPT_VERSION"

mkdir -p "$JPS_BOOTSTRAP_WORK_DIR/jvm"

if [ -e "$JVM_TARGET_DIR/.flag" ] && [ -n "$(ls "$JVM_TARGET_DIR")" ] && [ "x$(cat "$JVM_TARGET_DIR/.flag")" = "x${JVM_URL}" ]; then
    # Everything is up-to-date in $JVM_TARGET_DIR, do nothing
    true
else
  JVM_TEMP_FILE=$(mktemp "$JPS_BOOTSTRAP_WORK_DIR/jvm.tar.gz.XXXXXXXXX")
  trap 'echo "Removing $JVM_TEMP_FILE"; rm -f "$JVM_TEMP_FILE"; trap - EXIT' EXIT INT HUP

  warn "Downloading $JVM_URL to $JVM_TEMP_FILE"

  if command -v curl >/dev/null 2>&1; then
      if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS=""; fi
      curl -fsSL $CURL_PROGRESS --output "${JVM_TEMP_FILE}" "$JVM_URL"
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

# Download and compile jps-bootstrap
"$JAVA_HOME/bin/java" -ea -Daether.connector.resumeDownloads=false -jar "$JPS_BOOTSTRAP_COMMUNITY_HOME/lib/ant/lib/ant-launcher.jar" "-Dbuild.dir=$JPS_BOOTSTRAP_WORK_DIR" -f "$JPS_BOOTSTRAP_DIR/jps-bootstrap-classpath.xml"

_java_args_file="$JPS_BOOTSTRAP_WORK_DIR/java.args.$$.txt"
# shellcheck disable=SC2064
trap "rm -f '$_java_args_file'" EXIT INT HUP

# Run jps-bootstrap and produce java args file to run actual user class
export JPS_BOOTSTRAP_COMMUNITY_HOME
"$JAVA_HOME/bin/java" -ea -Xmx4g -Djava.awt.headless=true -classpath "$JPS_BOOTSTRAP_WORK_DIR/jps-bootstrap.out.lib/*" org.jetbrains.jpsBootstrap.JpsBootstrapMain "--java-argfile-target=$_java_args_file" "$@"

# Run user class via wrapper from platform to correctly capture and report exception to TeamCity build log
"$JAVA_HOME/bin/java" "@$_java_args_file"

#!/bin/sh
# Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

set -eu

JPS_BOOTSTRAP_DIR="$(cd "$(dirname "$0")"; pwd)"
JPS_BOOTSTRAP_COMMUNITY_HOME="$(cd "$JPS_BOOTSTRAP_DIR/../.."; pwd)"
JPS_BOOTSTRAP_PREPARE_DIR="$JPS_BOOTSTRAP_COMMUNITY_HOME/out/jps-bootstrap"

SCRIPT_VERSION=jps-bootstrap-cmd-v1

warn () {
    echo "$@"
}

die () {
    echo 2>&1
    echo "$@"  2>&1
    echo 2>&1
    exit 1
}

expBackOffRetry() {
  FACTOR=2
  WAIT_SECONDS=1

  ATTEMPTS=4
  ATTEMPT_COUNTER=1

  # Workaround for dash (/bin/sh is usually symlinked to /bin/dash): SAVED_TRAPS="$(trap)" won't work in dash/zsh
  SAVED_TRAPS_FILE="$(mktemp "$JPS_BOOTSTRAP_PREPARE_DIR/saved_traps.XXXXXXXXX")"
  trap > "$SAVED_TRAPS_FILE"
  SAVED_TRAPS="$(cat "$SAVED_TRAPS_FILE")"
  rm "$SAVED_TRAPS_FILE"

  # Cancel by user (SIGINT) != failed attempt -> handle via trap
  CANCELLED="false"
  trap "CANCELLED=true" INT HUP

  while true; do
    COMMAND_FAILED=false
    "$@" || COMMAND_FAILED=true

    if [ "$COMMAND_FAILED" = "false" ] || [ "$ATTEMPT_COUNTER" -ge "$ATTEMPTS" ] || [ "$CANCELLED" = "true" ]; then
      break
    fi

    warn "Eval '$*': attempt $ATTEMPT_COUNTER failed. Retrying in $WAIT_SECONDS seconds..."
    sleep "$WAIT_SECONDS"

    ATTEMPT_COUNTER="$((ATTEMPT_COUNTER + 1))"
    WAIT_SECONDS="$((WAIT_SECONDS * FACTOR))"
  done

  # Restore traps
  eval "$SAVED_TRAPS"

  if [ "$COMMAND_FAILED" = "false" ]; then
    return 0
  fi

  if [ "$CANCELLED" = "false" ]; then
    warn "Eval '$*': attempts limit exceeded, tried $ATTEMPTS times."
  fi

  return 1
}

darwin=false
case "$(uname)" in
  Darwin* )
    darwin=true
    ;;
esac

JBR_VERSION=17.0.4.1
JBR_BUILD=b597.1

if [ "$darwin" = "true" ]; then
    case $(uname -m) in
      x86_64)
        JBR_ARCH=osx-x64
        ;;
      arm64)
        JBR_ARCH=osx-aarch64
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
else
    case $(uname -m) in
      x86_64)
        JBR_ARCH=linux-x64
        ;;
      aarch64)
        JBR_ARCH=linux-aarch64
        ;;
      *)
        die "Unknown architecture $(uname -m)"
        ;;
    esac
fi

JVM_URL="https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$JBR_VERSION-$JBR_ARCH-$JBR_BUILD.tar.gz"
JVM_TARGET_DIR="$JPS_BOOTSTRAP_PREPARE_DIR/jvm/$JBR_VERSION$JBR_BUILD-$JBR_ARCH-$SCRIPT_VERSION"

mkdir -p "$JPS_BOOTSTRAP_PREPARE_DIR/jvm"

if [ -e "$JVM_TARGET_DIR/.flag" ] && [ -n "$(ls "$JVM_TARGET_DIR")" ] && [ "x$(cat "$JVM_TARGET_DIR/.flag")" = "x${JVM_URL}" ]; then
    # Everything is up-to-date in $JVM_TARGET_DIR, do nothing
    true
else
  JVM_TEMP_FILE=$(mktemp "$JPS_BOOTSTRAP_PREPARE_DIR/jvm.tar.gz.XXXXXXXXX")
  trap 'echo "Removing $JVM_TEMP_FILE"; rm -f "$JVM_TEMP_FILE"; trap - EXIT' EXIT INT HUP

  warn "Downloading $JVM_URL to $JVM_TEMP_FILE"

  if command -v curl >/dev/null 2>&1; then
      if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS=""; fi
      CURL_OPTIONS="-fsSL"
      if [ "${JBR_DOWNLOAD_CURL_VERBOSE:-false}" = "true" ]; then CURL_OPTIONS="-fvL"; fi
      # CURL_PROGRESS may be empty, with quotes this interpreted by curl as malformed URL
      # shellcheck disable=SC2086
      expBackOffRetry curl "$CURL_OPTIONS" $CURL_PROGRESS --output "${JVM_TEMP_FILE}" "$JVM_URL"
  elif command -v wget >/dev/null 2>&1; then
      if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
      expBackOffRetry wget $WGET_PROGRESS -O "${JVM_TEMP_FILE}" "$JVM_URL"
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

BOOTSTRAP_SYSTEM_PROPERTIES="${BOOTSTRAP_SYSTEM_PROPERTIES:-}"
if [ "x${JPS_BOOTSTRAP_PREPARE_SKIP_DOWNLOAD:-0}" != "x1" ] ; then
  # Download and compile jps-bootstrap
  # shellcheck disable=SC2086
  "$JAVA_HOME/bin/java" -ea -Daether.connector.resumeDownloads=false $BOOTSTRAP_SYSTEM_PROPERTIES -jar "$JPS_BOOTSTRAP_COMMUNITY_HOME/lib/ant/lib/ant-launcher.jar" "-Dbuild.dir=$JPS_BOOTSTRAP_PREPARE_DIR" -f "$JPS_BOOTSTRAP_DIR/jps-bootstrap-classpath.xml"
fi

_java_args_file="$JPS_BOOTSTRAP_PREPARE_DIR/java.args.$$.txt"
# shellcheck disable=SC2064
trap "{ set +x; } >/dev/null 2>&1; rm -f '$_java_args_file'" EXIT INT HUP

# Run jps-bootstrap and produce java args file to run actual user class
export JPS_BOOTSTRAP_COMMUNITY_HOME
# shellcheck disable=SC2086
"$JAVA_HOME/bin/java" -ea -Xmx4g -Djava.awt.headless=true $BOOTSTRAP_SYSTEM_PROPERTIES -classpath "$JPS_BOOTSTRAP_PREPARE_DIR/jps-bootstrap.out.lib/*" org.jetbrains.jpsBootstrap.JpsBootstrapMain "--java-argfile-target=$_java_args_file" "$@"

# Run user class via wrapper from platform to correctly capture and report exception to TeamCity build log
"$JAVA_HOME/bin/java" "@$_java_args_file"

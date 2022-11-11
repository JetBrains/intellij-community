#!/bin/bash
# Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail
set -x

function isForced() {
  for arg in "$@"; do
    if [[ "$arg" == --force ]]; then
      return 0
    fi
  done
  return 1
}

function jetSignExtensions() {
  args=("$@")
  ((lastElementIndex=${#args[@]}-1))
  for index in "${!args[@]}"; do
    arg=${args[$index]}
    case "$arg" in
    --sign)
      echo -n 'mac_codesign_identity='
      continue
      ;;
    --entitlements)
      echo -n 'mac_codesign_entitlements='
      continue
      ;;
    --options=runtime)
      echo -n 'mac_codesign_options=runtime'
      ;;
    --force)
      echo -n 'mac_codesign_force=true'
      ;;
    --timestamp | --verbose)
      continue
      ;;
    *)
      echo -n "$arg"
      ;;
    esac
    if [[ $index != "$lastElementIndex" ]]; then
      echo -n ","
    fi
  done
}

# See jetbrains.sign.util.FileUtil.contentType
function jetSignContentType() {
  case "${1##*/}" in
  *.sit)
    echo -n 'application/x-mac-app-zip'
    ;;
  *)
    echo -n 'application/x-mac-app-bin'
    ;;
  esac
}

function isMacOsBinary() {
  file "$1" | grep -q 'Mach-O'
}

function isSigned() {
  codesign --verify "$1" && ! grep -q Signature=adhoc < <(codesign --display --verbose "$1" 2>&1)
}

# last argument is a path to be signed
pathToBeSigned="$(pwd)/${*: -1}"
jetSignArgs=("${@:1:$#-1}")
if [[ ! -f "$pathToBeSigned" ]]; then
  echo "$pathToBeSigned is missing"
  exit 1
elif isSigned "$pathToBeSigned" && ! isForced "${jetSignArgs[@]}" ; then
  echo "Already signed: $pathToBeSigned"
elif [[ "$JETSIGN_CLIENT" == "null" ]]; then
  echo "JetSign client is missing, cannot proceed with signing"
  exit 1
elif ! isMacOsBinary "$pathToBeSigned" && [[ "$pathToBeSigned" != *.sit ]]; then
  echo "$pathToBeSigned won't be signed, assumed not to be a macOS executable"
else
  if isMacOsBinary "$pathToBeSigned" && ! isSigned "$pathToBeSigned" ; then
    echo "Unsigned macOS binary: $pathToBeSigned"
  fi
  workDir=$(dirname "$pathToBeSigned")
  pathSigned="$workDir/signed/${pathToBeSigned##*/}"
  jetSignExtensions=$(jetSignExtensions "${jetSignArgs[@]}")
  contentType=$(jetSignContentType "$pathToBeSigned")
  (
    cd "$workDir"
    "$JETSIGN_CLIENT" -log-format text -denoted-content-type "$contentType" -extensions "$jetSignExtensions" "$pathToBeSigned"
    # SRE-1223 workaround
    chmod "$(stat -f %A "$pathToBeSigned")" "$pathSigned"
    if isMacOsBinary "$pathSigned"; then
      isSigned "$pathSigned"
    fi
    rm "$pathToBeSigned"
    mv "$pathSigned" "$pathToBeSigned"
    rm -rf "$workDir/signed"
  )
fi

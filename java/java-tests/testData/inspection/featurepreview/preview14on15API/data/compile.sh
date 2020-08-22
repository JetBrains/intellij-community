#!/usr/bin/env bash
set +x

# java 1.8
JAVAC="$(which javac)"

BASE_PATH=$(dirname $0)
CLASSES="${BASE_PATH}"/classes
SOURCES="${BASE_PATH}"/src/String.java

function error() {
  echo "$@" && false
}

function javac_defined() {
  [ -x "${JAVAC}" ] || error "Java compiler not defined"
}

function javac_of_1_8() {
  "${JAVAC}" -version |& grep -e '1\.8' &> /dev/null || error "compiler of version 1.8 required"
}

function remove_classes() {
  rm -r "${CLASSES}" || error "Unable to remove the '${CLASSES}' directory"
}

function create_classes_dir() {
  mkdir -p "${CLASSES}" || error "Unable to create the '${CLASSES}' directory"
}

function compile() {
  "${JAVAC}" -g -source 1.8 -target 1.8 -d "${CLASSES}" "${SOURCES}" || error "Unable to compile"
}

javac_defined &&
  javac_of_1_8 &&
  remove_classes &&
  create_classes_dir &&
  compile &&
  echo "The classes '$(find ${CLASSES} -type f -name "*.class")' compiled successfully"

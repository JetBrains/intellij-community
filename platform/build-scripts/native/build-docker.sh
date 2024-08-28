#!/bin/bash

function clear_files() {
  rm Cargo.toml
  rm Cargo.lock
}
trap clear_files EXIT

cp ../../../native/XPlatLauncher/Cargo.toml ./
cp ../../../native/XPlatLauncher/Cargo.lock ./

gcloud builds submit \
  --project google.com:android-studio-alphasource \
  --tag gcr.io/google.com/android-studio-alphasource/intellij_native
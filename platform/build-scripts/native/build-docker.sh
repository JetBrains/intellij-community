#!/bin/bash

gcloud builds submit \
  --project google.com:android-studio-alphasource \
  --tag gcr.io/google.com/android-studio-alphasource/intellij_native
#!/bin/bash

set -e

function retry() {
  local limit=$1
  shift
  local attempt=1
  while true; do
    # shellcheck disable=SC2015
    "$@" && { return 0; } || {
      ec=$?
      echo "Failed with exit code $ec. Attempt $attempt/$limit."
      if [[ $attempt -ge limit ]]; then
        return $ec
      fi
      ((attempt++))
    }
  done
}

GOLANG_IMAGE=$(cat golang-image.txt)
retry 3 docker pull "$GOLANG_IMAGE"
for platform in "windows/amd64" "windows/arm64" "linux/amd64" "linux/arm64" "darwin/amd64" "darwin/arm64"
do
  ENVVAR=$(echo $platform | sed 's/\//_/g')
  DOWNLOADURL=$(eval "echo \${${ENVVAR}_url}" )
  docker buildx build \
    --target bin --output bin/ --platform $platform \
    --build-arg "GOLANG_IMAGE=$GOLANG_IMAGE" \
    --build-arg "DOWNLOADURL=$DOWNLOADURL" .
done

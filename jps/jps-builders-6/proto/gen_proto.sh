#!/usr/bin/env sh
. "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"

protoc -I=. --java_out=../gen --java_opt=annotate_code javac_remote_proto.proto

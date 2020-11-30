#!/usr/bin/env sh
. "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"

protoc -I=. --java_out=lite:../../../jps-builders/gen --java_opt=annotate_code javac_remote_proto.proto
protoc-java6 -I=. --java_out=../gen --java_opt=annotate_code javac_remote_proto.proto

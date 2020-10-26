#!/bin/sh
. "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"

protoc -I=. --java_out=../src --java_opt=annotate_code cmdline_remote_proto.proto

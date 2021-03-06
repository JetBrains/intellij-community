#!/bin/sh
. "$(git rev-parse --show-toplevel)/build/protobuf/getprotoc.sh"

protoc -I=. --java_out=lite:../gen --java_opt=annotate_code cmdline_remote_proto.proto

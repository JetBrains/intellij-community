ARG JAVA_VERSION=21
FROM ghcr.io/graalvm/native-image-community:${JAVA_VERSION}
RUN microdnf update -y oraclelinux-release-el9 \
    && microdnf --enablerepo ol9_codeready_builder install -y gdb-gdbserver gcc maven \
    && microdnf clean all
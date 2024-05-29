FROM ubuntu:14.04

RUN apt-get update && apt-get install -y \
    make \
    cmake \
    xz-utils \
    zip unzip \
    binutils \
    clang \
    musl-tools

RUN useradd -ms /bin/bash builder
USER builder

ADD --chown=builder:builder https://musl.libc.org/releases/musl-1.2.2.tar.gz /home/builder/
RUN chmod 644 /home/builder/musl-1.2.2.tar.gz

WORKDIR /home/builder


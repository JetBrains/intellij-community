FROM ubuntu:14.04

RUN apt-get update && apt-get install -y \
    make \
    cmake \
    xz-utils \
    zip unzip \
    binutils \
    clang \
    musl musl-tools \
    curl \
    pkg-config \
    libssl-dev

RUN useradd -ms /bin/bash builder
USER builder

ADD --chown=builder:builder https://musl.libc.org/releases/musl-1.2.2.tar.gz /home/builder/
RUN chmod 644 /home/builder/musl-1.2.2.tar.gz

# Setup Rust for offline build
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y -v --default-toolchain stable --default-host x86_64-unknown-linux-gnu --profile minimal; \
    . "$HOME/.cargo/env"; \
    rustup target add x86_64-unknown-linux-musl; \
    rustup show;

RUN . "$HOME/.cargo/env"; \
    cargo install cargo-prefetch; \
    cargo prefetch; \
    cargo prefetch autocfg@=1.1.0 chrono@=0.4.31 libc@=0.2.148 num-traits@=0.2.16

WORKDIR /home/builder
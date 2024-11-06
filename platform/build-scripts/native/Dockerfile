FROM ubuntu:20.04

RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    make \
    cmake \
    xz-utils \
    zip unzip \
    binutils \
    clang \
    musl musl-tools \
    curl \
    pkg-config \
    libssl-dev \
    mingw-w64 \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -ms /bin/bash builder
USER builder

ADD --chown=builder:builder https://musl.libc.org/releases/musl-1.2.2.tar.gz /home/builder/
RUN chmod 644 /home/builder/musl-1.2.2.tar.gz

# Setup Rust for offline build
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y -v --default-toolchain stable --default-host x86_64-unknown-linux-gnu --profile minimal; \
    . "$HOME/.cargo/env"; \
    rustup target add x86_64-unknown-linux-musl; \
    rustup target add x86_64-pc-windows-gnu; \
    cargo install --locked cargo-about; \
    rustup show;


# Pre-fetch dependencies for Rust projects

# create empty project
RUN mkdir -p /home/builder/prefetch/src && echo "fn main() {}" > /home/builder/prefetch/src/main.rs && echo "fn main() {}" > /home/builder/prefetch/build.rs
WORKDIR /home/builder/prefetch

# fetch dependencies for XPlatLauncher
COPY cargo/XPlatLauncher/Cargo.toml cargo/XPlatLauncher/Cargo.lock /home/builder/prefetch/
RUN . "$HOME/.cargo/env"; cargo fetch

# fetch dependencies for restarter
COPY cargo/restarter/Cargo.toml cargo/restarter/Cargo.lock /home/builder/prefetch/
RUN . "$HOME/.cargo/env"; cargo fetch

# some crates (fnv-1.0.7) have busted permissions. Make cargo cache readable for everyone
RUN chmod -R a+r "$HOME/.cargo"

WORKDIR /home/builder
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

# Copy over the Cargo.toml files to the shell project
COPY Cargo.toml Cargo.lock /home/builder/

# Build and cache the dependencies
WORKDIR /home/builder

RUN mkdir /home/builder/src && echo "fn main() {}" > /home/builder/src/main.rs && echo "fn main() {}" > /home/builder/build.rs
RUN . "$HOME/.cargo/env"; cargo fetch --target x86_64-unknown-linux-gnu

# ToDo: use `cargo fetch` for restarter libs as well, as it move preciese and could handle yanked packages
RUN . "$HOME/.cargo/env"; \
    cargo install cargo-prefetch; \
    cargo prefetch; \
    cargo prefetch autocfg@=1.1.0 chrono@=0.4.38 num-traits@=0.2.16 winresource@=0.1.17 windows@=0.58.0 libc@=0.2.155


# syntax=docker/dockerfile:1
FROM registry.jetbrains.team/p/ij/docker-hub/ubuntu:20.04@sha256:8e5c4f0285ecbb4ead070431d29b576a530d3166df73ec44affc1cd27555141b AS build_env
LABEL Description="Community Build Environment"
RUN apt-get update && \
    apt-get install -y wget \
    tar \
    p7zip-full \
    libfreetype6 \
    fontconfig \
    zip \
    unzip \
    libgl1-mesa-glx \
    squashfs-tools \
    git \
    && rm -rf /var/lib/apt/lists/*
# Maven cache to reuse
VOLUME /root/.m2
# Community sources root
VOLUME /community
WORKDIR /community
ENTRYPOINT ["/bin/sh", "./installers.cmd"]

FROM build_env AS build_env_with_docker
LABEL Description="Community Build Environment with Docker (required to build Snapcraft distributions)"
RUN apt-get update && \
    apt-get install -y docker.io \
    && rm -rf /var/lib/apt/lists/*
COPY --from=registry.jetbrains.team/p/ij/docker-hub/docker/buildx-bin@sha256:acb92208a71a4b4b8d393cad2574aa932a58c4507fce6f0d05e50498d2acb547 /buildx /usr/libexec/docker/cli-plugins/docker-buildx
RUN docker buildx version
# Docker daemon socket is expected to be mounted with --volume /var/run/docker.sock:/var/run/docker.sock
# and the container should be run as the root user to be able to connect to the socket
VOLUME /var/run/docker.sock
# the current repository has to be specified as a safe directory,
# otherwise git calls will detect dubious ownership (see https://github.com/git/git/blob/master/Documentation/config/safe.txt)
# due to the container being run as the root user
CMD ["docker system info && git config --add safe.directory . && ./installers.cmd"]

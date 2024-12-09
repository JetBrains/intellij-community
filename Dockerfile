# syntax=docker/dockerfile:1
FROM ubuntu:20.04 AS build_env
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
COPY --from=docker/buildx-bin:latest /buildx /usr/libexec/docker/cli-plugins/docker-buildx
RUN docker buildx version
# Docker daemon socket is expected to be mounted with --volume /var/run/docker.sock:/var/run/docker.sock
# and the container should be run as the root user to be able to connect to the socket
VOLUME /var/run/docker.sock
CMD ["docker system info && ./installers.cmd"]

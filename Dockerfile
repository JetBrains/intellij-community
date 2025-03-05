# syntax=docker/dockerfile:1
FROM registry.jetbrains.team/p/ij/docker-hub/ubuntu:20.04@sha256:8e5c4f0285ecbb4ead070431d29b576a530d3166df73ec44affc1cd27555141b AS build_env
LABEL Description="IDE Build Environment"
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
    # IJPL-173242
    git \
    && rm -rf /var/lib/apt/lists/*
# optional volume to reuse Maven cache from the host
VOLUME /home/ide_builder/.m2
# the home directory should exist and be writable for any user used to launch a container because it is required for the IDE
RUN useradd --create-home ide_builder && \
    # the .m2 directory should be initialized with something \
    # otherwise the `chmod` effect is discarded if no Maven cache volume is specified for `docker run`
    mkdir -p /home/ide_builder/.m2/repository && \
    chmod --recursive a+rwx /home/ide_builder
# for jps-bootstrap itself
ENV BOOTSTRAP_SYSTEM_PROPERTIES="-Duser.home=/home/ide_builder"
# Community sources root
VOLUME /community
WORKDIR /community
# the repository has to be specified as a safe directory,
# otherwise git calls will detect dubious ownership (see https://github.com/git/git/blob/master/Documentation/config/safe.txt),
# if the container's user doesn't match the host's user (no `--user "$(id -u)"` argument is supplied for `docker run`)
RUN git init /community && \
    git config --global --add safe.directory /community && \
    rm -rf /community/.git

FROM build_env AS tests_env
LABEL Description="Tests Environment"
ENTRYPOINT ["/bin/sh", "./tests.cmd", "-Duser.home=/home/ide_builder"]

FROM build_env AS intellij_idea
LABEL Description="IntelliJ IDEA Build Environment"
ENTRYPOINT ["/bin/sh", "./installers.cmd", "-Duser.home=/home/ide_builder"]

FROM build_env AS pycharm
LABEL Description="PyCharm Build Environment"
ENTRYPOINT ["/bin/sh", "./python/installers.cmd", "-Duser.home=/home/ide_builder"]

# syntax=docker/dockerfile:1
FROM registry.jetbrains.team/p/ij/docker-hub/ubuntu:20.04@sha256:8e5c4f0285ecbb4ead070431d29b576a530d3166df73ec44affc1cd27555141b AS build_env
LABEL Description="IDE Build Environment"
RUN apt-get update && \
    apt-get install -y \
    # Used to download bazelisk in bazel.cmd
    curl \
    # Used org.jetbrains.intellij.build.impl.TarKt.unTar
    # TODO: switch to JVM implementation
    tar \
    # Used in org.jetbrains.intellij.build.impl.WindowsDistributionBuilderKt.checkThatExeInstallerAndZipWithJbrAreTheSame
    # TODO: should be downloaded in-place
    p7zip-full \
    # to run headless, but real IDE in buildscripts to gather various information from runtime
    libfreetype6 \
    fontconfig \
    # IJPL-173242 Provide a way to retrieve git revision which IntelliJ-based IDE was built from \
    # TODO Switch to jgit or to external stamping in the future (like for Bazel)
    git \
    && rm -rf /var/lib/apt/lists/*
# optional volume to reuse Bazel caches from the host
VOLUME /home/ide_builder/.cache
# the home directory should exist and be writable for any user used to launch a container because it is required for the IDE
RUN useradd --create-home ide_builder && \
    # the .cache directory should be initialized with something \
    # otherwise the `chmod` effect is discarded if no cache volume is specified for `docker run`
    mkdir -p /home/ide_builder/.cache && \
    chmod --recursive a+rwx /home/ide_builder
ENV HOME="/home/ide_builder"
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
ENTRYPOINT ["/bin/sh", "./tests.cmd"]

FROM build_env AS intellij_idea
LABEL Description="IntelliJ IDEA Build Environment"
ENTRYPOINT ["/bin/sh", "./installers.cmd"]

FROM build_env AS pycharm
LABEL Description="PyCharm Build Environment"
ENTRYPOINT ["/bin/sh", "./python/installers.cmd"]

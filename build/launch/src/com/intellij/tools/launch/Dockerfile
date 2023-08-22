FROM ubuntu:18.04

MAINTAINER ivan.pashchenko@jetbrains.com

ARG USER_NAME
ARG USER_ID
ARG USER_HOME
ARG DEFAULT_GATEWAY

# this is required for the headless mode to work without exceptions :)
RUN apt update && apt install -y xorg openbox
RUN apt install -y net-tools iputils-ping iproute2 telnet sudo tcpdump conntrack iptables

RUN useradd -u $USER_ID -d $USER_HOME -m $USER_NAME

# configuring network requires root
RUN echo "$USER_NAME ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
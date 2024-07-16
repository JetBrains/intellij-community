FROM buildpack-deps:curl AS builder

RUN cd /tmp && curl -L https://github.com/flitnetics/jaeger-objectstorage/releases/download/v3.0.0/jaeger-objectstorage_3.0.0_linux_x86_64.tar.gz | tar xvz

FROM jaegertracing/all-in-one@sha256:501ca4a98fd5a4c42f9a1f2cb0c6a63e46bcf8bfe290d5c71285240483cb5760

COPY --from=builder /tmp/jaeger-objectstorage /usr/bin/jaeger-objectstorage
COPY ./storage-config.yaml /etc/storage-config.yaml

ENV GRPC_STORAGE_PLUGIN_BINARY=/usr/bin/jaeger-objectstorage
ENV GRPC_STORAGE_PLUGIN_CONFIGURATION_FILE=/etc/storage-config.yaml
ENV SPAN_STORAGE_TYPE=grpc-plugin

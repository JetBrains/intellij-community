FROM buildpack-deps:curl AS builder

RUN cd /tmp && curl -L https://github.com/flitnetics/jaeger-objectstorage/releases/download/v2.0.1/jaeger-objectstorage_2.0.1_linux_x86_64.tar.gz | tar xvz

FROM jaegertracing/all-in-one@sha256:f500e6faa18bbb291ea56405cc2db17b2f1bc094773f161741332b40284d2590

COPY --from=builder /tmp/jaeger-objectstorage /usr/bin/jaeger-objectstorage
COPY ./storage-config.yaml /etc/storage-config.yaml

ENV GRPC_STORAGE_PLUGIN_BINARY=/usr/bin/jaeger-objectstorage
ENV GRPC_STORAGE_PLUGIN_CONFIGURATION_FILE=/etc/storage-config.yaml
ENV SPAN_STORAGE_TYPE=grpc-plugin

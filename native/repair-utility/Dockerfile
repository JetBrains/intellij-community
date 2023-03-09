ARG GOLANG_IMAGE
FROM --platform=${BUILDPLATFORM} ${GOLANG_IMAGE} AS build
ENV CGO_ENABLED=0
WORKDIR /src
COPY . .
ARG TARGETOS
ARG TARGETARCH
ARG DOWNLOADURL
RUN go mod download all
RUN GOOS=${TARGETOS} GOARCH=${TARGETARCH} go build -o /out/repair -ldflags "-X repair/helpers.EmbeededDownloadUrl=${DOWNLOADURL}" .

FROM scratch AS bin-linux-amd64
COPY --from=build /out/repair /repair-linux-amd64

FROM scratch AS bin-linux-arm64
COPY --from=build /out/repair /repair-linux-arm64

FROM scratch AS bin-darwin-amd64
COPY --from=build /out/repair /repair-darwin-amd64

FROM scratch AS bin-darwin-arm64
COPY --from=build /out/repair /repair-darwin-arm64

FROM scratch AS bin-windows-amd64
COPY --from=build /out/repair /repair.exe

FROM scratch AS bin-windows-arm64
COPY --from=build /out/repair /repair64a.exe

FROM bin-${TARGETOS}-${TARGETARCH} AS bin
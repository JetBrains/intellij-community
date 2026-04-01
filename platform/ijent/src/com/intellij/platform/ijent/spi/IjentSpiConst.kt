// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

/**
 * A soft limit for a single gRPC message payload size, applied to file reads, writes, tunnels, and process stdin.
 *
 * ## What this controls
 *
 * - **Read requests**: the `size` field in `ReadRequest` is capped at this value (see `GrpcIjentOpenedFile.coercionLimit`).
 *   This bounds the response size because the Rust side allocates `Vec::resize(max_size, 0u8)` for each read.
 * - **Write data**: `ByteString.copyFrom(buf, min(remaining, coercionLimit))` limits data per gRPC write message.
 * - **Tunnel and stdin packets**: regrouped into chunks of at most this size (see `regroupIntoByteString`).
 *
 * The API implementation applies this limit internally; API users need not fragment data themselves.
 *
 * ## Why 768 KB (131072 * 6)
 *
 * The value was chosen empirically through benchmarking file read throughput over gRPC-over-stdio.
 *
 * The key tradeoff is **round-trips vs. memory per call**:
 * - Smaller values (e.g., 128 KB) require more gRPC round-trips per buffer fill.
 *   A typical 512 KB NIO read buffer would need 4 gRPC calls at 128 KB vs. 1 call at 768 KB.
 * - Larger values reduce round-trips but increase per-call memory allocation on the Rust side
 *   and the serialized gRPC response size.
 * - 768 KB showed the best throughput in benchmarks, likely because it covers common read buffer
 *   sizes (up to 512 KB) in a single round-trip while staying well within gRPC limits.
 *
 * ## gRPC message size safety
 *
 * Both gRPC-java (Netty transport, used by the IDE) and tonic (used by the Rust IJent agent) default to
 * a 4 MB max inbound message size. The IJent channel builders do not override this default.
 * Therefore, values up to approximately 3 MB are safe (leaving headroom for protobuf framing, gRPC headers,
 * and HTTP/2 overhead). At 768 KB, the total message including protobuf tag+varint (4 bytes) and gRPC
 * length-prefixed frame header (5 bytes) is well under the 4 MB limit.
 *
 * ## Transport details (stdio pipe)
 *
 * For the stdio transport (`IOStreamNettyChannel`), gRPC frames are written directly to stdout and read from stdin.
 * The underlying pipe buffer on Linux defaults to 64 KB (tunable up to 1 MB via `F_SETPIPE_SZ`).
 * A 768 KB gRPC message is fragmented into multiple pipe writes by the OS, but this happens transparently
 * at the kernel level and does not cause additional gRPC-level round-trips.
 *
 * ## Special channel bypass for writes
 *
 * When the special channel is available (HyperV/vsock, controlled by `ijent.permit.special.channel` registry key),
 * write payloads larger than 4096 bytes bypass gRPC entirely and are sent through a side channel.
 * In that case, `coercionLimit` becomes `Int.MAX_VALUE` and this constant does not apply to writes.
 *
 * ## Notes
 *
 * - The other side of communication may be a machine with limited RAM. Avoid increasing this value
 *   excessively, as the Rust agent allocates a `Vec<u8>` of this size for every read call.
 * - It's acceptable if the actual message slightly exceeds this limit (by a few KB).
 * - This value must stay well below the 4 MB gRPC hard limit to avoid `io.grpc.StatusRuntimeException: RESOURCE_EXHAUSTED`.
 */
const val RECOMMENDED_MAX_PACKET_SIZE: Int = 131_072 * 6
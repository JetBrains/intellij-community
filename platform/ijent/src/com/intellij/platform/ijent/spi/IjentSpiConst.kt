// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

/**
 * A soft limit for a single message size. It can be applied to tunnels, file reading and writing, etc.
 *
 * The API implementation should apply this limit itself, so API users may know nothing about this constant.
 * Moreover, API users are not supposed to fragment packets with this constant, because the API implementation would do that again.
 *
 * It's a good practice to use this limit for sending data chunks to IJent,
 * because the other side of communication may be a machine with little RAM,
 * and creating hundreds of megabytes of packets can be undesirable.
 *
 * Also, it's known that the gRPC implementation has a hard limit for _incoming_ messages, so this constant should be used in
 * requests to mitigate huge responses.
 *
 * It's acceptable if the message exceeds this limit for a few kilobytes.
 *
 * 131072 is 2^17 -- this number is chosen with no research, just a wild guess.
 * It may be changed if necessary, but it must not be close to the timeout from `io.grpc.internal.MessageDeframer.processHeader`
 */
const val RECOMMENDED_MAX_PACKET_SIZE = 131_072
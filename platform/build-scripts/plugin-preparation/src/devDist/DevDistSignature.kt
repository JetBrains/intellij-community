package org.jetbrains.intellij.build.devDist

import com.dynatrace.hash4j.hashing.HashStream128
import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * The signature of what [build] puts into the stream: a 128-bit `xxh3` hash in base 36.
 * A signature is a staleness guard that readers compare as an opaque string.
 * The Go parity test mirrors this function in `kotlin_preparation_test.go`.
 */
@ApiStatus.Internal
fun devDistSignature(build: HashStream128.() -> Unit): String {
  val stream = Hashing.xxh3_128().hashStream()
  stream.build()
  val value = stream.get()
  val bytes = ByteBuffer.allocate(16).putLong(value.mostSignificantBits).putLong(value.leastSignificantBits).array()
  return BigInteger(1, bytes).toString(36)
}

/** The signature of [values] in order. Each value goes in with its length. */
@ApiStatus.Internal
fun devDistSignatureOf(values: Iterable<String>): String = devDistSignature { for (value in values) putString(value) }

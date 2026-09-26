/**
 * ...
 * {@snippet lang = "java":
 * Objects.requireNonNull(channel, "channel is null");
 * final var buffer = ByteBuffer.allocate(BYTES);
 * put(buffer);
 * buffer.flip();
 * while (buffer.hasRemaining()) { // @highlight region
 *     final var written = channel.write(buffer);
 *     assert written >= 0; // why?
 * } // @end
 * return channel;
 *}
 */
public class Hello {
}

```java
public class Hello
```

---

 ...
 
```java
Objects.requireNonNull(channel, "channel is null");
final var buffer = ByteBuffer.allocate(BYTES);
put(buffer);
buffer.flip();
while (buffer.hasRemaining()) { 
    final var written = channel.write(buffer);
    assert written >= 0; // why?
} 
return channel;
```
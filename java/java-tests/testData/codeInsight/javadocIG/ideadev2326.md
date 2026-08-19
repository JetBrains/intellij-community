From class: [`Idea4780`](Idea4780)
```java
public Object read()
throws IOException
```

**Throws:**
[`IOException`](java.io.IOException) &ndash; if an I/O error occurs while reading.  

[`EOFException`](java.io.EOFException) &ndash; if this source is already closed when the `read()` is called,
 or is closed during the `read()`.  

[`InterruptedIOException`](java.io.InterruptedIOException) &ndash; if the reading thread is interrupted.
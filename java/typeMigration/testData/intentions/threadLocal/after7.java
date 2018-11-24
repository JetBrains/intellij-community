// "Convert to ThreadLocal" "true"
class X {
    private final ThreadLocal<byte[]> bytes = ThreadLocal.withInitial(() -> new byte[10]);

  byte foo(byte b) {
    bytes.get()[0] = 1;
    foo(bytes.get()[1])
    return bytes.get()[2];
  }
}
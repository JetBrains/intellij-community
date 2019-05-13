import java.io.Serializable;

final class Expected {
  public static <P extends Serializable> Expected make(final P good) {
    return null;
  }
}

interface Serializer {
  <T> T deserialize(final Class<T> type);
}

class FooBar {
  <T extends Serializable> void invoke(final Class<T> rpc, final Serializer serializer) {
    Expected.make(serializer.<warning descr="Explicit type arguments can be inferred"><T></warning>deserialize(rpc));
  }
}


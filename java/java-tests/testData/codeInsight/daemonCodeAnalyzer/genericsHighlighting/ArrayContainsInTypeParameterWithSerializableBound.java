import java.io.Serializable;
abstract class Box<<warning descr="Type parameter 'T' is never used">T</warning> extends Serializable> {
  public static <V extends Serializable> Box<V> get(ByteArrayBox byteArrayBox) {
    return <warning descr="Unchecked cast: 'ByteArrayBox' to 'Box<V>'">(Box<V>) byteArrayBox</warning>;
  }
}

final class ByteArrayBox extends Box<byte[]> {}

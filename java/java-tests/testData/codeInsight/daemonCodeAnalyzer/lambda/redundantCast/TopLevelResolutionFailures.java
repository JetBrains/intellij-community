import java.io.Serializable;
abstract class Issue4 {

  public void merge(Descriptor<?> descriptor) {
    put((Descriptor<Serializable>) descriptor, get(descriptor));
  }

  public abstract <T extends Serializable> void put(Descriptor<T> key, T value);
  public abstract <T extends Serializable> T get(Descriptor<T> key);
}

class Descriptor<T extends Serializable> {}

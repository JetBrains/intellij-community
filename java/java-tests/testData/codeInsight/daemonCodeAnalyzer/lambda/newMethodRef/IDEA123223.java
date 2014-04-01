import java.util.List;
import java.util.function.Function;

class FluTr<K> {

  class Group {
    List<Authority> getAuthorities() {
      return null;
    }
  }

  class Authority {
    String getPermission() {
      return null;
    }
  }

  public void filterForPermission(final String permission) {
    transformAndConcat(Group::getAuthorities)
      .transform(Authority::getPermission)
      .contains(permission);
  }

  boolean contains(String f) {
    return false;
  }

  public final <T> FluTr<T> transform(Function<? super K,T> function) { return null; }
  public <T> FluTr<T> transformAndConcat(Function<? super Group,? extends Iterable<? extends T>> function) { return null; }

}

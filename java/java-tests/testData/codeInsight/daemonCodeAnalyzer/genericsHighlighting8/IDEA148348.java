
import java.util.Collection;


interface C<K extends String> extends Collection<K> {}

class Test {

  C<?> id;


  static <T extends CharSequence> T get(Collection<? extends T> id) {
    return null;
  }

  String getObj() {
    return get( id);
  }

}
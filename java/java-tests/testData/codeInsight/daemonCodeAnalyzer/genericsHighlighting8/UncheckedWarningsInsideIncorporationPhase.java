abstract class Group {

  public Group() {
  }

  public <T extends Category> T get(Key<T> key) {
    <error descr="Incompatible types. Found: 'Category', required: 'T'">return getCategory(key);</error>
  }

  public abstract <R extends Category<R>> R getCategory(Key<R> key);
}

interface Category<Tc extends Category> {
}

class Key<Tk extends Category> {
}

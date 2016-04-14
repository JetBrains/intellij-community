abstract class Group {

  public Group() {
  }

  public <T extends Category> T get(Key<T> key) {
    return getCategory<error descr="'getCategory(Key<R>)' in 'Group' cannot be applied to '(Key<T>)'">(key)</error>;
  }

  public abstract <R extends Category<R>> R getCategory(Key<R> key);
}

interface Category<Tc extends Category> {
}

class Key<Tk extends Category> {
}

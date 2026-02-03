import java.util.ArrayList;

class Arrays {
  static <T> ArrayList<T> asList(T... ts) {
    return null;
  }
}

class DDD {
    static {
        DDD ddd = new DDD();
        new ArrayList<DDD>(Arrays.asList(<caret>));
    }
}

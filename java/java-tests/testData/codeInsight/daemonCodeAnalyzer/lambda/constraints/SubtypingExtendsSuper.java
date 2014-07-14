import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class Sample {

  static List<String> getList(Function<Object, String> function, ArrayList<? super String> objects) {
    return transform(objects, new ArrayList<String>(), function);
  }

  static <R, S, T extends Collection<S>> T transform(Iterable<? extends R> oldCollection,
                                                     T newCollection,
                                                     Function<R, S> function) {

    return newCollection;
  }

  interface Function<X, Y> {
    Y apply(X input);
  }

}

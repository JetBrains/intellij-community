import java.util.*;

class Test {

  List<String> getList(Function<Object, String> function) {
        /*
         * When the first argument below is a raw type it turns red because IDEA thinks the return
         * type is Collection<>.  javac and Eclipse don't care
         */
    return  transform(new ArrayList(), new ArrayList<String>(), function);
  }

  <R, S, T extends Collection<S>> T transform(Iterable<? extends R> oldCollection, T newCollection, Function<R, S> function) {
    for (R r : oldCollection) {
      newCollection.add(function.apply(r));
    }
    return newCollection;
  }

  interface Function<X, Y> {
    Y apply(X input);
  }
}
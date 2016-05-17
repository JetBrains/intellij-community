// "Replace with lambda" "false"

import java.util.*;
import java.util.function.Function;

import static java.util.Collections.emptyList;

class Ambiguous {
  public void setRoots(List<String> roots) {}

  public static <T> List<T> concat(Iterable<? extends Collection<T>> list) {
    return new ArrayList<T>();
  }

  public static <T> List<T> concat(List<List<? extends T>> lists) {
    return new ArrayList<T>();
  }

  public static <T,V> List<V> map(Collection<? extends T> iterable, Function<T, V> mapping) {
    return emptyList();
  }

  public void anonymousToLambda(HashSet<String> modules) {
    setRoots(Ambiguous.concat(Ambiguous.map(modules, new Fun<caret>ction<String, List<String>>() {
                                @Override
                                public List<String> apply(String s) {
                                  return null;
                                }
                              })
    ));
  }

}
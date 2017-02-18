
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

class A {
  <T> void bar(List<T> root, LinkedHashSet<List<T>> list) {
    add<caret>IfNotNull(root, list);
  }

  private static <T> void addIfNotNull(T element, Collection<T> result) {
    nested(result, element);
  }


  private static <S> void nested(Collection<S> result, S element) {}
}
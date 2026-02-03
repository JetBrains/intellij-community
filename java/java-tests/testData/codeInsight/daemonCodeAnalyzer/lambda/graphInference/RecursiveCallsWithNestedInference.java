import java.util.*;
import java.util.stream.Collectors;

class Test {
  private <S> List<S> bar(List<S> list) {
    return null;
  }

  private <E> List<List<E>> foo(List<E> l) {
    foo(bar(l));
    final List<List<Object>> perms = foo(null);
    return null;
  }

  public static <E> List<E> pipe(E head, List<E> tail) {
    List<E> newList = new ArrayList<>(tail);
    newList.add(0, head);
    return newList;
  }
  public static <E> List<E> subtract(List<E> list, E e) {
    List<E> newList = new ArrayList<>(list);
    newList.remove(e);
    return newList;
  }
  public static <E> List<List<E>> perms(List<E> l) {
    return l.isEmpty()
           ? Collections.singletonList(Collections.emptyList())
           : l.stream().flatMap(h -> perms(subtract(l, h)).stream()
             .map(t -> pipe(h, t))).collect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(perms(Arrays.asList("a", "b", "c")));
  }
}
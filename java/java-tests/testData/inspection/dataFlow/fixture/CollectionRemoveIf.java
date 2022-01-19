import java.util.Collection;
import java.util.Collections;

class RemoveIf
{
  void unmodifiable(Collection<String> c) {
    c = Collections.unmodifiableCollection(c);
    c.<warning descr="Immutable object is modified">removeIf</warning>(x -> true);
  }

  void falsePredicate(Collection<String> c) {
    int size = c.size();
    c.removeIf(x -> false);
    if (<warning descr="Condition 'size == c.size()' is always 'true'">size == c.size()</warning>) {}
    c.removeIf(String::isEmpty);
    if (size == c.size()) {}
  }

  void empty(Collection<String> c) {
    if (!c.isEmpty()) return;
    c.removeIf(x -> c.add(x));
    if (<warning descr="Condition 'c.isEmpty()' is always 'true'">c.isEmpty()</warning>) {}
  }

  int x;

  void returnValueAndSideEffect(Collection<String> c) {
    x = 0;
    boolean ret = c.removeIf(v -> {
      x = 1;
      return v.isEmpty();
    });
    if (<warning descr="Condition 'x == 0 && ret' is always 'false'">x == 0 && <warning descr="Condition 'ret' is always 'false' when reached">ret</warning></warning>) {}
    if (x == 1 && ret) {}
  }
}
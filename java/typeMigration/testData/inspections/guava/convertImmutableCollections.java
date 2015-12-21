import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Comparator;

class CollectList {
  public <T> ImmutableList<T> toList(Fl<caret>uentIterable<T> p) { return p.toList(); }
  public <T> ImmutableList<T> toSortedList(F<caret>luentIterable<T> p, Comparator<T> c) { return p.toSortedList(c); }
  public <T> ImmutableSet<T> toSet(FluentItera<caret>ble<T> p) { return p.toSet(); }
  public <T> ImmutableSet<T> toSortedSet(<caret>FluentIterable<T> p, Comparator<T> c) { return p.toSortedSet(c); }
  public <T> ImmutableMap<T, T> toMap(FluentIte<caret>rable<T> p) { return p.toMap(new Function<T, T>() {
    @Override
    public T apply(T input) {
      return input;
    }
  }); }

  void m(Fl<caret>uentIterable<String> fluentIterable) {
    ImmutableMap<String, String> map = fluentIterable.toMap(new Function<String, String>() {
      @Override
      public String apply(String input) {
        return input;
      }
    });
    System.out.println(map.size());
    System.out.println(map.hashCode());
  }
}
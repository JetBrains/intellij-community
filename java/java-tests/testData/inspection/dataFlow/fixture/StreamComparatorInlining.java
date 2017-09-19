import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.*;

public class StreamComparatorInlining {
  static class Holder {
    @Nullable String nullable;

    @Nullable String getNullable() {
      return nullable;
    }
  }

  @Nullable String process(String s) {
    return s.isEmpty() ? null : s;
  }

  void testMinMax(List<String> list) {
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).max(Comparator.naturalOrder());
    list.stream().map(this::process).max(Comparator.nullsFirst(Comparator.naturalOrder()));
    list.stream().map(this::process).max(Comparator.nullsFirst(Comparator.comparing(String::length)));
    list.stream().map(this::process).min(Comparator.nullsLast(Comparator.comparing(String::length)));
    list.stream().map(this::process).min(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>));
    list.stream().map(this::process).min(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>).reversed());
  }

  void testSorted(List<String> list) {
    list.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">this::process</warning>, Comparator.reverseOrder())).collect(Collectors.toList());
    list.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">this::process</warning>)).collect(Collectors.toList());
    list.stream().sorted(Comparator.comparing(this::process, Comparator.nullsFirst(Comparator.naturalOrder()))).collect(Collectors.toList());
    list.stream().map(this::process).sorted(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>)).collect(Collectors.toList());
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).sorted().collect(Collectors.toList());
    list.stream().map(this::process).sorted(<warning descr="Method reference invocation 'String::compareToIgnoreCase' may produce 'java.lang.NullPointerException'">String::compareToIgnoreCase</warning>).collect(Collectors.toList());
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
  }

  void testSortedCheck(List<Holder> holders) {
    holders.stream().sorted(Comparator.comparing(h -> <warning descr="Function may return null, but it's not allowed here">h.nullable</warning>)).toArray();
    holders.stream().filter(h -> h.nullable != null).sorted(Comparator.comparing(h -> h.nullable)).toArray();

    holders.stream().sorted(Comparator.comparing(h -> <warning descr="Function may return null, but it's not allowed here">h.getNullable()</warning>)).toArray();
    holders.stream().filter(h -> h.getNullable() != null).sorted(Comparator.comparing(h -> h.getNullable())).toArray();

    holders.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">Holder::getNullable</warning>)).toArray();
    holders.stream().filter(h -> h.getNullable() != null).sorted(Comparator.comparing(Holder::getNullable)).toArray();
  }
}

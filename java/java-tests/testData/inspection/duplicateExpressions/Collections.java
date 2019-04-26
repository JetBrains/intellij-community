import java.util.*;

class C {
  void unmodifiableList(List<String> list) {
    List<String> u1 = <weak_warning descr="Multiple occurrences of 'Collections.unmodifiableList(list)'">Collections.unmodifiableList(list)</weak_warning>;
    List<String> u2 = <weak_warning descr="Multiple occurrences of 'Collections.unmodifiableList(list)'">Collections.unmodifiableList(list)</weak_warning>;
  }

  void unmodifiableMap(Map<String, Integer> map) {
    Map<String, Integer> u1 = <weak_warning descr="Multiple occurrences of 'Collections.unmodifiableMap(map)'">Collections.unmodifiableMap(map)</weak_warning>;
    Map<String, Integer> u2 = <weak_warning descr="Multiple occurrences of 'Collections.unmodifiableMap(map)'">Collections.unmodifiableMap(map)</weak_warning>;
  }

  void min(List<String> list) {
    String s1 = <weak_warning descr="Multiple occurrences of 'Collections.min(list)'">Collections.min(list)</weak_warning>;
    String s2 = <weak_warning descr="Multiple occurrences of 'Collections.min(list)'">Collections.min(list)</weak_warning>;
  }

  void max(List<String> list) {
    String s1 = <weak_warning descr="Multiple occurrences of 'Collections.max(list)'">Collections.max(list)</weak_warning>;
    String s2 = <weak_warning descr="Multiple occurrences of 'Collections.max(list)'">Collections.max(list)</weak_warning>;
  }

  void synchronizedList(List<String> list) {
    List<String> s1 = Collections.synchronizedList(list);
    List<String> s2 = Collections.synchronizedList(list);
  }
}
import java.util.Set;

class FooBar {
  Set<String> strings = null;
  {
    bar(strings.toArray(new String[strings.size()]));
  }

  void bar(String... a){}
}

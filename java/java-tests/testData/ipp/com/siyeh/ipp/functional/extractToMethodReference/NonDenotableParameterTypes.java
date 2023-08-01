import java.util.List;

class MyClass {


  void f(List<? extends String> l) {
    l.stream().filter(s -> s.substring(1).le<caret>ngth() > 0);
  }
}
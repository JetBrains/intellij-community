import java.util.Map;

class Test {
  void baz(){
    bar(new Map<error descr="Generic array creation not allowed"><?, Integer></error>[3]);
  }

  void bar(Map<?, Integer> ... x){ }
}
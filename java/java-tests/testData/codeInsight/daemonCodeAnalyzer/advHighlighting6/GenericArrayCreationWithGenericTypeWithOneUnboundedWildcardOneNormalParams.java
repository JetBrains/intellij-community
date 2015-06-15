import java.util.Map;

class Test {
  void baz(){
    bar(<error descr="Generic array creation">new Map<?, Integer>[3]</error>);
  }

  void bar(Map<?, Integer> ... x){ }
}
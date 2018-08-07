// "Wrap using 'String.valueOf()'" "false"
public class Test {
  void foo(String i) {
    switch (i) {
      case '<caret>0':
        System.out.println(i);
    }
  }
}

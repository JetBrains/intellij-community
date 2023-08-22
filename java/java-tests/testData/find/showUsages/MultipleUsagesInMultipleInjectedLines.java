// A B C D E F G H I J K L M N O P Q R S
// A B C D E F G H I J K L M N O P Q R S
public class MultipleUsagesInMultipleInjectedLines {
  void m() {
    String java = "class A { void f() { int <caret>a = 1;\n" +
                  "int b = a;\n" +
                  "int c = a; }}";
  }
}

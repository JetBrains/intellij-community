// A B C D E F G H I J K L M N O P Q R S
// A B C D E F G H I J K L M N O P Q R S
public class MultipleUsagesInOneInjectedLine {
  void m() {
    String java = "class A { void f() { int <caret>a = 1; int b = a; int c = a; }}";
  }
}

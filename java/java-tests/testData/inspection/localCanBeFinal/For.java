import java.util.*;
class For {
  void f(List<String> list) {
    for (Iterator<String>  <warning descr="Variable 'it' can have 'final' modifier">it</warning> = list.iterator(); it.hasNext();) {
    }
    for (int i = 0; i < 10; i++) {}
    for (int i = 0, length = 10; i < length; i++) {}
  }
}
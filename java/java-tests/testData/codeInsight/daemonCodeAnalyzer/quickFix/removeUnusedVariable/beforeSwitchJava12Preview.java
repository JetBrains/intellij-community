// "Remove variable 'i'" "true"
public class Main {
  void test(String s) {
    int <caret>i;
    switch(s) {
      case "foo" -> i = 1;
    }
  }
}
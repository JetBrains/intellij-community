// "Fix all 'My fake inspection on literal' problems in file" "true"
public class Test {
  void foo1() {
    String java = "class <caret>B {{}}";
  }
}
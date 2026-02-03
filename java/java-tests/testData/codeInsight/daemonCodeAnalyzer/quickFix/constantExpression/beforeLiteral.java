// "Compute constant value of '""" ...'" "false"
class Literal {
  void test() {
    System.out.println("""
        <caret>Hello""");
  }
}
class Simple {

  void f(String a, String b) {
    String s = "a:" +//a before arg
               a <caret>+ //comment
               "b:"//after literal
               + /*before arg*/b; //end comment

  }
}
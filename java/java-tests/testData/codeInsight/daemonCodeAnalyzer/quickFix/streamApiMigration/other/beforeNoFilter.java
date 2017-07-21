// "Replace with forEach" "false"
class Sample {
  void foo(It it){
    for (String s : i<caret>t) {
      if (s == null) {
      }
    }
  }
}

abstract class It implements Iterable<String> {} 
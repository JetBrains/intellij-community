// "Fix all 'Constant expression can be evaluated' problems in file" "false"
class IncompletePolyadic {
  
  void x() {
    System.out.println(100 * <caret>100 * );
  }
}
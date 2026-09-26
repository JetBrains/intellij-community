// "Fix all 'Constant expression can be evaluated' problems in file" "false"
class Test {
  double d = ((((<caret>0.1))));
  String s = ((((("asdf")))));
}
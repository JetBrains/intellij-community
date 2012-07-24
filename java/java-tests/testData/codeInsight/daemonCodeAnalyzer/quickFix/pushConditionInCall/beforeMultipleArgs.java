// "Push condition 'b' inside method call" "false"
class Foo {
  void bar(boolean b){
    String s = b <caret>? foo("true", true) : foo("false", false);
  }
  String foo(String p, boolean b) {return p;}
}
// "Push condition 'b' inside method call" "false"
class Foo {
  void bar(boolean b){
    String s = b <caret>? foo("true") : foo(false);
  }
  String foo(String p) {return p;}
  String foo(boolean b) {return "false";}
}
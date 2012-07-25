// "Push condition 'b' inside method call" "true"
class Foo {
  void bar(boolean b){
    String s = b <caret>? foo("true") : foo("false");
  }
  String foo(String p) {return p;}
}
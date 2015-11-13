// "Push condition 'b' inside method call" "true"
class Foo {
  void bar(boolean b){
    String s = b <caret>? foo("true", true).substring(1).substring(0) : foo("true", true).substring(2).substring(0);
  }
  String foo(String p, boolean b) {return p;}
}
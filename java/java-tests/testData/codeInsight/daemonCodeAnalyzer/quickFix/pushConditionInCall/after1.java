// "Push condition 'b' inside method call" "true"
class Foo {
  void bar(boolean b){
    String s = foo(b ? "true" : "false");
  }
  String foo(String p) {return p;}
}
// "Push condition 'b' inside method call" "true"
class Foo {
  void bar(boolean b){
    String s = foo(b ? "true" : "false", true).substring(0);
  }
  String foo(String p, boolean b) {return p;}
}
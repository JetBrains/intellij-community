// "Push condition 'b' inside method call" "true"
class Foo {
  void bar(boolean b){
    String s = foo("true", true).substring(b ? 1 : 2).substring(0);
  }
  String foo(String p, boolean b) {return p;}
}
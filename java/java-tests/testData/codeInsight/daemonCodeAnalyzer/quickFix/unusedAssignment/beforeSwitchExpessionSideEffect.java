// "Remove redundant initializer" "true-preview"
class A {
  void test2(){

    String str =  switch<caret>   (1) {
      case 1 -> "a";
      default ->
      {
        str = "b";
        System.out.println(str);
        yield str + "c";
      }
    };
  }
}
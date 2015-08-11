// "Generate overloaded method with default parameter value" "true"
class Test {
    int foo() {
      return foo();
  }

    int foo(int ii){
    //comment1
    System.out.println("");
    //comment2
    return 1;
  }
}
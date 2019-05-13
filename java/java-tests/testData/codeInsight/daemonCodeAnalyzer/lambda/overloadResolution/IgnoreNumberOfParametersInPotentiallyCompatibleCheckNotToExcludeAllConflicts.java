class BB {
  public boolean isVarArgCall() {
    return false;
  }

  private static boolean isVarArgCall(String method, String substitutor) {
    return false;
  }
}

class Test {
  void f(BB b){
    BB.<error descr="Non-static method 'isVarArgCall()' cannot be referenced from a static context">isVarArgCall</error>();
  }
}
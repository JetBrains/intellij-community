class Test {
  {
    I r = (i) -> {
        if (i > 0) return "a";
        return "b";
    };
  }
  interface I {
    String f(int i);
  } 
}
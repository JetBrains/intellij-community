class C {
  {
      String expr = "extract me";
      Runnable r = () ->  System.out.println(expr);
  }
}
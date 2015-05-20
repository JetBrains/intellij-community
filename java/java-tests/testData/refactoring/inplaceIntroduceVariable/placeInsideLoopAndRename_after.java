class C {
    {
      Runnable r = () -> {
          String expr = "extract me";
          System.out.println(expr);
      };
    }
}
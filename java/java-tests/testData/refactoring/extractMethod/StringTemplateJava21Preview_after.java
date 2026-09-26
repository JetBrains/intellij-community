class Main {

  public static void x(StringTemplate.Processor<String, RuntimeException> p, String s) {
      newMethod(p, s);
  }

    private static void newMethod(StringTemplate.Processor<String, RuntimeException> p, String s) {
        String t = p."\{s}";
    }
}
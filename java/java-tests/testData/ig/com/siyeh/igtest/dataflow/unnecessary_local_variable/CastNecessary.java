class Scratch {
  public static void main(String... arguments) {
    String <warning descr="Local variable 's' is redundant">s</warning> = arguments[0];
    Object o = s;
    use(o);
  }

  private static void use(Object objectionable) {
    System.out.println("Objection!");
  }

  private static void use(String s) {
    System.out.println("Strings are like ropes.  Threads are like fibers");
  }
}
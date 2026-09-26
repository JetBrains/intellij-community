record Value(String name, boolean valid) {

  static void x(Value v) {
    System.out.println(v.name);
  }
}
class User {

  public static void main(String[] args) {
    System.out.println(new Value("dubious", false).name());
  }
}
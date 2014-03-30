class X {
  public static void main(String[] args) {
    int primitive = 1024;
    Object object = primitive;
    System.out.println(primitive == object);
  }
}
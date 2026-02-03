class X {
  void test() {
    Object obj1 = 100000L;
    Object obj2 = 100000L;
    if(obj1 == obj2) {}
  }

  public static void main(String[] args) {
    int primitive = 1024;
    Object object = primitive;
    System.out.println(primitive == object);
  }
}
class X {
  void test() throws NoSuchMethodException {
    Class<?>[] params = new Class<?>[3];
    params[0] = byte[].class;
    params[1] = int.class;
    params[2] = int.class;
    String.class.getDeclaredMethod("checkBounds", params); 
    Class[] params2 = {byte[].class, int.class, int.class};
    String.class.getDeclaredMethod("checkBounds", params2);
    String.class.getDeclaredMethod("checkBounds", byte[].class, int.class, int.class);
  }
}
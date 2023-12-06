class No {
  void f(Class<?> c) {
    try {
        c.getConstructor().newInstance();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
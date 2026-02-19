class Low {

  void g(Class<?> x) throws IllegalAccessException, InstantiationException {
    x.<caret>newInstance();
  }
}
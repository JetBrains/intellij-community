class SubClient extends Client {
  void method (final Factory factory) {
    Product product = factory.create();
    System.out.println (product);
  }
} 

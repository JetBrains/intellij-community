class SubClient extends Client {
  void method () {
    Product product = factory.create();
    System.out.println (product);
  }
} 

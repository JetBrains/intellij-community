class SubClient extends Client {
  void method () {
    Product product = getFactory().create();
    System.out.println (product);
  }
} 

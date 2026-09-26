class Client {
  protected Factory factory =
    new Factory() {
      public Product create () {
        return new Product("created");
      }
    };  
}

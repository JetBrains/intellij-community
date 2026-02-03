class Client {
    protected Factory getFactory () {
      return
        new Factory() {
          public Product create () {
            return new Product("created");
          }
        };  
    }
}

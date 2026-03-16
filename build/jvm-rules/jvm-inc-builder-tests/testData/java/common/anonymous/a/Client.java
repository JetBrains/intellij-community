class Client{
  void foo(){
    Runnable runnable = new Runnable(){
      public void run(){
        Server.foo();
      }
    };
  }
}
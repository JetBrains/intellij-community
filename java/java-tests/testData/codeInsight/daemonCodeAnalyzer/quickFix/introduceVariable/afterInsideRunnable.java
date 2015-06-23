// "Introduce local variable" "true"
class a {
  void a() {
    list.add(new Runnable(){
      @Override
      public void run() {
          int i = Integer.parseInt("10");
      }
    })
  }
}


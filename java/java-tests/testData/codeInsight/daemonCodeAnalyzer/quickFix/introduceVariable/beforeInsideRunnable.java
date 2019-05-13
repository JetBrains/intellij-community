// "Introduce local variable" "true"
class a {
  void a() {
    list.add(new Runnable(){
      @Override
      public void run() {
        Integer.parseInt("10")<caret>
      }
    })
  }
}


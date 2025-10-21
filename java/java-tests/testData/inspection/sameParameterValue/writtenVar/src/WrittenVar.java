class WrittenVar {

  void method1(String text){
    text = text + "!";
    System.out.println(text);
  }

  void method2(){
    method1("ABC");
  }
}
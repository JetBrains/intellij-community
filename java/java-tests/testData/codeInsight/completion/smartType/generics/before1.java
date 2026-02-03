class A<T>{

  T get(){return null;}
  void put(String str);

  {
    put(new A<String>().ge<caret>)
  }
}

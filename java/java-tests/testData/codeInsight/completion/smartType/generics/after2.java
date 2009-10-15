class A<T>{

  T get(){return null;}
  void put(T str);

  {
    new A<String>().put(new A<String>().get());<caret>
  }
}

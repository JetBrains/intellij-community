class A<T>{

  T get(){return null;}
  <T> void put(T str);

  {
    Object obj;
    put(obj);<caret>
  }
}

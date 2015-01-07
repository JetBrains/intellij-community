// "Create method 'fooBar'" "true"
class FooBar {
  {
    Runnable r = Container<String>::fooBar;
  }
}
class Container<T>{
    public static void fooBar() {
        
    }
}

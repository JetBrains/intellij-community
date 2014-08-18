@interface Example {

  public <error descr="Modifier 'static' not allowed here">static</error> String myMethod() {
    return "";
  }
  public <error descr="Modifier 'default' not allowed here">default</error> String myMethod1() {
    return "";
  }
}

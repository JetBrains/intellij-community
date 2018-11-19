class Test{
  short foo(){
    short v = 0;
    short s = (<warning descr="Casting 'v' to 'short' is redundant">short</warning>)v;
    return s;
  }
}
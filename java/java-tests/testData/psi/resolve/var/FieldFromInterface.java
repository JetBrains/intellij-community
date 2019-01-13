class Client {
  int foo(){
    return A.<caret>FIELD;
  }
}

class A implements I{
}

interface I {
  public static final int FIELD = 1;
}

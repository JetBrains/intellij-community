import p.*;

class Main {
  public static void main() {
    <warning descr="Qualifier 'p' is unnecessary and can be removed">p</warning>.Date a = new <warning descr="Qualifier 'p' is unnecessary and can be removed">p<caret></warning>.Date();
  }
}


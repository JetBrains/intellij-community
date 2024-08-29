import module java.base;
import p.*;

class Main {
  public static void main() {
    <warning descr="Qualifier 'p' is unnecessary, and can be replaced with an import">p</warning>.Date a = new <warning descr="Qualifier 'p' is unnecessary, and can be replaced with an import">p</warning>.Date();
  }
}


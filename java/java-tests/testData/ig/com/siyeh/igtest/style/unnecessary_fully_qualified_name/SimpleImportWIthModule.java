import module java.base;

class Main {
  public static void main() {
    <warning descr="Qualifier 'java.util' is unnecessary and can be removed">java.<caret>util</warning>.Date date = new <warning descr="Qualifier 'java.util' is unnecessary and can be removed">java.util</warning>.Date();
  }
}


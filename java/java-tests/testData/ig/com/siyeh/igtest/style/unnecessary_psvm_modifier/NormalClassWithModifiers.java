package test;

public class NormalClassWithModifiers {
  <warning descr="Modifier 'public' is redundant for 'main' method on Java 23-preview">public</warning> static void main(String[] args) {
    System.out.println("Hello World!");
  }


  public static int main() {
    System.out.println("Hello World!");
    return 1;
  }
}
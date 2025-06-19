import p.*;

<warning descr="Explicit class declaration can be converted into a compact source file">public class beforeWithImportConflic<caret>t</warning>  {

  public static void main(String[] args) {
    List a = null;
    System.out.println("Hello, world!" + args);
  }
}

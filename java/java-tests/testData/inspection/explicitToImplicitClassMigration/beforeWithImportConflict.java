import p.*;

<warning descr="Explicit class declaration can be converted into implicitly declared class">public class beforeWithImport<caret>Conflict</warning>  {

  public static void main(String[] args) {
    List a = null;
    System.out.println("Hello, world!" + args);
  }
}

public class InterfaceConflict3{
  public interface A{
    int a = 0;
  }

  public interface B{
    int a = 0;
  }

  public class E implements A{
  }

  public static class C extends E
  implements B{
    static{
      System.out.println("" + <ref>a);
    }
  }
}
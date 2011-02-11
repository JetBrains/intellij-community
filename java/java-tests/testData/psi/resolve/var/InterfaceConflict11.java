public class InterfaceConflict11{
  public static class A{
    static int a = 0;
  }

  public static class B extends A{
    int a = 0;
  }

  public static class C extends B{
    static void foo(){
      System.out.println("" + <ref>a);
    }
  }
}
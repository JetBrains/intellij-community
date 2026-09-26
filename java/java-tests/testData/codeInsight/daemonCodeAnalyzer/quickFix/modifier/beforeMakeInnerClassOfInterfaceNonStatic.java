// "Make 'Inner' not static" "false"
interface MemberClassOfAnInterface {

  int f();

  public static class Inner {
    static void main() {
      System.out.println("Hello World!");
    }
  }

  static void main() {
    MemberClassOfAnInterface.Inner inner = new MemberClassOfAnInterface(){
      @Override
      public int f() {
        return 0;
      }
    }.new Inner<caret>(); // <- Error
  }
}
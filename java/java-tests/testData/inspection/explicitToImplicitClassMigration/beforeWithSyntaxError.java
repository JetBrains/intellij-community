public class beforeWi<caret>thSyntaxError  {

  public static void main(String[] args) {
    <error descr="Cannot resolve symbol 'error'">error</error> error<error descr="';' expected"> </error><error descr="Variable 'error' might not have been initialized">error</error>;
    System.out.println("Hello, world!");
  }
}

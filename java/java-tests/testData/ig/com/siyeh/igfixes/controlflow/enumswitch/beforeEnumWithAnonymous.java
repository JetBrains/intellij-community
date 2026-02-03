// "Create missing branches 'A', 'B', and 'C'" "true"
class Scratch
{
  public enum EnumWithCode {
    A {},
    B,
    C;
  }

  public static void main(String[] args)
  {
    final EnumWithCode x = null;
    System.out.println( switch(<caret>x) {
    });
  }
}
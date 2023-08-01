class InnerClassVariableHidesOuter {

  private static final int serialVersionUID = -1;
  private int a = 1;
  private static final char B = '1';

  private static class Inner1 {
    private int a = 2;
    private char <warning descr="Inner class field 'B' hides outer class field">B</warning> = '2';
    private static final int serialVersionUID = -1;
  }
  private class Inner2 {
    private int <warning descr="Inner class field 'a' hides outer class field">a</warning> = 3;
    private char <warning descr="Inner class field 'B' hides outer class field">B</warning> = '3';
    private static final int serialVersionUID = -1;
  }
  private class Inner3 {
    private static final char <warning descr="Inner class field 'B' hides outer class field">B</warning> = '4';
    private static final int <warning descr="Inner class field 'a' hides outer class field">a</warning> = 4;
    private static final int serialVersionUID = -1;
  }
}
class Test {

  Object field;

  private static class InnerStaticClass {

    private class InnerClass {

      Object field;
    }
  }
}
class IdeaBloopers13
{
  public static final IdeaBloopers13 staticField1 = new IdeaBloopers13()
  {
    //IdeaBloopers13.this.stringValue = null; <-- Java error: non-static variable this cannot be referenced from a static context
    String stringValue = ""; //IntellijIdea inspection: Inner class field 'stringValue' hides outer class field
  };

  private String stringValue;
}
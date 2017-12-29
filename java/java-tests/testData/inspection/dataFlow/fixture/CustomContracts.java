public class CustomContracts {
  public void testSubstring(String s) {
    if (s.<warning descr="The call to 'substring' always fails as index is out of bounds">substring</warning>(-1).length() == 0) {
      System.out.println("Oops");
    }
  }

  public void testSubstring2(String s, int index) {
    if (s.substring(0, index).length() == 0 || <warning descr="Condition 'index < 0' is always 'false' when reached">index < 0</warning>) {
      System.out.println("Oops");
    }
  }

  public void testSubstring3(String s, int index) {
    if (s.substring(3, index).equals("foo") || <warning descr="Condition 'index == 1' is always 'false' when reached">index == 1</warning>) {
      System.out.println("Oops");
    }
  }

  public void testCharAt(String s) {
    int index = 0;
    while (Character.isDigit(s.charAt(index))) {
      index++;
    }

    if (index == 0 || <warning descr="Condition 'index == s.length()' is always 'false' when reached">index == s.length()</warning>) {
      System.out.println("Wrong");
    }
  }
}

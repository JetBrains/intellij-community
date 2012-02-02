public class Test {
  <fold text='My private fields'>// <editor-fold desc="My private fields">
  private int x;
  private int y;
  // </editor-fold></fold>

  <fold text='My methods'>// <editor-fold desc="My methods">
  private void doSomething() <fold text='{...}'>{
    StringBuilder s = new StringBuilder();
    <fold text='My code'>// <editor-fold desc="My code">
    s.append("a");
    s.append("b");
    // </editor-fold></fold>
    System.out.println(s.toString());
  }</fold>
  // </editor-fold></fold>
}
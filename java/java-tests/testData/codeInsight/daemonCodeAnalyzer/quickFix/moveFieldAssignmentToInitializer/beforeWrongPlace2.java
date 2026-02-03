// "Move assignment to field declaration" "false"
public class X {
    int i;
    void f()
    {
      <caret>i=0;
    }
    {
      i=1;
    }
}

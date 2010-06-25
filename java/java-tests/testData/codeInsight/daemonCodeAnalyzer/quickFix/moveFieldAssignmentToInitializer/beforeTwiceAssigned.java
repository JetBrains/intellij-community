// "Move assignment to field declaration" "false"
public class X {
    int i;
    {
      i = 0;
      i = <caret>2;
    }
}

// "Move assignment to field declaration" "false"
public class X {
    int i=7;
    {
      i=<caret>0;
    }
}

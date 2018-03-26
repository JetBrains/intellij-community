// "Move assignment to field declaration" "true"
public class X {
  int i;

  // comment
  {
    (<caret>i)=0;
  }
}

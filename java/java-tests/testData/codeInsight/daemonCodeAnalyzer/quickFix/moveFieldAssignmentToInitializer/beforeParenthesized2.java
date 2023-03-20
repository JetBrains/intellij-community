// "Move assignment to field declaration" "true-preview"
public class X {
  int i;

  // comment
  {
    (<caret>i)=0;
  }
}

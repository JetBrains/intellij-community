// "Use lombok @Getter for 'bar'" "true"

public class QualifiedClass {
  private int bar;
  private int fieldWithoutGetter;

  public int getBar() {
    //Keep this comment
    return QualifiedClass.this.bar<caret>;
  }
}
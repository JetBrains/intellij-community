public class EscalateVisibility {
  public void x(B b){
    yy(b);
  }
  private void y<caret>y(B b){
  }
}

class B {}
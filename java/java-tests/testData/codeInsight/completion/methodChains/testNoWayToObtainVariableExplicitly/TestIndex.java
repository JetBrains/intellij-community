
//
// if component obtained in implicit way
//

public class TestIndex {


  //
  // simple test for spring like configuration
  //

  private PsiManager a;

  public void setA(PsiManager a) {
    this.a = a;
  }

  void m() {
    a.getProject();
    a.getProject();
  }

  void b() {
    a.getProject();
  }

  void c() {
    a.getProject();
  }

  void c22() {
    a.getProject();
  }

  void c11() {
    a.getProject();
  }

  void c1() {
    a.getProject();
  }
}

class PsiManager {
  Project getProject() {
    return null;
  }
}
class Project {}


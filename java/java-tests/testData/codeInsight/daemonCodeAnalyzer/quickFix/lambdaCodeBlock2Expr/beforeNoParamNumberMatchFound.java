// "Replace with expression lambda" "false"
class IdeaSetterArgsShouldBeFinal {

  interface I {
    void m1();
  }

  interface J {
    void m2();
  }

  <L> void foo(I i, J j1, J... j){}
  <K> void foo(I o, I i1, I... i){}

  {
    foo(() -> {
      System.ou<caret>t.println("");
    });
  }

}
// "Replace lambda with method reference" "true"
class Example {
  interface I {
      void foo(int i);
    }
  
    void m(int i) {}
  
    {
      I i = (i1) -> <caret>m(i1);
    }
}
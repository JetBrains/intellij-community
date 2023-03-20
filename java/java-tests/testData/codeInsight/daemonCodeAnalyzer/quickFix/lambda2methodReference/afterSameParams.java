// "Replace lambda with method reference" "true-preview"
class Example {
  interface I {
      void foo(int i);
    }
  
    void m(int i) {}
  
    {
      I i = this::m;
    }
}
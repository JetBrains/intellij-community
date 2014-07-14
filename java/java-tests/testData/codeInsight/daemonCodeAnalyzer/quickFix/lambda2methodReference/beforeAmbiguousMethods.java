// "Replace lambda with method reference" "true"
class Example {
    interface I {
      String foo(Integer i);
    }
  
    {
      I i = (i1) -> i1.<caret>toString();
    }
}
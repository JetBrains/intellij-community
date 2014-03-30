// "Replace lambda with method reference" "true"
class Example {
    interface I {
      String foo(Integer i);
    }
  
    {
      I i = Object::toString;
    }
}
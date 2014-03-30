// "Add Exception to Method Signature" "true"
class C {
  interface I {
    void a();
  }

  {
    I i = () -> {
      Thread.sl<caret>eep(2000);
    };
  }
}

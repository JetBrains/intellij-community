// "Add exception to method signature" "true"
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

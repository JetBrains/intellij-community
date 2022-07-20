// "Add exception to method signature" "true-preview"
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

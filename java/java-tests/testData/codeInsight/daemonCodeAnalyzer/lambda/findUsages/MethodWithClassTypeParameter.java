interface I {
  void hmmm();
}

class Test<T extends I> {

  void someUnlikelyName(T action) {
  }

  {
    someUnlikelyName(() -> {});
  }
}
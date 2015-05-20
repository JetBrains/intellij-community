// "Create inner class 'AInner'" "true"
class Test {
  {
    AInner aInner = new AInner<String, String>(42);
  }

    private class AInner<T, T1> {
        public AInner(int i) {
        }
    }
}
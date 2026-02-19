// "Create inner class 'AInner'" "true-preview"
class Test {
  {
    AInner aInner = new AInner<String, String>(42);
  }

    private class AInner<T, T1> {
        public AInner(int i) {
        }
    }
}
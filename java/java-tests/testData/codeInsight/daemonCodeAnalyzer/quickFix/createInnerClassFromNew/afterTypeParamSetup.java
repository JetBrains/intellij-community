// "Create inner class 'AInner'" "true-preview"
class Test {
  {
    AInner aInner = new AInner<String>(42);
  }

    private class AInner<T> {
        public AInner(int i) {
        }
    }
}
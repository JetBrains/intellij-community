class Test {
  {
    int i = 0;
    double[] doubles = null;

      NewMethodResult x = newMethod(doubles, i);
  }

    NewMethodResult newMethod(double[] doubles, int i) {
        double progressResult = doubles[0] / i;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
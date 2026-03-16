class MathClampBraveMode {

  void test(int[] val, int lo, int hi) {
    val[0] = ((Math.clamp(val[0], lo, hi)));
    System.out.println(val);
  }
  
  int myClamp(int input, int min, int max) {
    input = Math.clamp(input, min, max);
    return input;
  }

  float myClampButTheOrderOfArgsIsDifferent(float input, float min, float max) {
    input = Math.clamp(input, min, max);
    return input;
  }


  int fakeClamp(int input, int a, int b) {
    return Math.clamp(b, a, input);
  }

}
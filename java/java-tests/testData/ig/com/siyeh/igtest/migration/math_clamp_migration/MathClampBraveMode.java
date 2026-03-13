class MathClampBraveMode {

  void test(int[] val, int lo, int hi) {
    val[0] = ((<warning descr="Can be replaced with 'Math.clamp()'">Math.min(hi, Math.max(lo, val[0]))</warning>));
    System.out.println(val);
  }
  
  int myClamp(int input, int min, int max) {
    input = <warning descr="Can be replaced with 'Math.clamp()'">Math.<caret>min(max, Math.max(min, input))</warning>;
    return input;
  }

  float myClampButTheOrderOfArgsIsDifferent(float input, float min, float max) {
    input = <warning descr="Can be replaced with 'Math.clamp()'">Math.max(min, Math.min(max, input))</warning>;
    return input;
  }


  int fakeClamp(int input, int a, int b) {
    return <warning descr="Can be replaced with 'Math.clamp()'">Math.max(a, Math.min(b, input))</warning>;
  }

}
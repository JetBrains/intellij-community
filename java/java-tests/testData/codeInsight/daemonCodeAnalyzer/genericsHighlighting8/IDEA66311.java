import java.util.*;

class ErasureTest {
  <error descr="'toArrayDouble(List<? extends Number>)' clashes with 'toArrayDouble(List<double[]>)'; both methods have same erasure">public static double[] toArrayDouble(List<? extends Number> v)</error> {
    return null;
  }

  public static double[][] toArrayDouble(List<double[]> v) {
    return null;
  }
}

class ErasureTest1 {
  <error descr="'toArrayDouble(List<? extends Number>)' clashes with 'toArrayDouble(List)'; both methods have same erasure">public static double[] toArrayDouble(List<? extends Number> v)</error> {
    return null;
  }

  public static double[][] toArrayDouble(List v) {
    return null;
  }
}

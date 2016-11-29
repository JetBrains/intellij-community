import p.AccountCalculator;
import p.ObjectParameterDescriptor;

import java.io.IOException;

class Test {
  public static void calc(AccountCalculator calculator,
                          ObjectParameterDescriptor<String>.Holder holder) throws IOException{
    calculator.getMetrics(holder);
  }
}
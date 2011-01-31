// "Replace with <>" "true"
public class Test {
  F<F<String>> f = new FF<>();
}

class FF<X> extends F<X>{}
class F<T> {}
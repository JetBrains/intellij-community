import com.google.common.base.Function;
import com.google.common.base.Optional;

class A {
  public Optional<String> transform(Optional<Integer> p<caret>1) {
    return p1.transform(new MyFunction());
  }

  class MyFunction implements Function<Integer, String> {
    @Override
    public String apply(Integer input) {
      return null;
    }

    public String apply2(Integer input) {
      return null;
    }


    @Override
    public boolean equals(Object object) {
      return false;
    }
  }

}

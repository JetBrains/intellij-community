import com.google.common.base.Function;

import java.util.Optional;

class A {
  public Optional<String> transform(Optional<Integer> p1) {
    return p1.map(new MyFunction()::apply);
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

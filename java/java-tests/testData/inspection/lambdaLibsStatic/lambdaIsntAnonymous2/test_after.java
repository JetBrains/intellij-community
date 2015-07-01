import com.google.common.collect.Iterables;
import com.google.common.base.Predicate;

import java.lang.String;
import java.util.Collections;

class c {
  void m() {
    Collections.<String>emptyList().stream().allMatch(getPredicate(100)::apply);
  }

  public Predicate<String> getPredicate(final int param) {
    final MyComplexPredicate predicate = new MyComplexPredicate(param);
    predicate.setParam2(200);
    return predicate;
  }

  class MyComplexPredicate extends Predicate<String> {
    int param;
    int param2;

    public MyComplexPredicate(int param) {
      this.param = param;
    }

    public void setParam2(int param2) {
      this.param2 = param2;
    }

    @Override
    public boolean apply(String input) {
      System.out.println("lambda param " + param);
      doMagic();
      return false;
    }

    private void doMagic() {
      //do something
    }
  }
}
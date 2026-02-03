import java.util.ArrayList;
import java.util.List;

public class LambdaExpressions {

  public void t(){}

  public void test() {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 100; ++i) {
      list.add(i);
    }

    list.forEach(n -> test());

    int n = 0;
    list.forEach(m -> { if (n % 2 == 0) test(); });

    list.forEach(m -> { if (n % 2 != 0) test(); });

    list.forEach(m -> {
      if (n % 2 != 0) {
        System.out.println(n);
      }
      else{
        test();
      }
    });

    list.forEach(m -> {
      if (n % 2 == 0) {
        System.out.println(n);
      }
      else{
        test();
      }
    });

    list.forEach(m -> {
      if (n % 2 != 0) {
        System.out.println(n);
      }
      else{
        test();
        test();
      }
    });
  }
}


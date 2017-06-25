import java.util.List;
import java.util.function.Supplier;

class BaseTest {
  protected static java.util.List ourList;
}

class MyTest extends BaseTest {
    {
        Supplier<List> listSupplier = () -> ourList;
        listSupplier.get()
  }
}
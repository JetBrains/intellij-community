import java.util.function.Supplier;

class MyTest {

  public static final Inh values = () -> "";
}

interface Inh extends Supplier {
}

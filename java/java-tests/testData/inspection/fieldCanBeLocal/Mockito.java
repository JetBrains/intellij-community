import org.mockito.Mock;
import org.mockito.Mockito;

class Test {
  @Mock
  private Integer <warning descr="Field can be converted to a local variable">i</warning>;

  void init() {
    i = Mockito.mock(Integer.class);

    System.out.println(i.byteValue());
  }
}
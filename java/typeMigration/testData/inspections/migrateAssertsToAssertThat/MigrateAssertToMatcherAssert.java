import org.junit.Assert;
import java.util.Collection;

public class MigrateAssertToMatcherAssert {
  void m() {
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 != 3);
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 == 3);
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 > 3);
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 < 3);
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 >= 3);
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 <= 3);

    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 != 3);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 == 3);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 > 3);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 < 3);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 >= 3);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 <= 3);
  }

  void m2() {
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".equals("zxc"));
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd" == "zxc");
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".contains("qwe"));
  }

  void m3(Collection<String> c, String o) {
    Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(c.contains(o));
    Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>(c, o);
    Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>("msg", c, o);
    Assert.<warning descr="Assert expression 'assertNotNull' can be replaced with 'assertThat()' call">assertNotNull</warning>(c);
    Assert.<warning descr="Assert expression 'assertNull' can be replaced with 'assertThat()' call">assertNull</warning>(c);
    Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(c.contains(o));
  }

  void m(int[] a, int[] b) {
    Assert.<warning descr="Assert expression 'assertArrayEquals' can be replaced with 'assertThat()' call">assertArrayEquals</warning>(a, b);
  }
}
import org.junit.Assert;
import java.util.Collection;

public class TestCase {
  void m() {
    Assert.assertTrue(2 != 3);
    Assert.assertTrue(2 == 3);
    Assert.assertTrue(2 > 3);
    Assert.assertTrue(2 < 3);
    Assert.assertTrue(2 >= 3);
    Assert.assertTrue(2 <= 3);

    Assert.assertFalse(2 != 3);
    Assert.assertFalse(2 == 3);
    Assert.assertFalse(2 > 3);
    Assert.assertFalse(2 < 3);
    Assert.assertFalse(2 >= 3);
    Assert.assertFalse(2 <= 3);
  }

  void m2() {
    Assert.assertTrue("asd".equals("zxc"));
    Assert.assertTrue("asd" == "zxc");
    Assert.assertTrue("asd".contains("qwe"));
  }

  void m3(Collection c, Object o) {
    Assert.assertTrue(c.contains(o));
    Assert.assertEquals(c, o);
    Assert.assertEquals("msg", c, o);
    Assert.assertNotNull(c);
    Assert.assertNull(c);
  }

  void m(int[] a, int[] b) {
    Assert.assertArrayEquals(a, b);
  }
}
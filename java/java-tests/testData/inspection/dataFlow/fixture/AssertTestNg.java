import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.AssertJUnit;

class AssertTestNg {
  public void assertTestNgStyle(@Nullable String msg, @Nullable String obj, boolean x, boolean y)
  {
    Assert.assertNotNull(obj, msg);
    if (msg == null) {
      System.out.println("msg is null");
    }
    if (<warning descr="Condition 'obj == null' is always 'false'">obj == null</warning>) {
      System.out.println("obj is null");
    }
    Assert.assertTrue(x);
    if (<warning descr="Condition 'x' is always 'true'">x</warning>) {
      System.out.println("x is true");
    }
    Assert.assertTrue(y, msg);
    if (<warning descr="Condition 'y' is always 'true'">y</warning>) {
      System.out.println("x is true");
    }
  }

  public void assertJUnitStyle(@Nullable String msg, @Nullable String obj, boolean x, boolean y)
  {
    AssertJUnit.assertNotNull(msg, obj);
    if (msg == null) {
      System.out.println("msg is null");
    }
    if (<warning descr="Condition 'obj == null' is always 'false'">obj == null</warning>) {
      System.out.println("obj is null");
    }
    AssertJUnit.assertTrue(x);
    if (<warning descr="Condition 'x' is always 'true'">x</warning>) {
      System.out.println("x is true");
    }
    AssertJUnit.assertTrue(msg, y);
    if (<warning descr="Condition 'y' is always 'true'">y</warning>) {
      System.out.println("x is true");
    }
  }
}
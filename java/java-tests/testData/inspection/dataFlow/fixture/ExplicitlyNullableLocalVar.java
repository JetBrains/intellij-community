import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

abstract class IDEATest {
  abstract Object someMethod(@NotNull Object someParam);
  abstract Object someObject();

  public void testIDEA() {
    @Nullable Object obj2 = someObject();
    someMethod(<warning descr="Argument 'obj2' might be null">obj2</warning>);
  }
}
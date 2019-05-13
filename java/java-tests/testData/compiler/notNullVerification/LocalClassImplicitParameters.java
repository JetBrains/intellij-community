import java.lang.annotation.*;
import java.io.*;

@Target({ElementType.TYPE_USE, ElementType.PARAMETER})
@interface NotNull {}

public class LocalClassImplicitParameters {

  private void foo(String foo, final Integer bar) {
    class Test {
      private Test(@NotNull String test) {
        if (test == null) {
          System.out.println(bar);
        }
      }
    }

    new Test(foo);
  }

  public int ok() {
    foo("a", 2);
    create2NotNull("a", "b", null, null, 0, 1, 2, null);
    createNullableNotNull(null, "b", null, null, 0, 1, 2, null);
    return 42;
  }

  public void failLocal() {
    foo(null, null);
  }



  public void failLocal2NotNull() {
    create2NotNull(null, null, null, null, 0, 1, 2, null);
  }

  private void create2NotNull(String arg1, String arg2, File vFile, File breakOn, int inclusionLevel, int afterOffset, int beforeOffset, Object changeSet) {
    class Test2 implements Serializable {
      Test2(@NotNull String test, @NotNull String another) {}
      String some() {
        return " " + vFile + breakOn + inclusionLevel + changeSet + afterOffset + beforeOffset;
      }
    }

    new Test2(arg1, arg2).some();
  }



  public void failLocalNullableNotNull() {
    createNullableNotNull("a", null, null, null, 0, 1, 2, null);
  }

  private void createNullableNotNull(String arg1, String arg2, File vFile, File breakOn, int inclusionLevel, int afterOffset, int beforeOffset, Object changeSet) {
    class Test3 implements Serializable {
      Test3(String test, @NotNull String another) {}
      String some() {
        return " " + vFile + breakOn + inclusionLevel + changeSet + afterOffset + beforeOffset;
      }
    }
    new Test3(arg1, arg2).some();
  }


  public void failAnonymous() {
    new _Super("a") {
      void method(@NotNull java.util.List<?> test){}
    }.method(null);
  }

  public void failInner() {
    new Inner(null, "b");
  }

  private class Inner {
    Inner(@NotNull String param, @NotNull String param2) {
    }
  }
}

class _Super {
  _Super(@NotNull String f) {}
}
// "Create missing switch branches with null branch" "false"
import org.jetbrains.annotations.NotNull;

class Test {
  sealed interface SA permits R1, R2 {
  }

  record R1() implements SA {
  }

  record R2() implements SA {
  }


  public void test(@NotNull SA sa) {
    switch (sa<caret>) {
    }
  }
}
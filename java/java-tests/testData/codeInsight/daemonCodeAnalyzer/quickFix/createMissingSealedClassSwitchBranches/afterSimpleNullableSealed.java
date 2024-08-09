// "Create missing switch branches with null branch" "true-preview"
import org.jetbrains.annotations.Nullable;

class Test {
  sealed interface SA permits R1, R2 {
  }

  record R1() implements SA {
  }

  record R2() implements SA {
  }


  public void test(@Nullable SA sa) {
    switch (sa) {
        case R1 r1 -> {
        }
        case R2 r2 -> {
        }
        case null -> {
        }
    }
  }
}
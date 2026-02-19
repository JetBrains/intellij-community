import org.jetbrains.annotations.Nullable;

class TestExample2 {
  public static void main(String[] args) {
    getII(new RI(new RI2(null)));
  }

  sealed interface II {
    record AI() implements II {
    }

    record BI() implements II {
    }
  }

  record RI(RI2 value) {
  }

  record RI2(@Nullable II value) {
  }

  public static II getII(RI ri) {
    return switch (ri) {
      case RI(RI2(<warning descr="Pattern matching may throw 'MatchException'">II.BI bi</warning>)) -> bi;
      case RI(RI2(II.AI ai)) -> ai;
    };
  }
}


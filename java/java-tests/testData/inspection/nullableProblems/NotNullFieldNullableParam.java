import org.jetbrains.annotations.*;

class Test {
      @NotNull
      String text;
  
      public Test(@Nullable String a)
      {
          if (a == null)
              this.text = "test";
          else
              this.text = a;
      }
}
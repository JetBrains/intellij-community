import org.jetbrains.annotations.*;

class Test {
     @Nullable private final String baseFile;
     @Nullable private final String baseFile1;


     public Test(@NotNull String baseFile) {
         this.baseFile = baseFile;
         this.baseFile1 = null;
     }

     public Test(@NotNull String baseFile1, boolean a) {
         this.baseFile1 = baseFile1;
         if (baseFile1.contains("foo")) {
           this.baseFile = null;
         } else {
           this.baseFile = null;
         }
     }
}

class Test2 {
  @Nullable Object member;

  public Test2(@NotNull Object member) {
    this.member = member;
  }

  public void setMember(@Nullable Object member) {
    this.member = member;
  }
}

class Test3 {
  @Nullable final Object <warning descr="@Nullable field is always initialized not-null">member</warning>;

  public Test3(@NotNull Object member) {
    this.member = member;
  }

}

class Test4 {
  @Nullable Object member;

  public Test4(@NotNull Object member) {
    this.member = member;
  }

  public Test4(int a) {
    this.member = null;
  }
}


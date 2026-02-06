package com.test;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@interface Nullable {

}

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
@interface NotNull {

}


public class SomeBuilderTest {

  public static void test(@Nullable Container.Generator generator) {
      Container.Builder builder = newMethod(generator);


      if (builder != null) {
      System.out.println(builder.toString());
    }


  }

    @Nullable
    private static Container.Builder newMethod(@Nullable Container.Generator generator) {
        Container.Builder builder1 = new Container.Builder();
        builder1 = builder1.next();
        if (generator != null) {
          if (1 == generator.nextInt()) {
            builder1 = null;
          } else {
            builder1 = builder1.next();
          }
        }
        Container.Builder builder = builder1;
        return builder;
    }


    public class Container {

    public static class Generator {
      int nextInt() {
        return 0;
      }
    }

    public static class Builder {
      public Builder next() {
        return new Builder();
      }
    }
  }
}

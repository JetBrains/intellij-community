package org.example;

import org.jspecify.annotations.*;

@NullMarked
class CustomOptionalTest2 {
  @Nullable
  public String a;
  public String @Nullable [] a3;

  CustomOptional2<String> returnNotNullableDelegate() {
    return Delegate2.ofNullable(a);
  }

  CustomOptional2<String> returnNotNullableDelegate2() {
    return Delegate2.ofNullable2(a);
  }

  CustomOptional2<String[]> returnNotNullableDelegate3() {
    return Delegate2.ofNullable3(a3);
  }

  CustomOptional2<?> returnNotNullableDelegate4() {
    return Delegate2.ofNullable4(a3);
  }

  CustomOptional2<?> returnNotNullableDelegate5() {
    return Delegate2.ofNullable4(a3);
  }
}

class Delegate2 {
  static <T> CustomOptional2<T> ofNullable(@Nullable T t) {
    return CustomOptional2.ofNullable(t);
  }

  static CustomOptional2<String> ofNullable2(@Nullable String t) {
    return CustomOptional2.ofNullable(t);
  }

  static CustomOptional2<String[]> ofNullable3(@Nullable String @Nullable [] t) {
    return CustomOptional2.ofNullable(t);
  }

  static <T extends Object> CustomOptional2<? extends T> ofNullable4(T t) {
    return CustomOptional2.ofNullable(t);
  }

  static CustomOptional2<?> ofNullable5(Object t) {
    return CustomOptional2.ofNullable(t);
  }
}

class CustomOptional2<T extends @NotNull Object> {
  public CustomOptional2(T s) {

  }

  public static <T> CustomOptional2<@NotNull T> ofNullable(@Nullable T value) {
    return value == null ? (CustomOptional2<T>) new CustomOptional2<>("")
                         : new CustomOptional2<>(value);
  }
}
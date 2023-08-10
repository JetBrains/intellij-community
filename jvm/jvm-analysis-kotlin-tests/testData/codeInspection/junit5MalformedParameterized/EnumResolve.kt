package test;


import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

public class Clazz {

  private enum class Dupl {A, B, C}
  private enum class Empty {}

  @ParameterizedTest
  @EnumSource(value = Dupl::class, names = ["A", "<warning descr="Duplicate 'enum' constant name">A</warning>"])
  fun duplicateEnum() {
  }

  @ParameterizedTest
  @EnumSource(value = Empty::class, names = ["<warning descr="Can't resolve 'enum' constant reference.">S</warning>"])
  fun emptyEnum() {
  }
}

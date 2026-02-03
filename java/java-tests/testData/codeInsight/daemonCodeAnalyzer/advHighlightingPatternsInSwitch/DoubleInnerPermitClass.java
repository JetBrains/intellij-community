package org;

public class DoubleInnerPermitClass {
  //
  //        Root
  //          ^
  //          |
  //      +---+----+
  //      |        |
  //    Left1      |
  //      ^        |
  //      |      Right
  //    Left2      ^
  //      ^        |
  //      |        |
  //      +--+-----+
  //         |
  //       Impl
  //

  public sealed interface Root {}

  public sealed interface Left1 extends Root {}
  public sealed interface Left2 extends Left1 {}

  public sealed interface Right extends Root {}

  public record Impl() implements Left2, Right {}


  public static void tryArrayComponentType(Root vt) {
    switch (vt) {
      case Left1 _ -> {}
    }
  }
}
package com.siyeh.igtest.style.equals_called_on_enum_constant;

public class EqualsCalled {

    enum E {
        A,B,C
    }

    void one() {
        E.A.<warning descr="'equals()' called on enum value">equals</warning>(E.C);
        E.B.<warning descr="'equals()' called on enum value">equals</warning>(new Object());
        E.C.equals<error descr="Expected 1 argument but found 0">()</error>;
        final Object A = new Object();
        A.equals(1);
    }

    void objectsEquals(E a, E b) {
      if(java.util.Objects.<warning descr="'equals()' called on enum value">equals</warning>(a, b)) {
        System.out.println("equals");
      }
    }
}
class Main {
  enum Suit {
    SPADES, HEARTS, DIAMONDS, CLUBS
  }

  private boolean equalsType(Suit suit, String type) {
    return suit.equals(type);
  }
}

// "Create missing switch branch 'Sub2'" "true"
sealed abstract class I {
}

final class Sub1 extends I {
}

final class Sub2 extends I {
}

class Test {
  void testI(I i) {
    switch (i) {
      case Sub1 s1:
        System.out.println("ok");
        break;
        case Sub2 sub2:
            break;
    }
  }
}
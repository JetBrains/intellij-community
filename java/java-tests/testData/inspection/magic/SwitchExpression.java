import org.intellij.lang.annotations.MagicConstant;

import static java.util.Calendar.*;

class Main {
  void acceptMonth(@MagicConstant(intValues = {JANUARY, FEBRUARY, MARCH, APRIL, MAY, JUNE, JULY, AUGUST, SEPTEMBER, OCTOBER, NOVEMBER, DECEMBER}) int month) {
  }

  void test1(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(month);
  }

  void test2(int num) {
    int month = switch (num) {
      case 1 -> 42;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test3(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield 42;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test4(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield 42;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? 42 : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test5(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? 42 : APRIL) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test6(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : 42) : APRIL);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test7(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : 42);
      }
      default -> throw new IllegalStateException("Unexpected value: " + num);
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }

  void test8(int num) {
    int month = switch (num) {
      case 1 -> JANUARY;
      case 2 -> {
        yield FEBRUARY;
      }
      case 3 -> {
        if (Math.random() > 0.5) {
          yield MARCH;
        }
        yield MARCH;
      }
      case 4 -> {
        yield Math.random() > 0.5 ? APRIL : (Math.random() > 0.5 ? (Math.random() > 0.5 ? APRIL : APRIL) : 42);
      }
      default -> 42;
    };
    acceptMonth(<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, ...">month</warning>);
  }
}
// "Fix all 'Pattern variable can be used' problems in file" "true"
class X {
  void test(Object obj) {
    if (!(obj instanceof Number)) return;
    if (false) {
      Number n;
    }
    Number n = (Number)obj;
    System.out.println(n.longValue());
  }


  private static void simpleIfCondition(Object o) {
    if (o instanceof String) {
      String <caret>text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleIfConditionNotWarn(Object o) {
    if (o instanceof String && switch (o.hashCode()){
      default -> {
        String text = "1";
        yield true;
      }
    }) {
      String text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleWhileCondition(Object o) {
    while (o instanceof String) {
      String text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleWhileBeforeNotWarn(Object o) {
    while (o instanceof String) {
      if (false) {
        String text = "1";
      }

      String text  = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleWhileConditionNotWarn(Object o) {
    while (o instanceof String && switch (o.hashCode()){
      default -> {
        String text = "1";
        yield true;
      }
    }) {
      String text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleLoop(Object o) {
    for (int i = 0; o instanceof String; i++) {
      String text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleLoopUpdateNotWarn(Object o) {
    for (int i = 0; o instanceof String; i=i+switch (o.hashCode()){
      default -> {
        String text = (String)  o;
        yield 1;
      }
    }) {
      String text = (String)  o;
      System.out.println(text);
    }
  }

  private static void simpleWhileOutside(Object o) {
    while (!(o instanceof String) || o.hashCode() ==1) {
    }
    String text = (String)  o;
    System.out.println(text);
  }

  private static void simpleWhileOutsideBeforeNotWarn(Object o) {
    while (!(o instanceof String) || o.hashCode() ==1) {
    }
    if (false) {
      String text = (String)  o;
      System.out.println(text);
    }
    String text = (String)  o;
    System.out.println(text);
  }

  private static void simpleIfElseOutside(Object o) {
    if (o instanceof String) {
    } else {
      return;
    }
    String text = (String) o;
    System.out.println(text);
  }

  private static void simpleIfElseOutsideAndInsideNotWarn(Object o) {
    if (o instanceof String) {
      String text = (String) o;
      System.out.println(text);
    } else {
      return;
    }
    String text = "1";
    System.out.println(text);
  }

  private static void simpleIfElseOutsideAndInsideNotWarn2(Object o) {
    if (o instanceof String) {
      String text = "1";
      System.out.println(text);
    } else {
      return;
    }
    String text = (String) o;
    System.out.println(text);
  }

  private static void simpleIfElseOutside2(Object o) {
    if (o instanceof String && o.hashCode()==1) {
    } else {
      return;
    }
    String text = (String) o;
    System.out.println(text);
  }

  private static void simpleIfElseOutsideAndInsideNotWarn3(Object o) {
    if (!(o instanceof String && o.hashCode()==1)) {
      return;
    } else {
      String text = "1";
    }
    String text = (String) o;
    System.out.println(text);
  }

  private static void simpleIfElseOutsideAndInsideNotWarn4(Object o) {
    if (!(o instanceof String && o.hashCode()==1)) {
      return;
    } else {
      String text = (String) o;
      System.out.println(text);
    }
    String text = "1";
  }

  private static void simpleIfElseOutsideBeforeNotWarn(Object o) {
    if (!(o instanceof String && o.hashCode()==1)) {
      return;
    } else {
    }
    if (true) {
      String text = "1";
    }
    String text = (String) o;
    System.out.println(text);
  }

  private static void simpleIfElseInsideElseBeforeNotWarn(Object o) {
    if (!(o instanceof String && o.hashCode()==1)) {
      return;
    } else {
      if (true) {
        String text = "1";
      }
      String text = (String) o;
      System.out.println(text);
    }
  }

  private void testPair(Object obj) {
    if (obj instanceof Integer) {
      Integer typed = (Integer) obj;
    }

    if (obj instanceof Double) {
      Double typed = (Double) obj;
    }
  }
}